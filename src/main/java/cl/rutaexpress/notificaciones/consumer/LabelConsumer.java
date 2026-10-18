package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class LabelConsumer {

    private static final Logger log = LoggerFactory.getLogger(LabelConsumer.class);
    private static final int MAX_REINTENTOS = 3;

    private final EtiquetaService etiquetaService;
    private final EstadisticasService estadisticasService;
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;

    public LabelConsumer(
            EtiquetaService etiquetaService,
            EstadisticasService estadisticasService,
            MessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.etiquetaService = etiquetaService;
        this.estadisticasService = estadisticasService;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_LABEL, ackMode = "MANUAL")
    public void recibirSolicitudEtiqueta(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            MensajeEnvelope<?> rawEnvelope = (MensajeEnvelope<?>) messageConverter.fromMessage(message);

            EtiquetaPayload payload;
            if (rawEnvelope.datos() instanceof EtiquetaPayload p) {
                payload = p;
            } else {
                payload = objectMapper.convertValue(rawEnvelope.datos(), EtiquetaPayload.class);
            }

            MensajeEnvelope<EtiquetaPayload> envelope = new MensajeEnvelope<>(
                    rawEnvelope.tipo(),
                    rawEnvelope.eventoId(),
                    rawEnvelope.timestamp(),
                    rawEnvelope.traceId(),
                    rawEnvelope.correlationId(),
                    payload
            );

            log.info("Mensaje recibido en cola [{}], deliveryTag: {}", RabbitMQConfig.QUEUE_LABEL, deliveryTag);

            etiquetaService.generarEtiqueta(envelope);

            // Confirmacion explicita de mensaje procesado exitosamente
            channel.basicAck(deliveryTag, false);
            estadisticasService.registrarEtiquetaProcesada();

            log.info("Etiqueta confirmada exitosamente (ACK) en {}", RabbitMQConfig.QUEUE_LABEL);

        } catch (Exception ex) {
            int reintentos = obtenerReintentos(message);
            log.error("Error al generar etiqueta en {} (intento {}/{}): {}",
                    RabbitMQConfig.QUEUE_LABEL, reintentos + 1, MAX_REINTENTOS, ex.getMessage());

            if (reintentos >= MAX_REINTENTOS - 1) {
                // Reintentos agotados: NACK con requeue=false para derivar a Dead Letter Queue (DLQ)
                log.warn("Maximo de reintentos alcanzado. Derivando etiqueta a {} via DLX", RabbitMQConfig.QUEUE_LABEL_DLQ);
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarEtiquetaFallida();
            } else {
                // Falla controlada: desviar a DLQ
                log.warn("Derivando mensaje de rotulado con error a DLQ");
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarEtiquetaFallida();
            }
        }
    }

    private int obtenerReintentos(Message message) {
        Object retries = message.getMessageProperties().getHeaders().get("x-retries");
        if (retries instanceof Number number) {
            return number.intValue();
        }
        var xDeath = message.getMessageProperties().getXDeathHeader();
        if (xDeath != null && !xDeath.isEmpty()) {
            Object count = xDeath.get(0).get("count");
            if (count instanceof Number number) {
                return number.intValue();
            }
        }
        return 0;
    }
}
