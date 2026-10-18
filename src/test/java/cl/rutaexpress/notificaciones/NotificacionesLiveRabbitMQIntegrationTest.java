package cl.rutaexpress.notificaciones;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.EstadisticasResponse;
import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.NotificacionEmailPayload;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import cl.rutaexpress.notificaciones.service.EstadisticasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=true"
})
class NotificacionesLiveRabbitMQIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private EstadisticasService estadisticasService;

    @Test
    @DisplayName("Integración Real RabbitMQ: Envío y consumo en vivo a través de las 3 colas de trabajo")
    void testConsumoRealRabbitMQ() throws InterruptedException {
        // 1. Publicar mensaje a q.cmd.email via cmd.direct
        NotificacionEmailPayload emailPayload = new NotificacionEmailPayload(
                100L,
                "RE-LIVE-NOTIF-01",
                "11111111-1",
                "22222222-2",
                "ACEPTADO",
                "Su envio ha sido aceptado",
                "Contenido del correo",
                LocalDateTime.now()
        );
        MensajeEnvelope<NotificacionEmailPayload> emailEnv =
                new MensajeEnvelope<>("EMAIL_ENVIO_ACEPTADO", "ev-100", LocalDateTime.now(), "tr-100", "RE-LIVE-NOTIF-01", emailPayload);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DIRECT, "email.send", emailEnv);

        // 2. Publicar ticket a q.cmd.warehouse via cmd.direct
        TicketBodegaPayload ticketPayload = new TicketBodegaPayload(
                101L,
                "RE-LIVE-NOTIF-02",
                "11111111-1",
                "22222222-2",
                "Bodega Central",
                "Sucursal Oriente",
                new BigDecimal("6.50"),
                LocalDateTime.now(),
                "Preparacion picking prioritaria"
        );
        MensajeEnvelope<TicketBodegaPayload> ticketEnv =
                new MensajeEnvelope<>("TICKET_BODEGA_GENERAR", "ev-101", LocalDateTime.now(), "tr-101", "RE-LIVE-NOTIF-02", ticketPayload);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DIRECT, "warehouse.ticket", ticketEnv);

        // 3. Publicar rotulado a q.cmd.label via cmd.direct
        EtiquetaPayload labelPayload = new EtiquetaPayload(
                102L,
                "RE-LIVE-NOTIF-03",
                "11111111-1",
                "22222222-2",
                "Bodega Central",
                "Sucursal Oriente",
                new BigDecimal("2.10"),
                "TERMICO_10X15",
                LocalDateTime.now()
        );
        MensajeEnvelope<EtiquetaPayload> labelEnv =
                new MensajeEnvelope<>("ETIQUETA_GENERAR", "ev-102", LocalDateTime.now(), "tr-102", "RE-LIVE-NOTIF-03", labelPayload);

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_DIRECT, "label.gen", labelEnv);

        // Esperar unos momentos a que los listeners asíncronos procesen y confirmen con ACK
        Thread.sleep(1500);

        // 4. Consultar endpoint de estadisticas y verificar que los mensajes fueron consumidos
        ResponseEntity<String> rawResp = restTemplate.getForEntity(
                "/api/notificaciones/estadisticas",
                String.class
        );
        System.out.println("DEBUG_STATS_JSON: " + rawResp.getBody());
        assertEquals(HttpStatus.OK, rawResp.getStatusCode());

        EstadisticasResponse stats = estadisticasService.obtenerEstadisticas();
        assertTrue(stats.emailsProcesados() >= 1, "Debe haber procesado al menos 1 email");
        assertTrue(stats.ticketsBodegaProcesados() >= 1, "Debe haber procesado al menos 1 ticket de bodega");
        assertTrue(stats.etiquetasProcesadas() >= 1, "Debe haber procesado al menos 1 etiqueta");
        assertTrue(stats.totalMensajesProcesados() >= 3, "Total de mensajes procesados debe ser >= 3");
    }
}
