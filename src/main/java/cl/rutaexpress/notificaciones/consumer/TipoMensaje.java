package cl.rutaexpress.notificaciones.consumer;

import cl.rutaexpress.notificaciones.config.RabbitMQConfig;

public enum TipoMensaje {
    EMAIL(RabbitMQConfig.QUEUE_EMAIL_RETRY),
    BODEGA(RabbitMQConfig.QUEUE_WAREHOUSE_RETRY),
    ETIQUETA(RabbitMQConfig.QUEUE_LABEL_RETRY);

    private final String colaEspera;

    TipoMensaje(String colaEspera) {
        this.colaEspera = colaEspera;
    }

    public String colaEspera() {
        return colaEspera;
    }
}
