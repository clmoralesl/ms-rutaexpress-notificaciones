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
        ReintentosProgramados reintentosProgramados,
        LocalDateTime timestamp
) {
    public record ReintentosProgramados(long email, long bodega, long etiqueta) {
    }
}
