package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.NotificacionEmailPayload;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import cl.rutaexpress.notificaciones.service.NotificacionEmailService;
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
public class EmailConsumer {

    private static final Logger log = LoggerFactory.getLogger(EmailConsumer.class);
    private static final int MAX_REINTENTOS = 3;

    private final NotificacionEmailService emailService;
    private final EstadisticasService estadisticasService;
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;

    public EmailConsumer(
            NotificacionEmailService emailService,
            EstadisticasService estadisticasService,
            MessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.emailService = emailService;
        this.estadisticasService = estadisticasService;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EMAIL, ackMode = "MANUAL")
    public void recibirNotificacionEmail(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            MensajeEnvelope<?> rawEnvelope = (MensajeEnvelope<?>) messageConverter.fromMessage(message);

            NotificacionEmailPayload payload;
            if (rawEnvelope.datos() instanceof NotificacionEmailPayload p) {
                payload = p;
            } else {
                payload = objectMapper.convertValue(rawEnvelope.datos(), NotificacionEmailPayload.class);
            }

            MensajeEnvelope<NotificacionEmailPayload> envelope = new MensajeEnvelope<>(
                    rawEnvelope.tipo(),
                    rawEnvelope.eventoId(),
                    rawEnvelope.timestamp(),
                    rawEnvelope.traceId(),
                    rawEnvelope.correlationId(),
                    payload
            );

            log.info("Mensaje recibido en cola [{}], deliveryTag: {}", RabbitMQConfig.QUEUE_EMAIL, deliveryTag);

            emailService.procesarNotificacionEmail(envelope);

            // Confirmacion explicita de mensaje procesado exitosamente
            channel.basicAck(deliveryTag, false);
            estadisticasService.registrarEmailProcesado();

            log.info("Mensaje confirmado exitosamente (ACK) en {}", RabbitMQConfig.QUEUE_EMAIL);

        } catch (Exception ex) {
            int reintentos = obtenerReintentos(message);
            log.error("Error al procesar mensaje en {} (intento {}/{}): {}",
                    RabbitMQConfig.QUEUE_EMAIL, reintentos + 1, MAX_REINTENTOS, ex.getMessage());

            if (reintentos >= MAX_REINTENTOS - 1) {
                // Reintentos agotados: NACK con requeue=false para derivar a Dead Letter Queue (DLQ)
                log.warn("Maximo de reintentos alcanzado. Derivando mensaje a {} via DLX", RabbitMQConfig.QUEUE_EMAIL_DLQ);
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarEmailFallido();
            } else {
                // Falla controlada: NACK con requeue=false hacia DLQ
                log.warn("Derivando mensaje con error a DLQ para analisis forense");
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarEmailFallido();
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
