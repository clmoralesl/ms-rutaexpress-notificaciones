package cl.rutaexpress.notificaciones.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MensajeEnvelope<T>(
        String tipo,
        String eventoId,
        LocalDateTime timestamp,
        String traceId,
        String correlationId,
        T datos
) {
    public static <T> MensajeEnvelope<T> de(String tipo, String correlationId, T datos) {
        return new MensajeEnvelope<>(
                tipo,
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                UUID.randomUUID().toString(),
                correlationId,
                datos
        );
    }
}
