package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.PropiedadesMensajeria;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.MessagePropertiesBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Component
public class PoliticaReintentos {

    public enum Clasificacion { PERMANENTE, TRANSITORIO }

    public static final String CABECERA_REINTENTOS = "x-reintentos";
    public static final int INTENTO_INVALIDO = Integer.MAX_VALUE;
    private static final Logger log = LoggerFactory.getLogger(PoliticaReintentos.class);
    private static final Pattern ENTERO = Pattern.compile("\\d{1,9}");
    private static final int PROFUNDIDAD_MAXIMA = 10;
    private static final Set<String> CABECERAS_DEL_BROKER = Set.of(
            "x-death", "x-first-death-exchange", "x-first-death-queue", "x-first-death-reason",
            "x-last-death-exchange", "x-last-death-queue", "x-last-death-reason");
    private static final List<Class<? extends Throwable>> PERMANENTES = List.of(
            MessageConversionException.class, IllegalArgumentException.class, ClassCastException.class,
            JsonProcessingException.class, AmqpRejectAndDontRequeueException.class);

    private final RabbitTemplate rabbitTemplate;
    private final PropiedadesMensajeria.Reintentos config;

    public PoliticaReintentos(RabbitTemplate rabbitTemplate, PropiedadesMensajeria propiedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.config = propiedades.reintentos();
    }

    public Clasificacion clasificar(Throwable error) {
        Throwable actual = error;
        for (int i = 0; actual != null && i < PROFUNDIDAD_MAXIMA; i++) {
            for (Class<? extends Throwable> tipo : PERMANENTES) {
                if (tipo.isInstance(actual)) {
                    return Clasificacion.PERMANENTE;
                }
            }
            actual = actual.getCause();
        }
        return Clasificacion.TRANSITORIO;
    }

    public int intentoActual(Message message) {
        Object valor = message.getMessageProperties().getHeaders().get(CABECERA_REINTENTOS);
        if (valor == null) {
            return 0;
        }
        if (valor instanceof Integer || valor instanceof Long || valor instanceof Short || valor instanceof Byte) {
            long n = ((Number) valor).longValue();
            return (n >= 0 && n < INTENTO_INVALIDO) ? (int) n : INTENTO_INVALIDO;
        }
        String texto = valor.toString();
        return ENTERO.matcher(texto).matches() ? Integer.parseInt(texto) : INTENTO_INVALIDO;
    }

    public boolean puedeReintentar(int intento) {
        return intento < config.maximo();
    }

    /** Publica la copia en la cola de espera. Devuelve true solo si el broker la confirmo y la enruto. */
    public boolean programarReintento(Message original, String colaEspera, int intento) {
        MessageProperties propiedades = MessagePropertiesBuilder
                .fromClonedProperties(original.getMessageProperties()).build();
        CABECERAS_DEL_BROKER.forEach(propiedades.getHeaders()::remove);
        propiedades.setHeader(CABECERA_REINTENTOS, intento + 1);
        propiedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        Message copia = new Message(original.getBody(), propiedades);
        CorrelationData correlacion = new CorrelationData(UUID.randomUUID().toString());
        try {
            rabbitTemplate.send("", colaEspera, copia, correlacion);
            CorrelationData.Confirm confirmacion = correlacion.getFuture()
                    .get(config.tiempoConfirmacionMs(), TimeUnit.MILLISECONDS);
            if (correlacion.getReturned() != null) {
                log.error("Copia de reintento no enrutable: cola={}", colaEspera);
                return false;
            }
            if (!confirmacion.isAck()) {
                log.error("Copia de reintento rechazada por el broker: cola={}", colaEspera);
                return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException | TimeoutException | AmqpException e) {
            log.error("Copia de reintento no confirmada: cola={}, error={}", colaEspera, e.getClass().getSimpleName());
            return false;
        }
    }
}
