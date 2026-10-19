package cl.rutaexpress.notificaciones.controller;

import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/notificaciones/etiquetas")
public class EtiquetaController {

    private final EtiquetaService etiquetaService;

    public EtiquetaController(EtiquetaService etiquetaService) {
        this.etiquetaService = etiquetaService;
    }

    @GetMapping(value = "/{codigoSeguimiento}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> descargarEtiquetaPdf(@PathVariable String codigoSeguimiento) {
        byte[] pdfBytes = etiquetaService.obtenerPdfPorCodigo(codigoSeguimiento);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"etiqueta-" + codigoSeguimiento + ".pdf\"");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.OK);
    }

    @PostMapping(value = "/generar", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> generarEtiquetaManual(@RequestBody EtiquetaPayload payload) {
        MensajeEnvelope<EtiquetaPayload> envelope = MensajeEnvelope.de(
                "ETIQUETA_MANUAL_GENERAR",
                payload.codigoSeguimiento(),
                payload
        );

        byte[] pdfBytes = etiquetaService.generarEtiqueta(envelope);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"etiqueta-" + payload.codigoSeguimiento() + ".pdf\"");
        headers.setContentLength(pdfBytes.length);

        return new ResponseEntity<>(pdfBytes, headers, HttpStatus.CREATED);
    }

    @GetMapping("/{codigoSeguimiento}/estado")
    public ResponseEntity<Map<String, Object>> consultarEstadoEtiqueta(@PathVariable String codigoSeguimiento) {
        boolean existe = etiquetaService.existeEtiqueta(codigoSeguimiento);

        Map<String, Object> respuesta = Map.of(
                "codigoSeguimiento", codigoSeguimiento,
                "disponible", existe,
                "formato", "PDF_TERMICO_10X15",
                "urlDescarga", "/api/notificaciones/etiquetas/" + codigoSeguimiento + "/pdf"
        );

        return ResponseEntity.ok(respuesta);
    }
}
