package cl.rutaexpress.notificaciones.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/publico/salud")
public class SaludController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> verificarSalud() {
        return ResponseEntity.ok(Map.of(
                "estado", "UP",
                "servicio", "ms-rutaexpress-notificaciones",
                "puerto", 8083,
                "timestamp", LocalDateTime.now().toString()
        ));
    }
}
