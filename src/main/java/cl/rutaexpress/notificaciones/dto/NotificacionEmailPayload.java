package cl.rutaexpress.notificaciones.dto;

import java.time.LocalDateTime;

public record NotificacionEmailPayload(
        Long envioId,
        String codigoSeguimiento,
        String rutRemitente,
        String rutDestinatario,
        String estado,
        String asunto,
        String mensaje,
        LocalDateTime fecha
) {
}
