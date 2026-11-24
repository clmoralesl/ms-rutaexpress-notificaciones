package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria;
import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria.LimitesCola;
import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria.Reintentos;
import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.NotificacionEmailPayload;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import cl.rutaexpress.notificaciones.service.NotificacionEmailService;
import cl.rutaexpress.notificaciones.service.TicketBodegaService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumersTest {
    @Mock private NotificacionEmailService emailService;
    @Mock private TicketBodegaService ticketBodegaService;
    @Mock private EtiquetaService etiquetaService;
    @Mock private EstadisticasService estadisticas;
    @Mock private MessageConverter converter;
    @Mock private RabbitTemplate rabbitTemplate;
    @Mock private Channel channel;
    private ProcesadorMensajes procesador;

    private static final Map<TipoMensaje, String> COLA_ESPERA = Map.of(
            TipoMensaje.EMAIL, "q.cmd.email.retry",
            TipoMensaje.BODEGA, "q.cmd.warehouse.retry",
            TipoMensaje.ETIQUETA, "q.cmd.label.retry");

    @BeforeEach
    void setUp() {
        PoliticaReintentos politica = new PoliticaReintentos(rabbitTemplate, new PropiedadesMensajeria(
                new LimitesCola(1, 1), new LimitesCola(1, 1), new Reintentos(3, 10000, 200)));
        procesador = new ProcesadorMensajes(converter, RabbitMQConfig.construirObjectMapper(), politica, estadisticas);
    }

    private void invocar(TipoMensaje tipo, Message m) {
        switch (tipo) {
            case EMAIL -> new EmailConsumer(emailService, procesador).recibirNotificacionEmail(m, channel);
            case BODEGA -> new WarehouseConsumer(ticketBodegaService, procesador).recibirTicketBodega(m, channel);
            case ETIQUETA -> new LabelConsumer(etiquetaService, procesador).recibirSolicitudEtiqueta(m, channel);
        }
    }

    private void servicioFalla(TipoMensaje tipo) {
        RuntimeException e = new IllegalStateException("servicio externo lento");
        switch (tipo) {
            case EMAIL -> doThrow(e).when(emailService).procesarNotificacionEmail(any());
            case BODEGA -> doThrow(e).when(ticketBodegaService).generarTicketPicking(any());
            case ETIQUETA -> doThrow(e).when(etiquetaService).generarEtiqueta(any());
        }
    }

    private void verificarServicioInvocado(TipoMensaje tipo) {
        switch (tipo) {
            case EMAIL -> verify(emailService).procesarNotificacionEmail(any());
            case BODEGA -> verify(ticketBodegaService).generarTicketPicking(any());
            case ETIQUETA -> verify(etiquetaService).generarEtiqueta(any());
        }
    }

    private static MensajeEnvelope<?> envelope(TipoMensaje tipo) {
        Object datos = switch (tipo) {
            case EMAIL -> new NotificacionEmailPayload(1L, "RE-001", "1-1", "2-2", "ACEPTADO", "Asunto", "Mensaje", LocalDateTime.now());
            case BODEGA -> new TicketBodegaPayload(2L, "RE-002", "1-1", "2-2", "Origen", "Destino", new BigDecimal("5.00"), LocalDateTime.now(), "Instruccion");
            case ETIQUETA -> new EtiquetaPayload(3L, "RE-003", "1-1", "2-2", "Origen", "Destino", new BigDecimal("1.20"), "TERMICO_10X15", LocalDateTime.now());
        };
        return new MensajeEnvelope<>("TIPO", "ev-1", LocalDateTime.now(), "tr-1", "RE-001", datos);
    }

    private static Message mensaje(Object reintentos, boolean reentregado) {
        MessageProperties p = new MessageProperties();
        p.setDeliveryTag(7L);
        p.setRedelivered(reentregado);
        if (reintentos != null) p.setHeader("x-reintentos", reintentos);
        return new Message("{}".getBytes(StandardCharsets.UTF_8), p);
    }

    private void confirmarCopia(boolean ack) {
        doAnswer(inv -> {
            CorrelationData cd = inv.getArgument(3);
            cd.getFuture().complete(new CorrelationData.Confirm(ack, null));
            return null;
        }).when(rabbitTemplate).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private void verificarDlq(TipoMensaje tipo) throws IOException {
        verify(channel).basicNack(7L, false, false);
        verify(estadisticas).registrarFallido(tipo);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void exitoHaceAck(TipoMensaje tipo) throws Exception {
        Message m = mensaje(1, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        invocar(tipo, m);
        verificarServicioInvocado(tipo);
        verify(channel).basicAck(7L, false);
        verify(estadisticas).registrarProcesado(tipo);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void payloadInvalidoVaADlqSinReintento(TipoMensaje tipo) throws Exception {
        Message m = mensaje(null, false);
        when(converter.fromMessage(m)).thenThrow(new MessageConversionException("sin __TypeId__"));
        invocar(tipo, m);
        verificarDlq(tipo);
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void cuerpoQueNoEsEnvelopeVaADlq(TipoMensaje tipo) throws Exception {
        Message m = mensaje(null, false);
        when(converter.fromMessage(m)).thenReturn("{}".getBytes(StandardCharsets.UTF_8));
        invocar(tipo, m);
        verificarDlq(tipo);
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void errorTransitorioProgramaReintentoYHaceAck(TipoMensaje tipo) throws Exception {
        Message m = mensaje(null, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        confirmarCopia(true);
        invocar(tipo, m);
        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(COLA_ESPERA.get(tipo)), copia.capture(), any(CorrelationData.class));
        assertEquals(1, copia.getValue().getMessageProperties().getHeaders().get("x-reintentos"));
        verify(channel).basicAck(7L, false);
        verify(estadisticas).registrarReintentoProgramado(tipo);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void penultimoIntentoAunReintenta(TipoMensaje tipo) throws Exception {
        Message m = mensaje(2, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        confirmarCopia(true);
        invocar(tipo, m);
        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(COLA_ESPERA.get(tipo)), copia.capture(), any(CorrelationData.class));
        assertEquals(3, copia.getValue().getMessageProperties().getHeaders().get("x-reintentos"));
        verify(channel).basicAck(7L, false);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void maximoAlcanzadoVaADlq(TipoMensaje tipo) throws Exception {
        Message m = mensaje(3, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        invocar(tipo, m);
        verificarDlq(tipo);
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void cabeceraInvalidaVaADlq(TipoMensaje tipo) throws Exception {
        Message m = mensaje("-1", false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        invocar(tipo, m);
        verificarDlq(tipo);
        verifyNoInteractions(rabbitTemplate);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void copiaNoConfirmadaReencolaSinAck(TipoMensaje tipo) throws Exception {
        Message m = mensaje(0, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        confirmarCopia(false);
        invocar(tipo, m);
        verify(channel).basicNack(7L, false, true);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(estadisticas, never()).registrarFallido(any());
        verify(estadisticas, never()).registrarReintentoProgramado(any());
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void copiaNoConfirmadaEnReentregaVaADlq(TipoMensaje tipo) throws Exception {
        Message m = mensaje(0, true);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        servicioFalla(tipo);
        confirmarCopia(false);
        invocar(tipo, m);
        verificarDlq(tipo);
    }

    @ParameterizedTest @EnumSource(TipoMensaje.class)
    void falloDelAckNoPropaga(TipoMensaje tipo) throws Exception {
        Message m = mensaje(null, false);
        when(converter.fromMessage(m)).thenReturn(envelope(tipo));
        doThrow(new IOException("canal cerrado")).when(channel).basicAck(7L, false);
        assertDoesNotThrow(() -> invocar(tipo, m));
        verify(estadisticas, never()).registrarProcesado(any());
    }
}
