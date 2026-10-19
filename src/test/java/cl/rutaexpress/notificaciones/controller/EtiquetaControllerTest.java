package cl.rutaexpress.notificaciones.controller;

import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EtiquetaController.class)
class EtiquetaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @SpyBean
    private EtiquetaService etiquetaService;

    @Test
    @DisplayName("GET /api/notificaciones/etiquetas/{codigo}/pdf retorna un documento PDF valido 10x15 cm")
    void testDescargarEtiquetaPdfRetornaPdfValido() throws Exception {
        String codigoSeguimiento = "RT-2026-PRUEBA-01";

        byte[] respuestaPdf = mockMvc.perform(get("/api/notificaciones/etiquetas/{codigo}/pdf", codigoSeguimiento))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("inline; filename=\"etiqueta-RT-2026-PRUEBA-01.pdf\"")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        // Validar que el archivo generado contiene la cabecera estandar de un PDF (%PDF-)
        assertTrue(respuestaPdf.length > 500, "El tamano del PDF generado debe ser mayor a 500 bytes");
        String cabecera = new String(respuestaPdf, 0, 5);
        assertTrue(cabecera.startsWith("%PDF-"), "El contenido debe iniciar con la cabecera valida de PDF %PDF-");
    }

    @Test
    @DisplayName("POST /api/notificaciones/etiquetas/generar genera y retorna PDF con metadatos personalizados")
    void testGenerarEtiquetaManual() throws Exception {
        EtiquetaPayload payload = new EtiquetaPayload(
                101L,
                "RT-MANUAL-999",
                "18.123.456-7",
                "19.987.654-3",
                "Av. Libertador Bernardo O'Higgins 1000",
                "Calle Los Conquistadores 2000, Providencia",
                new BigDecimal("5.50"),
                "PDF_TERMICO_10X15",
                LocalDateTime.now()
        );

        byte[] respuestaPdf = mockMvc.perform(post("/api/notificaciones/etiquetas/generar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition", containsString("inline; filename=\"etiqueta-RT-MANUAL-999.pdf\"")))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertTrue(respuestaPdf.length > 500, "El tamano del PDF generado debe ser mayor a 500 bytes");
        String cabecera = new String(respuestaPdf, 0, 5);
        assertTrue(cabecera.startsWith("%PDF-"));
    }

    @Test
    @DisplayName("GET /api/notificaciones/etiquetas/{codigo}/estado retorna metadatos en JSON")
    void testConsultarEstadoEtiqueta() throws Exception {
        mockMvc.perform(get("/api/notificaciones/etiquetas/{codigo}/estado", "RT-ESTADO-123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.codigoSeguimiento", is("RT-ESTADO-123")))
                .andExpect(jsonPath("$.formato", is("PDF_TERMICO_10X15")))
                .andExpect(jsonPath("$.urlDescarga", is("/api/notificaciones/etiquetas/RT-ESTADO-123/pdf")));
    }
}
