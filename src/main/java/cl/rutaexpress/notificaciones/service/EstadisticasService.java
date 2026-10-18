package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.dto.EstadisticasResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EstadisticasService {

    private final AtomicLong emailsProcesados = new AtomicLong(0);
    private final AtomicLong emailsFallidos = new AtomicLong(0);

    private final AtomicLong ticketsBodegaProcesados = new AtomicLong(0);
    private final AtomicLong ticketsBodegaFallidos = new AtomicLong(0);

    private final AtomicLong etiquetasProcesadas = new AtomicLong(0);
    private final AtomicLong etiquetasFallidas = new AtomicLong(0);

    private final AtomicLong mensajesEnviadosADLQ = new AtomicLong(0);

    public void registrarEmailProcesado() {
        emailsProcesados.incrementAndGet();
    }

    public void registrarEmailFallido() {
        emailsFallidos.incrementAndGet();
        mensajesEnviadosADLQ.incrementAndGet();
    }

    public void registrarTicketBodegaProcesado() {
        ticketsBodegaProcesados.incrementAndGet();
    }

    public void registrarTicketBodegaFallido() {
        ticketsBodegaFallidos.incrementAndGet();
        mensajesEnviadosADLQ.incrementAndGet();
    }

    public void registrarEtiquetaProcesada() {
        etiquetasProcesadas.incrementAndGet();
    }

    public void registrarEtiquetaFallida() {
        etiquetasFallidas.incrementAndGet();
        mensajesEnviadosADLQ.incrementAndGet();
    }

    public EstadisticasResponse obtenerEstadisticas() {
        long totalProcesados = emailsProcesados.get() + ticketsBodegaProcesados.get() + etiquetasProcesadas.get();
        return new EstadisticasResponse(
                emailsProcesados.get(),
                emailsFallidos.get(),
                ticketsBodegaProcesados.get(),
                ticketsBodegaFallidos.get(),
                etiquetasProcesadas.get(),
                etiquetasFallidas.get(),
                totalProcesados,
                mensajesEnviadosADLQ.get(),
                LocalDateTime.now()
        );
    }
}
