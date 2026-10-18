package cl.rutaexpress.notificaciones.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SaludController.class)
class SaludControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/publico/salud responde HTTP 200 con estado UP y puerto 8083")
    void testSaludEndpoint() throws Exception {
        mockMvc.perform(get("/api/publico/salud")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("UP"))
                .andExpect(jsonPath("$.servicio").value("ms-rutaexpress-notificaciones"))
                .andExpect(jsonPath("$.puerto").value(8083))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
