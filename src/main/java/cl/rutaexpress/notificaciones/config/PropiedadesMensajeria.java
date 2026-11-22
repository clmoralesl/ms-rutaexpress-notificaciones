package cl.rutaexpress.notificaciones.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Argumentos de las colas de RabbitMQ (fuente unica de verdad, issue #99).
 * Cambiar un valor obliga a recrear las colas existentes: ver infra/mq/README.md.
 */
@Validated
@ConfigurationProperties(prefix = "rutaexpress.mensajeria")
public record PropiedadesMensajeria(
        @Valid @NotNull LimitesCola colaPrincipal,
        @Valid @NotNull LimitesCola colaDlq) {

    public record LimitesCola(
            @NotNull @Positive Integer ttlMs,
            @NotNull @Positive Integer largoMaximo) {
    }
}
