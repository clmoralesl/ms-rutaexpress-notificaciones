package cl.rutaexpress.notificaciones;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Requiere el broker local levantado con infra/mq/compose.yml.
 * Se ejecuta con: mvn verify -Pintegracion-externa
 */
@Tag("integracion-externa")
@SpringBootTest
@TestPropertySource(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class TopologiaRabbitMQIntegracionTest {

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Test
    @DisplayName("C8: redeclarar sobre la topologia existente no produce PRECONDITION_FAILED")
    void redeclararSobreTopologiaExistenteNoFalla() {
        assertDoesNotThrow(rabbitAdmin::initialize);
        List<String> colas = List.of(
                "q.cmd.email", "q.cmd.email.dlq", "q.cmd.email.retry",
                "q.cmd.warehouse", "q.cmd.warehouse.dlq", "q.cmd.warehouse.retry",
                "q.cmd.label", "q.cmd.label.dlq", "q.cmd.label.retry");
        for (String nombre : colas) {
            assertNotNull(rabbitAdmin.getQueueInfo(nombre), nombre);
        }
    }
}
