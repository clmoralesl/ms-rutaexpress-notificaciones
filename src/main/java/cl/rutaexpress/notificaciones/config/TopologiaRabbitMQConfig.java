package cl.rutaexpress.notificaciones.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia de RabbitMQ que consume este servicio: 3 exchanges, 9 colas y 15 bindings.
 * Debe coincidir con infra/mq/definitions.json y con infra/aws-ec2/scripts/bootstrap-mq.sh.
 */
@Configuration
@EnableConfigurationProperties(PropiedadesMensajeria.class)
public class TopologiaRabbitMQConfig {

    private final PropiedadesMensajeria propiedades;

    public TopologiaRabbitMQConfig(PropiedadesMensajeria propiedades) {
        this.propiedades = propiedades;
    }

    // ===== Exchanges =====

    @Bean
    public DirectExchange exchangeDirecto() {
        return ExchangeBuilder.directExchange(RabbitMQConfig.EXCHANGE_DIRECT).durable(true).build();
    }

    @Bean
    public TopicExchange exchangeTopico() {
        return ExchangeBuilder.topicExchange(RabbitMQConfig.EXCHANGE_TOPIC).durable(true).build();
    }

    @Bean
    public DirectExchange exchangeDeadLetter() {
        return ExchangeBuilder.directExchange(RabbitMQConfig.EXCHANGE_DEAD_LETTER).durable(true).build();
    }

    // ===== Caso de uso: correo =====

    @Bean
    public Queue colaCorreo() {
        return colaPrincipal(RabbitMQConfig.QUEUE_EMAIL, RabbitMQConfig.DEAD_LETTER_KEY_EMAIL);
    }

    @Bean
    public Queue colaCorreoDlq() {
        return colaDlq(RabbitMQConfig.QUEUE_EMAIL_DLQ);
    }

    @Bean
    public Queue colaCorreoEspera() {
        return colaEspera(RabbitMQConfig.QUEUE_EMAIL_RETRY, RabbitMQConfig.ROUTING_KEY_EMAIL);
    }

    @Bean
    public Binding enlaceCorreoDirecto() {
        return BindingBuilder.bind(colaCorreo()).to(exchangeDirecto()).with(RabbitMQConfig.ROUTING_KEY_EMAIL);
    }

    @Bean
    public Binding enlaceCorreoTopico() {
        return BindingBuilder.bind(colaCorreo()).to(exchangeTopico()).with(RabbitMQConfig.PATRON_EMAIL);
    }

    @Bean
    public Binding enlaceCorreoDlqClaveMuerta() {
        return BindingBuilder.bind(colaCorreoDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.DEAD_LETTER_KEY_EMAIL);
    }

    @Bean
    public Binding enlaceCorreoDlqClaveOriginal() {
        return BindingBuilder.bind(colaCorreoDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.ROUTING_KEY_EMAIL);
    }

    @Bean
    public Binding enlaceCorreoDlqNombreCola() {
        return BindingBuilder.bind(colaCorreoDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.QUEUE_EMAIL_DLQ);
    }

    // ===== Caso de uso: bodega =====

    @Bean
    public Queue colaBodega() {
        return colaPrincipal(RabbitMQConfig.QUEUE_WAREHOUSE, RabbitMQConfig.DEAD_LETTER_KEY_WAREHOUSE);
    }

    @Bean
    public Queue colaBodegaDlq() {
        return colaDlq(RabbitMQConfig.QUEUE_WAREHOUSE_DLQ);
    }

    @Bean
    public Queue colaBodegaEspera() {
        return colaEspera(RabbitMQConfig.QUEUE_WAREHOUSE_RETRY, RabbitMQConfig.ROUTING_KEY_WAREHOUSE);
    }

    @Bean
    public Binding enlaceBodegaDirecto() {
        return BindingBuilder.bind(colaBodega()).to(exchangeDirecto()).with(RabbitMQConfig.ROUTING_KEY_WAREHOUSE);
    }

    @Bean
    public Binding enlaceBodegaTopico() {
        return BindingBuilder.bind(colaBodega()).to(exchangeTopico()).with(RabbitMQConfig.PATRON_WAREHOUSE);
    }

    @Bean
    public Binding enlaceBodegaDlqClaveMuerta() {
        return BindingBuilder.bind(colaBodegaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.DEAD_LETTER_KEY_WAREHOUSE);
    }

    @Bean
    public Binding enlaceBodegaDlqClaveOriginal() {
        return BindingBuilder.bind(colaBodegaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.ROUTING_KEY_WAREHOUSE);
    }

    @Bean
    public Binding enlaceBodegaDlqNombreCola() {
        return BindingBuilder.bind(colaBodegaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.QUEUE_WAREHOUSE_DLQ);
    }

    // ===== Caso de uso: etiqueta =====

    @Bean
    public Queue colaEtiqueta() {
        return colaPrincipal(RabbitMQConfig.QUEUE_LABEL, RabbitMQConfig.DEAD_LETTER_KEY_LABEL);
    }

    @Bean
    public Queue colaEtiquetaDlq() {
        return colaDlq(RabbitMQConfig.QUEUE_LABEL_DLQ);
    }

    @Bean
    public Queue colaEtiquetaEspera() {
        return colaEspera(RabbitMQConfig.QUEUE_LABEL_RETRY, RabbitMQConfig.ROUTING_KEY_LABEL);
    }

    @Bean
    public Binding enlaceEtiquetaDirecto() {
        return BindingBuilder.bind(colaEtiqueta()).to(exchangeDirecto()).with(RabbitMQConfig.ROUTING_KEY_LABEL);
    }

    @Bean
    public Binding enlaceEtiquetaTopico() {
        return BindingBuilder.bind(colaEtiqueta()).to(exchangeTopico()).with(RabbitMQConfig.PATRON_LABEL);
    }

    @Bean
    public Binding enlaceEtiquetaDlqClaveMuerta() {
        return BindingBuilder.bind(colaEtiquetaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.DEAD_LETTER_KEY_LABEL);
    }

    @Bean
    public Binding enlaceEtiquetaDlqClaveOriginal() {
        return BindingBuilder.bind(colaEtiquetaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.ROUTING_KEY_LABEL);
    }

    @Bean
    public Binding enlaceEtiquetaDlqNombreCola() {
        return BindingBuilder.bind(colaEtiquetaDlq()).to(exchangeDeadLetter()).with(RabbitMQConfig.QUEUE_LABEL_DLQ);
    }

    private Queue colaPrincipal(String nombre, String claveDeadLetter) {
        return QueueBuilder.durable(nombre)
                .deadLetterExchange(RabbitMQConfig.EXCHANGE_DEAD_LETTER)
                .deadLetterRoutingKey(claveDeadLetter)
                .ttl(propiedades.colaPrincipal().ttlMs())
                .maxLength(propiedades.colaPrincipal().largoMaximo())
                .build();
    }

    private Queue colaDlq(String nombre) {
        return QueueBuilder.durable(nombre)
                .ttl(propiedades.colaDlq().ttlMs())
                .maxLength(propiedades.colaDlq().largoMaximo())
                .build();
    }

    private Queue colaEspera(String nombre, String claveOriginal) {
        return QueueBuilder.durable(nombre)
                .ttl(propiedades.reintentos().esperaMs())
                .deadLetterExchange(RabbitMQConfig.EXCHANGE_DIRECT)
                .deadLetterRoutingKey(claveOriginal)
                .build();
    }
}
