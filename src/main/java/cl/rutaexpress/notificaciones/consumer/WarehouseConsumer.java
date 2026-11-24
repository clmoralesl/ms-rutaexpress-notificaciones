package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import cl.rutaexpress.notificaciones.service.TicketBodegaService;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WarehouseConsumer {

    private final TicketBodegaService ticketService;
    private final ProcesadorMensajes procesador;

    public WarehouseConsumer(TicketBodegaService ticketService, ProcesadorMensajes procesador) {
        this.ticketService = ticketService;
        this.procesador = procesador;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_WAREHOUSE, ackMode = "MANUAL")
    public void recibirTicketBodega(Message message, Channel channel) {
        procesador.procesar(message, channel, TipoMensaje.BODEGA, TicketBodegaPayload.class,
                ticketService::generarTicketPicking);
    }
}
