package cl.rutaexpress.notificaciones.service;

import cl.rutaexpress.notificaciones.dto.MensajeEnvelope;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TicketBodegaService {

    private static final Logger log = LoggerFactory.getLogger(TicketBodegaService.class);

    private final ObjectMapper objectMapper;

    public TicketBodegaService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String generarTicketPicking(MensajeEnvelope<TicketBodegaPayload> envelope) {
        TicketBodegaPayload datos = envelope.datos();

        if (datos == null || datos.codigoSeguimiento() == null) {
            throw new IllegalArgumentException("Payload de ticket de bodega invalido o vacio");
        }

        Map<String, Object> ticketJson = new LinkedHashMap<>();
        ticketJson.put("formato", "TICKET_PICKING_TERMICO_80MM");
        ticketJson.put("ordenEnvioId", datos.envioId());
        ticketJson.put("codigoSeguimiento", datos.codigoSeguimiento());
        ticketJson.put("codigoBarras", "*RE-" + datos.codigoSeguimiento() + "*");
        ticketJson.put("remitente", datos.rutRemitente());
        ticketJson.put("destinatario", datos.rutDestinatario());
        ticketJson.put("origen", datos.direccionOrigen());
        ticketJson.put("destino", datos.direccionDestino());
        ticketJson.put("pesoKg", datos.pesoKg());
        ticketJson.put("fechaEmision", datos.fechaEmision());
        ticketJson.put("instruccionesBodega", datos.instrucciones());
        ticketJson.put("zonaPicking", "ZONA-SUR-METRO");
        ticketJson.put("estadoImpresion", "LISTO_PARA_IMPRESION");

        try {
            String ticketFormateado = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(ticketJson);

            log.info("========== [TICKET DE PICKING BODEGA GENERADO] ==========");
            log.info("Ticket Picking Generado:\n{}", ticketFormateado);
            log.info("=========================================================");

            return ticketFormateado;
        } catch (JsonProcessingException e) {
            log.error("Error al serializar ticket de picking para envio {}", datos.codigoSeguimiento(), e);
            throw new IllegalStateException("Error al generar JSON de ticket de picking", e);
        }
    }
}
