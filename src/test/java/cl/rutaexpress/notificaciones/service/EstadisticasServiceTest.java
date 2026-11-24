package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.consumer.TipoMensaje;
import cl.rutaexpress.notificaciones.dto.EstadisticasResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EstadisticasServiceTest {

    @Test
    void reintentoNoCuentaComoDlq() {
        EstadisticasService service = new EstadisticasService();
        service.registrarReintentoProgramado(TipoMensaje.EMAIL);
        service.registrarReintentoProgramado(TipoMensaje.EMAIL);
        service.registrarFallido(TipoMensaje.BODEGA);

        EstadisticasResponse response = service.obtenerEstadisticas();

        assertEquals(2, response.reintentosProgramados().email());
        assertEquals(0, response.reintentosProgramados().bodega());
        assertEquals(1, response.ticketsBodegaFallidos());
        assertEquals(1, response.totalMensajesEnviadosADLQ());
    }
}
