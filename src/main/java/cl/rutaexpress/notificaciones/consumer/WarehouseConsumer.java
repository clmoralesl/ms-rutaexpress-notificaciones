package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import cl.rutaexpress.notificaciones.service.TicketBodegaService;
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
public class WarehouseConsumer {

    private static final Logger log = LoggerFactory.getLogger(WarehouseConsumer.class);
    private static final int MAX_REINTENTOS = 3;

    private final TicketBodegaService ticketService;
    private final EstadisticasService estadisticasService;
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;

    public WarehouseConsumer(
            TicketBodegaService ticketService,
            EstadisticasService estadisticasService,
            MessageConverter messageConverter,
            ObjectMapper objectMapper) {
        this.ticketService = ticketService;
        this.estadisticasService = estadisticasService;
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_WAREHOUSE, ackMode = "MANUAL")
    public void recibirTicketBodega(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            MensajeEnvelope<?> rawEnvelope = (MensajeEnvelope<?>) messageConverter.fromMessage(message);

            TicketBodegaPayload payload;
            if (rawEnvelope.datos() instanceof TicketBodegaPayload p) {
                payload = p;
            } else {
                payload = objectMapper.convertValue(rawEnvelope.datos(), TicketBodegaPayload.class);
            }

            MensajeEnvelope<TicketBodegaPayload> envelope = new MensajeEnvelope<>(
                    rawEnvelope.tipo(),
                    rawEnvelope.eventoId(),
                    rawEnvelope.timestamp(),
                    rawEnvelope.traceId(),
                    rawEnvelope.correlationId(),
                    payload
            );

            log.info("Mensaje recibido en cola [{}], deliveryTag: {}", RabbitMQConfig.QUEUE_WAREHOUSE, deliveryTag);

            ticketService.generarTicketPicking(envelope);

            // Confirmacion explicita de mensaje procesado exitosamente
            channel.basicAck(deliveryTag, false);
            estadisticasService.registrarTicketBodegaProcesado();

            log.info("Ticket de picking confirmado exitosamente (ACK) en {}", RabbitMQConfig.QUEUE_WAREHOUSE);

        } catch (Exception ex) {
            int reintentos = obtenerReintentos(message);
            log.error("Error al procesar ticket de picking en {} (intento {}/{}): {}",
                    RabbitMQConfig.QUEUE_WAREHOUSE, reintentos + 1, MAX_REINTENTOS, ex.getMessage());

            if (reintentos >= MAX_REINTENTOS - 1) {
                // Reintentos agotados: NACK con requeue=false para derivar a Dead Letter Queue (DLQ)
                log.warn("Maximo de reintentos alcanzado. Derivando ticket a {} via DLX", RabbitMQConfig.QUEUE_WAREHOUSE_DLQ);
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarTicketBodegaFallido();
            } else {
                // Falla controlada: desviar a DLQ
                log.warn("Derivando ticket con error a DLQ para revision");
                channel.basicNack(deliveryTag, false, false);
                estadisticasService.registrarTicketBodegaFallido();
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
