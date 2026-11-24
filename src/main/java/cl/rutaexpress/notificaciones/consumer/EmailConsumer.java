package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.NotificacionEmailPayload;
import cl.rutaexpress.notificaciones.service.NotificacionEmailService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class EmailConsumer {

    private final NotificacionEmailService emailService;
    private final ProcesadorMensajes procesador;

    public EmailConsumer(NotificacionEmailService emailService, ProcesadorMensajes procesador) {
        this.emailService = emailService;
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_EMAIL, ackMode = "MANUAL")
    public void recibirNotificacionEmail(Message message, Channel channel) {
        procesador.procesar(message, channel, TipoMensaje.EMAIL, NotificacionEmailPayload.class,
                emailService::procesarNotificacionEmail);
    }
}
