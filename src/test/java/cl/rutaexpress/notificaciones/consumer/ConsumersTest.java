package cl.rutaexpress.notificaciones.consumer;

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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConverter;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsumersTest {

    @Mock
    private NotificacionEmailService emailService;

    @Mock
    private TicketBodegaService ticketBodegaService;

    @Mock
    private EtiquetaService etiquetaService;

    @Mock
    private EstadisticasService estadisticasService;

    @Mock
    private MessageConverter messageConverter;

    @Mock
    private Channel channel;

    private EmailConsumer emailConsumer;
    private WarehouseConsumer warehouseConsumer;
    private LabelConsumer labelConsumer;

    @BeforeEach
    void setUp() {
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        emailConsumer = new EmailConsumer(emailService, estadisticasService, messageConverter, mapper);
        warehouseConsumer = new WarehouseConsumer(ticketBodegaService, estadisticasService, messageConverter, mapper);
        labelConsumer = new LabelConsumer(etiquetaService, estadisticasService, messageConverter, mapper);
    }

    private Message crearMensajeMock(long deliveryTag) {
        MessageProperties props = new MessageProperties();
        props.setDeliveryTag(deliveryTag);
        return new Message(new byte[0], props);
    }

    @Test
    @DisplayName("EmailConsumer: Procesa correo exitosamente y emite basicAck")
    void testEmailConsumerExito() throws IOException {
        Message message = crearMensajeMock(101L);
        NotificacionEmailPayload payload = new NotificacionEmailPayload(
                1L, "RE-001", "1-1", "2-2", "ACEPTADO", "Asunto", "Mensaje", LocalDateTime.now()
        );
        MensajeEnvelope<NotificacionEmailPayload> envelope =
                new MensajeEnvelope<>("EMAIL_ENVIO_ACEPTADO", "ev-1", LocalDateTime.now(), "tr-1", "RE-001", payload);

        when(messageConverter.fromMessage(message)).thenReturn(envelope);

        emailConsumer.recibirNotificacionEmail(message, channel);

        verify(emailService, times(1)).procesarNotificacionEmail(envelope);
        verify(channel, times(1)).basicAck(101L, false);
        verify(estadisticasService, times(1)).registrarEmailProcesado();
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    @DisplayName("EmailConsumer: Ante error fatal emite basicNack con requeue=false para desviar a DLQ")
    void testEmailConsumerErrorFatalDLQ() throws IOException {
        Message message = crearMensajeMock(102L);

        when(messageConverter.fromMessage(message)).thenThrow(new IllegalArgumentException("Formato JSON invalido"));

        emailConsumer.recibirNotificacionEmail(message, channel);

        verify(channel, times(1)).basicNack(102L, false, false);
        verify(estadisticasService, times(1)).registrarEmailFallido();
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    @DisplayName("WarehouseConsumer: Genera ticket de picking exitosamente y emite basicAck")
    void testWarehouseConsumerExito() throws IOException {
        Message message = crearMensajeMock(201L);
        TicketBodegaPayload payload = new TicketBodegaPayload(
                2L, "RE-002", "1-1", "2-2", "Origen", "Destino", new BigDecimal("5.00"), LocalDateTime.now(), "Instruccion"
        );
        MensajeEnvelope<TicketBodegaPayload> envelope =
                new MensajeEnvelope<>("TICKET_BODEGA_GENERAR", "ev-2", LocalDateTime.now(), "tr-2", "RE-002", payload);

        when(messageConverter.fromMessage(message)).thenReturn(envelope);

        warehouseConsumer.recibirTicketBodega(message, channel);

        verify(ticketBodegaService, times(1)).generarTicketPicking(envelope);
        verify(channel, times(1)).basicAck(201L, false);
        verify(estadisticasService, times(1)).registrarTicketBodegaProcesado();
    }

    @Test
    @DisplayName("WarehouseConsumer: Ante excepcion emite basicNack con requeue=false para DLQ")
    void testWarehouseConsumerErrorDLQ() throws IOException {
        Message message = crearMensajeMock(202L);

        when(messageConverter.fromMessage(message)).thenThrow(new RuntimeException("Error en base de datos bodega"));

        warehouseConsumer.recibirTicketBodega(message, channel);

        verify(channel, times(1)).basicNack(202L, false, false);
        verify(estadisticasService, times(1)).registrarTicketBodegaFallido();
    }

    @Test
    @DisplayName("LabelConsumer: Procesa rotulado exitosamente y emite basicAck")
    void testLabelConsumerExito() throws IOException {
        Message message = crearMensajeMock(301L);
        EtiquetaPayload payload = new EtiquetaPayload(
                3L, "RE-003", "1-1", "2-2", "Origen", "Destino", new BigDecimal("1.20"), "TERMICO_10X15", LocalDateTime.now()
        );
        MensajeEnvelope<EtiquetaPayload> envelope =
                new MensajeEnvelope<>("ETIQUETA_GENERAR", "ev-3", LocalDateTime.now(), "tr-3", "RE-003", payload);

        when(messageConverter.fromMessage(message)).thenReturn(envelope);

        labelConsumer.recibirSolicitudEtiqueta(message, channel);

        verify(etiquetaService, times(1)).generarEtiqueta(envelope);
        verify(channel, times(1)).basicAck(301L, false);
        verify(estadisticasService, times(1)).registrarEtiquetaProcesada();
    }

    @Test
    @DisplayName("LabelConsumer: Ante fallo emite basicNack con requeue=false hacia DLQ")
    void testLabelConsumerErrorDLQ() throws IOException {
        Message message = crearMensajeMock(302L);

        when(messageConverter.fromMessage(message)).thenThrow(new RuntimeException("Falla de impresora termica"));

        labelConsumer.recibirSolicitudEtiqueta(message, channel);

        verify(channel, times(1)).basicNack(302L, false, false);
        verify(estadisticasService, times(1)).registrarEtiquetaFallida();
    }
}
