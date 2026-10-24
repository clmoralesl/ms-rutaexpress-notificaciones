package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EtiquetaService {

    private static final Logger log = LoggerFactory.getLogger(EtiquetaService.class);
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Cache en memoria para almacenar las etiquetas generadas indexadas por codigo de seguimiento
    private final Map<String, byte[]> etiquetasCache = new ConcurrentHashMap<>();
    private final Map<String, EtiquetaPayload> payloadsCache = new ConcurrentHashMap<>();

    public byte[] generarEtiqueta(MensajeEnvelope<EtiquetaPayload> envelope) {
        EtiquetaPayload datos = envelope.datos();

        if (datos == null || datos.codigoSeguimiento() == null) {
            throw new IllegalArgumentException("Payload de etiqueta termica invalido o vacio");
        }

        byte[] pdfBytes = generarPdf(datos);
        etiquetasCache.put(datos.codigoSeguimiento(), pdfBytes);
        payloadsCache.put(datos.codigoSeguimiento(), datos);

        log.info("========== [GENERACION ETIQUETA TERMICA CON QR EXITOSA] ==========");
        log.info("Codigo Seguimiento: {}", datos.codigoSeguimiento());
        log.info("Formato:            10x15 cm Estandar Termico (OpenPDF)");
        log.info("Tamano PDF:         {} bytes", pdfBytes.length);
        log.info("QR Tracking URL:    https://rutaexpress.cl/rastreo/{}", datos.codigoSeguimiento());
        log.info("==================================================================");

        return pdfBytes;
    }

    public byte[] obtenerPdfPorCodigo(String codigoSeguimiento) {
        byte[] pdf = etiquetasCache.get(codigoSeguimiento);
        if (pdf != null) {
            return pdf;
        }

        // Si no esta en cache previa, se genera una etiqueta bajo demanda con datos base
        EtiquetaPayload payloadBase = payloadsCache.getOrDefault(
                codigoSeguimiento,
                new EtiquetaPayload(
                        null,
                        codigoSeguimiento,
                        "11.111.111-1",
                        "22.222.222-2",
                        "Centro Logistico Central",
                        "Direccion Destino Registrada",
                        null,
                        "PDF_TERMICO_10X15",
                        LocalDateTime.now()
                )
        );

        byte[] generado = generarPdf(payloadBase);
        etiquetasCache.put(codigoSeguimiento, generado);
        return generado;
    }

    public boolean existeEtiqueta(String codigoSeguimiento) {
        return etiquetasCache.containsKey(codigoSeguimiento) || payloadsCache.containsKey(codigoSeguimiento);
    }

    public byte[] generarPdf(EtiquetaPayload datos) {
        try {
            // Dimension estandar 10 x 15 cm en puntos tipograficos (72 puntos por pulgada):
            // 10 cm = 3.937 in = ~283.46 pt
            // 15 cm = 5.905 in = ~425.20 pt
            Rectangle tamanoTermico = new Rectangle(283.5f, 425.2f);
            Document document = new Document(tamanoTermico, 12f, 12f, 12f, 12f);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            // Tipografias formales
            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
            Font fontSubtitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.DARK_GRAY);
            Font fontCodigo = FontFactory.getFont(FontFactory.COURIER_BOLD, 13, Color.BLACK);
            Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
            Font fontTexto = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.BLACK);
            Font fontAviso = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 7, Color.GRAY);

            // 1. Cabecera Corporativa
            PdfPTable tablaHeader = new PdfPTable(2);
            tablaHeader.setWidthPercentage(100);
            tablaHeader.setWidths(new float[]{65f, 35f});

            PdfPCell cellEmpresa = new PdfPCell();
            cellEmpresa.setBorder(Rectangle.NO_BORDER);
            cellEmpresa.addElement(new Paragraph("RUTAEXPRESS", fontTitulo));
            cellEmpresa.addElement(new Paragraph("Transporte & Logistica Inteligente", fontSubtitulo));
            tablaHeader.addCell(cellEmpresa);

            PdfPCell cellServicio = new PdfPCell();
            cellServicio.setBorder(Rectangle.BOX);
            cellServicio.setBorderWidth(1.2f);
            cellServicio.setBackgroundColor(new Color(240, 240, 240));
            cellServicio.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellServicio.setVerticalAlignment(Element.ALIGN_MIDDLE);
            cellServicio.addElement(new Paragraph("EXPRESS", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.BLACK)));
            cellServicio.addElement(new Paragraph("ESTANDAR", FontFactory.getFont(FontFactory.HELVETICA, 8, Color.DARK_GRAY)));
            tablaHeader.addCell(cellServicio);

            document.add(tablaHeader);

            // Separador horizontal
            Paragraph separador = new Paragraph("________________________________________________", fontTexto);
            separador.setAlignment(Element.ALIGN_CENTER);
            document.add(separador);

            // 2. Codigo de Seguimiento y QR de Rastreo
            String trackingUrl = "https://rutaexpress.cl/rastreo/" + datos.codigoSeguimiento();
            byte[] qrBytes = generarQrBytes(trackingUrl, 120, 120);
            Image qrImage = Image.getInstance(qrBytes);
            qrImage.setAlignment(Element.ALIGN_CENTER);

            PdfPTable tablaTracking = new PdfPTable(2);
            tablaTracking.setWidthPercentage(100);
            tablaTracking.setWidths(new float[]{55f, 45f});
            tablaTracking.setSpacingBefore(8f);

            PdfPCell cellCodigos = new PdfPCell();
            cellCodigos.setBorder(Rectangle.NO_BORDER);
            cellCodigos.addElement(new Paragraph("CODIGO DE ENVIO:", fontLabel));
            cellCodigos.addElement(new Paragraph(datos.codigoSeguimiento(), fontCodigo));
            cellCodigos.addElement(new Paragraph("FECHA: " + (datos.fechaSolicitud() != null ? datos.fechaSolicitud().format(FORMATO_FECHA) : LocalDateTime.now().format(FORMATO_FECHA)), fontTexto));
            cellCodigos.addElement(new Paragraph("PESO REGISTRADO: " + (datos.pesoKg() != null ? datos.pesoKg() + " KG" : "N/A"), fontLabel));
            tablaTracking.addCell(cellCodigos);

            PdfPCell cellQr = new PdfPCell();
            cellQr.setBorder(Rectangle.NO_BORDER);
            cellQr.setHorizontalAlignment(Element.ALIGN_CENTER);
            cellQr.addElement(qrImage);
            Paragraph qrTexto = new Paragraph("Escanear para Seguimiento", fontAviso);
            qrTexto.setAlignment(Element.ALIGN_CENTER);
            cellQr.addElement(qrTexto);
            tablaTracking.addCell(cellQr);

            document.add(tablaTracking);

            // 3. Seccion Remitente
            PdfPTable tablaRemitente = new PdfPTable(1);
            tablaRemitente.setWidthPercentage(100);
            tablaRemitente.setSpacingBefore(6f);

            PdfPCell cellRemitente = new PdfPCell();
            cellRemitente.setBorder(Rectangle.BOX);
            cellRemitente.setBorderWidth(0.8f);
            cellRemitente.setBackgroundColor(new Color(248, 248, 248));
            cellRemitente.setPadding(6f);
            cellRemitente.addElement(new Paragraph("REMITENTE (ORIGEN):", fontLabel));
            cellRemitente.addElement(new Paragraph("RUT: " + (datos.rutRemitente() != null ? datos.rutRemitente() : "No especificado"), fontTexto));
            if (datos.calleOrigen() != null && !datos.calleOrigen().isBlank()) {
                String lineaCalle = "CALLE: " + datos.calleOrigen() + " " + (datos.numeroOrigen() != null ? datos.numeroOrigen() : "")
                        + (datos.complementoOrigen() != null && !datos.complementoOrigen().isBlank() ? " - " + datos.complementoOrigen() : "");
                cellRemitente.addElement(new Paragraph(lineaCalle, fontTexto));
                String lineaGeo = "COMUNA / REGION: " + (datos.comunaOrigen() != null ? datos.comunaOrigen() : "")
                        + (datos.regionOrigen() != null ? " (" + datos.regionOrigen() + ")" : "");
                cellRemitente.addElement(new Paragraph(lineaGeo, fontTexto));
            } else {
                cellRemitente.addElement(new Paragraph("DIRECCION ORIGEN: " + (datos.direccionOrigen() != null ? datos.direccionOrigen() : "Centro de Despacho Principal"), fontTexto));
            }
            tablaRemitente.addCell(cellRemitente);
            document.add(tablaRemitente);

            // 4. Seccion Destinatario (Destacada)
            PdfPTable tablaDestinatario = new PdfPTable(1);
            tablaDestinatario.setWidthPercentage(100);
            tablaDestinatario.setSpacingBefore(6f);

            PdfPCell cellDestinatario = new PdfPCell();
            cellDestinatario.setBorder(Rectangle.BOX);
            cellDestinatario.setBorderWidth(1.2f);
            cellDestinatario.setBackgroundColor(new Color(255, 255, 255));
            cellDestinatario.setPadding(6f);
            cellDestinatario.addElement(new Paragraph("DESTINATARIO (ENTREGA FINAL):", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK)));
            cellDestinatario.addElement(new Paragraph("RUT: " + (datos.rutDestinatario() != null ? datos.rutDestinatario() : "No especificado"), fontLabel));
            if (datos.calleDestino() != null && !datos.calleDestino().isBlank()) {
                String lineaCalle = "DIRECCION: " + datos.calleDestino() + " " + (datos.numeroDestino() != null ? datos.numeroDestino() : "")
                        + (datos.complementoDestino() != null && !datos.complementoDestino().isBlank() ? " - " + datos.complementoDestino() : "");
                cellDestinatario.addElement(new Paragraph(lineaCalle, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK)));
                String lineaGeo = "COMUNA / REGION: " + (datos.comunaDestino() != null ? datos.comunaDestino() : "")
                        + (datos.regionDestino() != null ? " (" + datos.regionDestino() + ")" : "");
                cellDestinatario.addElement(new Paragraph(lineaGeo, fontTexto));
            } else {
                cellDestinatario.addElement(new Paragraph("DIRECCION DESTINO: " + (datos.direccionDestino() != null ? datos.direccionDestino() : "Direccion no especificada"), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK)));
            }
            tablaDestinatario.addCell(cellDestinatario);
            document.add(tablaDestinatario);

            // 5. Pie de Pagina de Control de Bodega
            Paragraph pie = new Paragraph();
            pie.setSpacingBefore(10f);
            pie.setAlignment(Element.ALIGN_CENTER);
            pie.add(new Paragraph("* GUIA DE DESPACHO Y ROTULADO OFICIAL RUTAEXPRESS *", fontAviso));
            pie.add(new Paragraph("Valido para recepcion, inspeccion fisica y entrega en ruta", fontAviso));
            document.add(pie);

            document.close();
            return out.toByteArray();

        } catch (Exception ex) {
            log.error("Error al generar PDF de etiqueta para codigo {}: {}", datos.codigoSeguimiento(), ex.getMessage());
            throw new IllegalStateException("Error al construir documento PDF de etiqueta termica", ex);
        }
    }

    private byte[] generarQrBytes(String texto, int ancho, int alto) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(texto, BarcodeFormat.QR_CODE, ancho, alto);
        ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
        return pngOutputStream.toByteArray();
    }
}
