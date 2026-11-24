package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.consumer.TipoMensaje;
import cl.rutaexpress.notificaciones.dto.EstadisticasResponse;
import cl.rutaexpress.notificaciones.dto.EstadisticasResponse.ReintentosProgramados;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class EstadisticasService {

    private final Map<TipoMensaje, AtomicLong> procesados = new EnumMap<>(TipoMensaje.class);
    private final Map<TipoMensaje, AtomicLong> fallidos = new EnumMap<>(TipoMensaje.class);
    private final Map<TipoMensaje, AtomicLong> reintentos = new EnumMap<>(TipoMensaje.class);
    private final AtomicLong mensajesEnviadosADLQ = new AtomicLong(0);

    public EstadisticasService() {
        for (TipoMensaje tipo : TipoMensaje.values()) {
            procesados.put(tipo, new AtomicLong(0));
            fallidos.put(tipo, new AtomicLong(0));
            reintentos.put(tipo, new AtomicLong(0));
        }
    }

    public void registrarProcesado(TipoMensaje tipo) {
        procesados.get(tipo).incrementAndGet();
    }

    public void registrarFallido(TipoMensaje tipo) {
        fallidos.get(tipo).incrementAndGet();
        mensajesEnviadosADLQ.incrementAndGet();
    }

    public void registrarReintentoProgramado(TipoMensaje tipo) {
        reintentos.get(tipo).incrementAndGet();
    }

    public EstadisticasResponse obtenerEstadisticas() {
        long ep = procesados.get(TipoMensaje.EMAIL).get();
        long ef = fallidos.get(TipoMensaje.EMAIL).get();
        long bp = procesados.get(TipoMensaje.BODEGA).get();
        long bf = fallidos.get(TipoMensaje.BODEGA).get();
        long etp = procesados.get(TipoMensaje.ETIQUETA).get();
        long etf = fallidos.get(TipoMensaje.ETIQUETA).get();

        long totalProcesados = ep + bp + etp;

        ReintentosProgramados reintentosProgramados = new ReintentosProgramados(
                reintentos.get(TipoMensaje.EMAIL).get(),
                reintentos.get(TipoMensaje.BODEGA).get(),
                reintentos.get(TipoMensaje.ETIQUETA).get()
        );

        return new EstadisticasResponse(
                ep,
                ef,
                bp,
                bf,
                etp,
                etf,
                totalProcesados,
                mensajesEnviadosADLQ.get(),
                reintentosProgramados,
                LocalDateTime.now()
        );
    }
}
