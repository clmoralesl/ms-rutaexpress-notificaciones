package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria;
import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria.LimitesCola;
import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria.Reintentos;
import cl.rutaexpress.notificaciones.consumer.PoliticaReintentos.Clasificacion;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PoliticaReintentosTest {
    private static final String COLA = "q.cmd.label.retry";
    @Mock private RabbitTemplate rabbitTemplate;
    private PoliticaReintentos politica;

    @BeforeEach
    void setUp() {
        politica = new PoliticaReintentos(rabbitTemplate, new PropiedadesMensajeria(
                new LimitesCola(1, 1), new LimitesCola(1, 1), new Reintentos(3, 10000, 200)));
    }

    private static Message mensaje(Object cabecera) {
        MessageProperties p = new MessageProperties();
        if (cabecera != null) p.setHeader("x-reintentos", cabecera);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), p);
    }

    private void responder(boolean ack, boolean devolver) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            if (devolver) cd.setReturned(new ReturnedMessage(inv.getArgument(2), 312, "NO_ROUTE", "", COLA));
            cd.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbitTemplate).send(eq(""), eq(COLA), any(Message.class), any(CorrelationData.class));
    }

    static Stream<Exception> permanentes() {
        return Stream.of(new MessageConversionException("x"), new IllegalArgumentException("x"),
                new NumberFormatException("x"), new ClassCastException("x"), new JsonParseException((JsonParser) null, "x"),
                new AmqpRejectAndDontRequeueException("x"),
                new RuntimeException("envoltura", new IllegalArgumentException("causa")));
    }

    static Stream<Exception> transitorios() {
        return Stream.of(new RuntimeException("x"), new IllegalStateException("x"),
                new AmqpConnectException(new java.net.ConnectException("x")), new NullPointerException());
    }

    @ParameterizedTest @MethodSource("permanentes")
    void clasificaPermanentes(Exception e) { assertEquals(Clasificacion.PERMANENTE, politica.clasificar(e)); }

    @ParameterizedTest @MethodSource("transitorios")
    void clasificaTransitorios(Exception e) { assertEquals(Clasificacion.TRANSITORIO, politica.clasificar(e)); }

    @Test
    void cadenaCiclicaNoBloquea() {
        RuntimeException a = new RuntimeException("a");
        RuntimeException b = new RuntimeException("b", a);
        a.initCause(b);
        assertTimeoutPreemptively(Duration.ofSeconds(1),
                () -> assertEquals(Clasificacion.TRANSITORIO, politica.clasificar(b)));
    }

    @Test
    void intentoActual() {
        assertEquals(0, politica.intentoActual(mensaje(null)));
        assertEquals(2, politica.intentoActual(mensaje(2)));
        assertEquals(2, politica.intentoActual(mensaje(2L)));
        assertEquals(2, politica.intentoActual(mensaje("2")));
        for (Object invalido : new Object[] {-1, -1L, "-1", "abc", "", "2.5", 2.5d, Long.MAX_VALUE, "9999999999"}) {
            assertEquals(PoliticaReintentos.INTENTO_INVALIDO, politica.intentoActual(mensaje(invalido)), String.valueOf(invalido));
        }
    }

    @Test
    void puedeReintentarHastaElMaximo() {
        assertTrue(politica.puedeReintentar(0));
        assertTrue(politica.puedeReintentar(2));
        assertFalse(politica.puedeReintentar(3));
        assertFalse(politica.puedeReintentar(PoliticaReintentos.INTENTO_INVALIDO));
    }

    @Test
    void copiaConservaPropiedadesEIncrementaContador() {
        responder(true, false);
        Message original = mensaje(1);
        MessageProperties p = original.getMessageProperties();
        p.setMessageId("m-1");
        p.setCorrelationId("RE-001");
        p.setContentType("application/json");
        p.setHeader("__TypeId__", "cl.rutaexpress.envios.dto.MensajeEnvelope");
        p.setHeader("x-death", List.of(Map.of("count", 5L, "queue", COLA)));
        p.setHeader("x-first-death-queue", COLA);

        assertTrue(politica.programarReintento(original, COLA, 1));

        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(COLA), captor.capture(), any(CorrelationData.class));
        MessageProperties copia = captor.getValue().getMessageProperties();
        assertEquals(2, copia.getHeaders().get("x-reintentos"));
        assertEquals("m-1", copia.getMessageId());
        assertEquals("RE-001", copia.getCorrelationId());
        assertEquals("application/json", copia.getContentType());
        assertEquals("cl.rutaexpress.envios.dto.MensajeEnvelope", copia.getHeaders().get("__TypeId__"));
        assertFalse(copia.getHeaders().containsKey("x-death"));
        assertFalse(copia.getHeaders().containsKey("x-first-death-queue"));
        assertEquals(MessageDeliveryMode.PERSISTENT, copia.getDeliveryMode());
        assertArrayEquals(original.getBody(), captor.getValue().getBody());
        assertEquals(1, p.getHeaders().get("x-reintentos"));
        assertTrue(p.getHeaders().containsKey("x-death"));
    }

    @Test void nackDelBrokerDevuelveFalse() { responder(false, false); assertFalse(politica.programarReintento(mensaje(0), COLA, 0)); }

    @Test void mensajeNoEnrutableDevuelveFalse() { responder(true, true); assertFalse(politica.programarReintento(mensaje(0), COLA, 0)); }

    @Test void sinConfirmacionEnElPlazoDevuelveFalse() { assertFalse(politica.programarReintento(mensaje(0), COLA, 0)); }

    @Test
    void excepcionAlPublicarDevuelveFalse() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("caido")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertFalse(politica.programarReintento(mensaje(0), COLA, 0));
    }
}
