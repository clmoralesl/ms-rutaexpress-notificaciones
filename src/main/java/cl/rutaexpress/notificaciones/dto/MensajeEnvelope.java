package cl.rutaexpress.notificaciones.dto;

import java.time.LocalDateTime;

public record MensajeEnvelope<T>(
        String tipo,
        String eventoId,
        LocalDateTime timestamp,
        String traceId,
        String correlationId,
        T datos
) {
}
