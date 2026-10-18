package cl.rutaexpress.notificaciones.dto;

import java.time.LocalDateTime;

public record EstadisticasResponse(
        long emailsProcesados,
        long emailsFallidos,
        long ticketsBodegaProcesados,
        long ticketsBodegaFallidos,
        long etiquetasProcesadas,
        long etiquetasFallidas,
        long totalMensajesProcesados,
        long totalMensajesEnviadosADLQ,
        LocalDateTime timestamp
) {
}
