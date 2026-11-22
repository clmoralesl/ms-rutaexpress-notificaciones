package cl.rutaexpress.notificaciones.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.validation.ValidationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopologiaRabbitMQConfigTest {

    private static final String TTL_PRINCIPAL = "rutaexpress.mensajeria.cola-principal.ttl-ms=86400000";
    private static final String LARGO_PRINCIPAL = "rutaexpress.mensajeria.cola-principal.largo-maximo=10000";
    private static final String TTL_DLQ = "rutaexpress.mensajeria.cola-dlq.ttl-ms=604800000";
    private static final String LARGO_DLQ = "rutaexpress.mensajeria.cola-dlq.largo-maximo=50000";

    private ApplicationContextRunner runner(String... propiedades) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(TopologiaRabbitMQConfig.class)
                .withPropertyValues(propiedades);
    }

    private ApplicationContextRunner runnerValido() {
        return runner(TTL_PRINCIPAL, LARGO_PRINCIPAL, TTL_DLQ, LARGO_DLQ);
    }

    @Test
    @DisplayName("C1: colas principales con DLX, DLK, TTL y largo maximo exactos")
    void colasPrincipalesTienenDlxDlkTtlYLargo() {
        runnerValido().run(ctx -> {
            Map<String, String> claves = Map.of(
                    "q.cmd.email", "email.dead",
                    "q.cmd.warehouse", "warehouse.dead",
                    "q.cmd.label", "label.dead");
            Map<String, Queue> colas = ctx.getBeansOfType(Queue.class).values().stream()
                    .collect(Collectors.toMap(Queue::getName, q -> q));
            claves.forEach((nombre, clave) -> {
                Queue q = colas.get(nombre);
                assertNotNull(q, nombre);
                assertTrue(q.isDurable());
                assertFalse(q.isAutoDelete());
                assertFalse(q.isExclusive());
                Map<String, Object> args = q.getArguments();
                assertEquals(4, args.size(), nombre);
                assertEquals("cmd.dead.dlx", args.get("x-dead-letter-exchange"));
                assertEquals(clave, args.get("x-dead-letter-routing-key"));
                assertEquals(86400000L, ((Number) args.get("x-message-ttl")).longValue());
                assertEquals(10000L, ((Number) args.get("x-max-length")).longValue());
            });
        });
    }

    @Test
    @DisplayName("C1 y C4: DLQ con TTL y largo maximo y sin dead letter exchange")
    void colasDlqTienenTtlYLargoSinDlx() {
        runnerValido().run(ctx -> {
            Map<String, Queue> colas = ctx.getBeansOfType(Queue.class).values().stream()
                    .collect(Collectors.toMap(Queue::getName, q -> q));
            for (String nombre : Set.of("q.cmd.email.dlq", "q.cmd.warehouse.dlq", "q.cmd.label.dlq")) {
                Queue q = colas.get(nombre);
                assertNotNull(q, nombre);
                assertTrue(q.isDurable());
                assertFalse(q.isAutoDelete());
                assertFalse(q.isExclusive());
                Map<String, Object> args = q.getArguments();
                assertEquals(2, args.size(), nombre);
                assertFalse(args.containsKey("x-dead-letter-exchange"));
                assertEquals(604800000L, ((Number) args.get("x-message-ttl")).longValue());
                assertEquals(50000L, ((Number) args.get("x-max-length")).longValue());
            }
        });
    }

    @Test
    @DisplayName("C2: exchanges con tipo correcto, durables, sin auto borrado y no internos")
    void exchangesConTipoYDurabilidad() {
        runnerValido().run(ctx -> {
            Map<String, Exchange> exchanges = ctx.getBeansOfType(Exchange.class).values().stream()
                    .collect(Collectors.toMap(Exchange::getName, e -> e));
            assertEquals(3, exchanges.size());
            assertEquals("direct", exchanges.get("cmd.direct").getType());
            assertEquals("topic", exchanges.get("cmd.topic").getType());
            assertEquals("direct", exchanges.get("cmd.dead.dlx").getType());
            exchanges.values().forEach(e -> {
                assertTrue(e.isDurable());
                assertFalse(e.isAutoDelete());
                assertFalse(e.isInternal());
            });
        });
    }

    @Test
    @DisplayName("C3: 15 bindings iguales a infra/mq/definitions.json y 6 colas")
    void bindingsCoincidenConDefinitions() {
        Set<String> esperados = Set.of(
                "cmd.direct|q.cmd.email|email.send",
                "cmd.topic|q.cmd.email|email.*",
                "cmd.dead.dlx|q.cmd.email.dlq|email.dead",
                "cmd.dead.dlx|q.cmd.email.dlq|email.send",
                "cmd.dead.dlx|q.cmd.email.dlq|q.cmd.email.dlq",
                "cmd.direct|q.cmd.warehouse|warehouse.ticket",
                "cmd.topic|q.cmd.warehouse|warehouse.#",
                "cmd.dead.dlx|q.cmd.warehouse.dlq|warehouse.dead",
                "cmd.dead.dlx|q.cmd.warehouse.dlq|warehouse.ticket",
                "cmd.dead.dlx|q.cmd.warehouse.dlq|q.cmd.warehouse.dlq",
                "cmd.direct|q.cmd.label|label.gen",
                "cmd.topic|q.cmd.label|label.*",
                "cmd.dead.dlx|q.cmd.label.dlq|label.dead",
                "cmd.dead.dlx|q.cmd.label.dlq|label.gen",
                "cmd.dead.dlx|q.cmd.label.dlq|q.cmd.label.dlq");
        runnerValido().run(ctx -> {
            Set<String> reales = ctx.getBeansOfType(Binding.class).values().stream()
                    .map(b -> b.getExchange() + "|" + b.getDestination() + "|" + b.getRoutingKey())
                    .collect(Collectors.toSet());
            assertEquals(15, ctx.getBeansOfType(Binding.class).size());
            assertEquals(esperados, reales);
            assertEquals(6, ctx.getBeansOfType(Queue.class).size());
        });
    }

    @Test
    @DisplayName("C5: TTL en cero o negativo impide el arranque")
    void ttlNoPositivoImpideArranque() {
        for (String valor : new String[] {"0", "-1"}) {
            runner("rutaexpress.mensajeria.cola-principal.ttl-ms=" + valor,
                    LARGO_PRINCIPAL, TTL_DLQ, LARGO_DLQ)
                    .run(ctx -> assertNotNull(ctx.getStartupFailure(), "ttl=" + valor));
        }
    }

    @Test
    @DisplayName("C5: propiedad ausente impide el arranque")
    void propiedadAusenteImpideArranque() {
        runner(TTL_PRINCIPAL, LARGO_PRINCIPAL, TTL_DLQ)
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));
    }

    @Test
    @DisplayName("C6: valor mayor que Integer.MAX_VALUE impide el arranque")
    void valorFueraDeRangoImpideArranque() {
        runner("rutaexpress.mensajeria.cola-principal.ttl-ms=3000000000",
                LARGO_PRINCIPAL, TTL_DLQ, LARGO_DLQ)
                .run(ctx -> assertNotNull(ctx.getStartupFailure()));
    }
}
