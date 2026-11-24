package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class ProcesadorMensajes {

    @FunctionalInterface
    public interface ManejadorMensaje<T> {
        void manejar(MensajeEnvelope<T> envelope) throws Exception;
    }

    private static final Logger log = LoggerFactory.getLogger(ProcesadorMensajes.class);
    private final MessageConverter messageConverter;
    private final ObjectMapper objectMapper;
    private final PoliticaReintentos politica;
    private final EstadisticasService estadisticas;

    public ProcesadorMensajes(MessageConverter messageConverter, ObjectMapper objectMapper,
                              PoliticaReintentos politica, EstadisticasService estadisticas) {
        this.messageConverter = messageConverter;
        this.objectMapper = objectMapper;
        this.politica = politica;
        this.estadisticas = estadisticas;
    }

    public <T> void procesar(Message message, Channel channel, TipoMensaje tipo,
                             Class<T> clasePayload, ManejadorMensaje<T> manejador) {
        long tag = message.getMessageProperties().getDeliveryTag();
        MensajeEnvelope<T> envelope = null;
        try {
            envelope = convertir(message, clasePayload);
            manejador.manejar(envelope);
        } catch (Exception error) {
            manejarError(message, channel, tipo, envelope, error);
            return;
        }
        try {
            channel.basicAck(tag, false);
            estadisticas.registrarProcesado(tipo);
        } catch (IOException | RuntimeException e) {
            log.error("Fallo el ACK: tipo={}, eventoId={}, error={}", tipo, envelope.eventoId(), e.getClass().getSimpleName());
        }
    }

    <T> MensajeEnvelope<T> convertir(Message message, Class<T> clasePayload) {
        Object convertido = messageConverter.fromMessage(message);
        if (!(convertido instanceof MensajeEnvelope<?> crudo)) {
            throw new MessageConversionException("El cuerpo no es un MensajeEnvelope");
        }
        if (crudo.datos() == null) {
            throw new IllegalArgumentException("MensajeEnvelope sin datos");
        }
        T datos = clasePayload.isInstance(crudo.datos())
                ? clasePayload.cast(crudo.datos())
                : objectMapper.convertValue(crudo.datos(), clasePayload);
        return new MensajeEnvelope<>(crudo.tipo(), crudo.eventoId(), crudo.timestamp(),
                crudo.traceId(), crudo.correlationId(), datos);
    }

    private void manejarError(Message message, Channel channel, TipoMensaje tipo,
                              MensajeEnvelope<?> envelope, Exception error) {
        MessageProperties props = message.getMessageProperties();
        long tag = props.getDeliveryTag();
        int intento = politica.intentoActual(message);
        String eventoId = envelope != null ? envelope.eventoId() : "desconocido";
        String correlationId = envelope != null ? envelope.correlationId() : props.getCorrelationId();
        String clase = error.getClass().getSimpleName();
        try {
            if (politica.clasificar(error) == PoliticaReintentos.Clasificacion.PERMANENTE
                    || !politica.puedeReintentar(intento)) {
                channel.basicNack(tag, false, false);
                estadisticas.registrarFallido(tipo);
                log.warn("Mensaje derivado a DLQ: tipo={}, eventoId={}, correlationId={}, intento={}, error={}",
                        tipo, eventoId, correlationId, intento, clase);
                return;
            }
            if (politica.programarReintento(message, tipo.colaEspera(), intento)) {
                channel.basicAck(tag, false);
                estadisticas.registrarReintentoProgramado(tipo);
                log.warn("Reintento programado: tipo={}, eventoId={}, correlationId={}, intento={}, error={}",
                        tipo, eventoId, correlationId, intento + 1, clase);
                return;
            }
            if (Boolean.TRUE.equals(props.getRedelivered())) {
                channel.basicNack(tag, false, false);
                estadisticas.registrarFallido(tipo);
                log.error("Copia no confirmada en una reentrega; se deriva a DLQ: tipo={}, eventoId={}", tipo, eventoId);
            } else {
                channel.basicNack(tag, false, true);
                log.error("Copia no confirmada; se reencola el original: tipo={}, eventoId={}", tipo, eventoId);
            }
        } catch (IOException | RuntimeException e) {
            log.error("Fallo el ACK o NACK: tipo={}, eventoId={}, error={}", tipo, eventoId, e.getClass().getSimpleName());
        }
    }
}
