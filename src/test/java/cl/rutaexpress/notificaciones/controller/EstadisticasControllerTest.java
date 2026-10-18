package cl.rutaexpress.notificaciones.controller;

import cl.rutaexpress.notificaciones.dto.EstadisticasResponse;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EstadisticasController.class)
class EstadisticasControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EstadisticasService estadisticasService;

    @Test
    @DisplayName("GET /api/notificaciones/estadisticas retorna metricas de colas y DLQ")
    void testEstadisticasEndpoint() throws Exception {
        EstadisticasResponse stats = new EstadisticasResponse(
                10L, 1L, 8L, 0L, 5L, 0L, 23L, 1L, LocalDateTime.now()
        );

        when(estadisticasService.obtenerEstadisticas()).thenReturn(stats);

        mockMvc.perform(get("/api/notificaciones/estadisticas")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.emailsProcesados").value(10))
                .andExpect(jsonPath("$.emailsFallidos").value(1))
                .andExpect(jsonPath("$.ticketsBodegaProcesados").value(8))
                .andExpect(jsonPath("$.totalMensajesProcesados").value(23))
                .andExpect(jsonPath("$.totalMensajesEnviadosADLQ").value(1));
    }
}
