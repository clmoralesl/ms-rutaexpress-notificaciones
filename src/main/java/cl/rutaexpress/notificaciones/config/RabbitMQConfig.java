package cl.rutaexpress.notificaciones.config;

import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.DefaultJackson2JavaTypeMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_EMAIL = "q.cmd.email";
    public static final String QUEUE_EMAIL_DLQ = "q.cmd.email.dlq";

    public static final String QUEUE_WAREHOUSE = "q.cmd.warehouse";
    public static final String QUEUE_WAREHOUSE_DLQ = "q.cmd.warehouse.dlq";

    public static final String QUEUE_LABEL = "q.cmd.label";
    public static final String QUEUE_LABEL_DLQ = "q.cmd.label.dlq";

    public static final String EXCHANGE_DIRECT = "cmd.direct";
    public static final String EXCHANGE_TOPIC = "cmd.topic";
    public static final String EXCHANGE_DEAD_LETTER = "cmd.dead.dlx";

    public static ObjectMapper construirObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(construirObjectMapper());
        DefaultJackson2JavaTypeMapper typeMapper = new DefaultJackson2JavaTypeMapper();
        typeMapper.setTrustedPackages("*");
        Map<String, Class<?>> idClassMapping = new HashMap<>();
        idClassMapping.put("cl.rutaexpress.envios.dto.MensajeEnvelope", MensajeEnvelope.class);
        idClassMapping.put("cl.rutaexpress.notificaciones.dto.MensajeEnvelope", MensajeEnvelope.class);
        typeMapper.setIdClassMapping(idClassMapping);
        converter.setJavaTypeMapper(typeMapper);
        return converter;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setPrefetchCount(10);
        return factory;
    }
}
