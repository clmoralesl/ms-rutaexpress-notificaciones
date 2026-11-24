package cl.rutaexpress.notificaciones;

import cl.rutaexpress.notificaciones.consumer.PoliticaReintentos;
import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pruebas de integracion con el broker real para validar la politica de reintentos
 * y derivacion a DLQ (Issue #106).
 * Se ejecuta con: mvn verify -Pintegracion-externa
 */
@DirtiesContext
@Tag("integracion-externa")
@SpringBootTest
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=true")
class ReintentosRabbitMQIntegracionTest {

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private PoliticaReintentos politica;

    @Autowired
    private EstadisticasService estadisticasService;

    @MockBean
    private EtiquetaService etiquetaService;

    @BeforeEach
    void setUp() {
        reset(etiquetaService);
        rabbitAdmin.purgeQueue("q.cmd.label");
        rabbitAdmin.purgeQueue("q.cmd.label.retry");
        rabbitAdmin.purgeQueue("q.cmd.label.dlq");
    }

    private MensajeEnvelope<EtiquetaPayload> crearEnvelope() {
        EtiquetaPayload payload = new EtiquetaPayload(
                10L, "RE-RETRY-01", "1-1", "2-2", "Origen", "Destino",
                new BigDecimal("1.50"), "TERMICO_10X15", LocalDateTime.now()
        );
        return new MensajeEnvelope<>(
                "ETIQUETA_GENERAR", "ev-retry-01", LocalDateTime.now(), "tr-1", "RE-RETRY-01", payload
        );
    }

    @Test
    @DisplayName("I1: Error transitorio persistente se reintenta 3 veces con TTL y termina en DLQ con x-reintentos=3")
    void transitorioPersistenteTerminaEnDlq() {
        List<Long> tiempos = new CopyOnWriteArrayList<>();
        doAnswer(inv -> {
            tiempos.add(System.currentTimeMillis());
            throw new IllegalStateException("falla transitoria en generacion de etiqueta");
        }).when(etiquetaService).generarEtiqueta(any());

        rabbitTemplate.convertAndSend("cmd.direct", "label.gen", crearEnvelope());

        Message msgDlq = rabbitTemplate.receive("q.cmd.label.dlq", 60_000);
        assertNotNull(msgDlq, "El mensaje debio llegar a q.cmd.label.dlq tras agotar reintentos");
        assertEquals(3, msgDlq.getMessageProperties().getHeaders().get("x-reintentos"));
        assertEquals(4, tiempos.size(), "Debe haber exactamente 4 intentos (1 inicial + 3 reintentos)");

        for (int i = 1; i < tiempos.size(); i++) {
            long diferencia = tiempos.get(i) - tiempos.get(i - 1);
            assertTrue(diferencia >= 9000,
                    "La diferencia entre reintentos debe ser de al menos 9000 ms, fue: " + diferencia);
        }
    }

    @Test
    @DisplayName("I2: Error transitorio resuelto en el segundo intento realiza ACK y no llega a DLQ")
    void transitorioResueltoEnSegundoIntentoHaceAck() {
        long inicialProcesadas = estadisticasService.obtenerEstadisticas().etiquetasProcesadas();
        AtomicInteger llamadas = new AtomicInteger(0);

        doAnswer(inv -> {
            if (llamadas.incrementAndGet() == 1) {
                throw new IllegalStateException("falla transitoria inicial");
            }
            return new byte[0];
        }).when(etiquetaService).generarEtiqueta(any());

        rabbitTemplate.convertAndSend("cmd.direct", "label.gen", crearEnvelope());

        verify(etiquetaService, timeout(30_000).times(2)).generarEtiqueta(any());
        Message msgDlq = rabbitTemplate.receive("q.cmd.label.dlq", 2_000);
        assertNull(msgDlq, "El mensaje no debe llegar a la DLQ si se resolvio exitosamente");
        assertEquals(inicialProcesadas + 1, estadisticasService.obtenerEstadisticas().etiquetasProcesadas());
    }

    @Test
    @DisplayName("I3: Copia hacia cola inexistente no se confirma (mandatory/return)")
    void copiaHaciaColaInexistenteNoSeConfirma() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message mensaje = new Message("{}".getBytes(StandardCharsets.UTF_8), props);

        assertFalse(politica.programarReintento(mensaje, "q.issue106.inexistente", 0));
    }

    @Test
    @DisplayName("I4: Mensaje sin __TypeId__ se clasifica permanente y va directo a DLQ en primer intento")
    void sinTypeIdVaADlqEnPrimerIntento() {
        MessageProperties props = new MessageProperties();
        props.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        Message rawMsg = new Message("{\"eventoId\":\"ev-notype\"}".getBytes(StandardCharsets.UTF_8), props);

        rabbitTemplate.send("cmd.direct", "label.gen", rawMsg);

        Message msgDlq = rabbitTemplate.receive("q.cmd.label.dlq", 5_000);
        assertNotNull(msgDlq, "Mensaje sin __TypeId__ debe derivar a DLQ");
        assertNull(msgDlq.getMessageProperties().getHeaders().get("x-reintentos"));
        verify(etiquetaService, never()).generarEtiqueta(any());
    }
}
