package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class EtiquetaService {

    private static final Logger log = LoggerFactory.getLogger(EtiquetaService.class);

    public void generarEtiqueta(MensajeEnvelope<EtiquetaPayload> envelope) {
        EtiquetaPayload datos = envelope.datos();

        if (datos == null || datos.codigoSeguimiento() == null) {
            throw new IllegalArgumentException("Payload de etiqueta termica invalido o vacio");
        }

        log.info("========== [GENERACION ETIQUETA TERMICA CON QR] ==========");
        log.info("Codigo Seguimiento: {}", datos.codigoSeguimiento());
        log.info("Formato:            {} (10x15 cm Estandar)", datos.formatoEtiqueta() != null ? datos.formatoEtiqueta() : "PDF_TERMICO");
        log.info("QR Tracking URL:    https://rutaexpress.cl/rastreo/{}", datos.codigoSeguimiento());
        log.info("Destinatario:       {} ({})", datos.rutDestinatario(), datos.direccionDestino());
        log.info("Peso Registrado:    {} kg", datos.pesoKg());
        log.info("Estado Rotulado:    ETIQUETA_GENERADA_EXITOSAMENTE");
        log.info("==========================================================");
    }
}
