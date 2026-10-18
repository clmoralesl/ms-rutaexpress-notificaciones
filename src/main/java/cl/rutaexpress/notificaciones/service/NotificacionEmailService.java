package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.NotificacionEmailPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacionEmailService {

    private static final Logger log = LoggerFactory.getLogger(NotificacionEmailService.class);

    public void procesarNotificacionEmail(MensajeEnvelope<NotificacionEmailPayload> envelope) {
        NotificacionEmailPayload datos = envelope.datos();

        if (datos == null || datos.codigoSeguimiento() == null) {
            throw new IllegalArgumentException("Payload de correo invalido o vacio");
        }

        log.info("========== [DESPACHO NOTIFICACION EMAIL / WEBPUSH] ==========");
        log.info("Evento ID:         {}", envelope.eventoId());
        log.info("Correlation ID:    {}", envelope.correlationId());
        log.info("Trace ID:          {}", envelope.traceId());
        log.info("Tipo Evento:       {}", envelope.tipo());
        log.info("Codigo Seguimiento:{}", datos.codigoSeguimiento());
        log.info("RUT Remitente:     {}", datos.rutRemitente());
        log.info("RUT Destinatario:  {}", datos.rutDestinatario());
        log.info("Nuevo Estado:      {}", datos.estado());
        log.info("Asunto:            {}", datos.asunto());
        log.info("Mensaje:           {}", datos.mensaje());
        log.info("Canal:             SMTP / WebPush Simulado (200 OK)");
        log.info("=============================================================");
    }
}
