package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.service.EtiquetaService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LabelConsumer {

    private final EtiquetaService etiquetaService;
    private final ProcesadorMensajes procesador;

    public LabelConsumer(EtiquetaService etiquetaService, ProcesadorMensajes procesador) {
        this.etiquetaService = etiquetaService;
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_LABEL, ackMode = "MANUAL")
    public void recibirSolicitudEtiqueta(Message message, Channel channel) {
        procesador.procesar(message, channel, TipoMensaje.ETIQUETA, EtiquetaPayload.class,
                etiquetaService::generarEtiqueta);
    }
}
