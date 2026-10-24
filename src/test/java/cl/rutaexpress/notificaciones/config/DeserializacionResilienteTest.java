package cl.rutaexpress.notificaciones.config;

import cl.rutaexpress.notificaciones.dto.EtiquetaPayload;
import cl.rutaexpress.notificaciones.dto.TicketBodegaPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class DeserializacionResilienteTest {

    @Test
    @DisplayName("RabbitMQConfig.construirObjectMapper tolera campos desconocidos y mapea campos estructurados en EtiquetaPayload")
    void testEtiquetaPayloadConCamposDesconocidos() throws Exception {
        String jsonConCamposNuevos = """
            {
                "envioId": 100,
                "codigoSeguimiento": "RTX-2026-TEST01",
                "rutRemitente": "11111111-1",
                "rutDestinatario": "22222222-2",
                "direccionOrigen": "Av. Providencia 1234, Providencia, Region Metropolitana",
                "direccionDestino": "Calle Los Aromos 456, Maipu, Region Metropolitana",
                "pesoKg": 4.5,
                "formatoEtiqueta": "PDF_TERMICO_10X15",
                "calleOrigen": "Av. Providencia",
                "numeroOrigen": "1234",
                "complementoOrigen": "Oficina 301",
                "comunaOrigen": "Providencia",
                "regionOrigen": "Metropolitana de Santiago",
                "calleDestino": "Calle Los Aromos",
                "numeroDestino": "456",
                "complementoDestino": null,
                "comunaDestino": "Maipu",
                "regionDestino": "Metropolitana de Santiago",
                "campoDesconocidoNuevo": "datoInesperado",
                "metadatosFuturos": {
                    "sensorTemperatura": 22.4,
                    "versionPayload": 3
                }
            }
            """;

        ObjectMapper mapper = RabbitMQConfig.construirObjectMapper();
        EtiquetaPayload payload = mapper.readValue(jsonConCamposNuevos, EtiquetaPayload.class);

        assertNotNull(payload);
        assertEquals(100L, payload.envioId());
        assertEquals("RTX-2026-TEST01", payload.codigoSeguimiento());
        assertEquals("11111111-1", payload.rutRemitente());
        assertEquals(new BigDecimal("4.5"), payload.pesoKg());
        assertEquals("Av. Providencia", payload.calleOrigen());
        assertEquals("1234", payload.numeroOrigen());
        assertEquals("Oficina 301", payload.complementoOrigen());
        assertEquals("Providencia", payload.comunaOrigen());
        assertEquals("Calle Los Aromos", payload.calleDestino());
        assertEquals("Maipu", payload.comunaDestino());
    }

    @Test
    @DisplayName("RabbitMQConfig.construirObjectMapper tolera campos desconocidos y mapea campos estructurados en TicketBodegaPayload")
    void testTicketBodegaPayloadConCamposDesconocidos() throws Exception {
        String jsonTicket = """
            {
                "envioId": 200,
                "codigoSeguimiento": "RTX-2026-TICKET01",
                "rutRemitente": "11111111-1",
                "rutDestinatario": "22222222-2",
                "direccionOrigen": "Bodega Central",
                "direccionDestino": "Sucursal Norte",
                "pesoKg": 12.0,
                "instrucciones": "Manejar con cuidado",
                "calleOrigen": "Av. Libertador",
                "numeroOrigen": "2000",
                "comunaOrigen": "Santiago",
                "regionOrigen": "Metropolitana de Santiago",
                "calleDestino": "Av. Pedro de Valdivia",
                "numeroDestino": "500",
                "comunaDestino": "Concepcion",
                "regionDestino": "Biobio",
                "atributoInesperado": 999,
                "extensionBodega": {
                    "rack": "A-12",
                    "nivel": 3
                }
            }
            """;

        ObjectMapper mapper = RabbitMQConfig.construirObjectMapper();
        TicketBodegaPayload payload = mapper.readValue(jsonTicket, TicketBodegaPayload.class);

        assertNotNull(payload);
        assertEquals(200L, payload.envioId());
        assertEquals("RTX-2026-TICKET01", payload.codigoSeguimiento());
        assertEquals("Manejar con cuidado", payload.instrucciones());
        assertEquals("Av. Libertador", payload.calleOrigen());
        assertEquals("Santiago", payload.comunaOrigen());
        assertEquals("Concepcion", payload.comunaDestino());
    }

    @Test
    @DisplayName("Verificacion de que un ObjectMapper estandar sin la configuracion de RabbitMQConfig falla ante campos desconocidos")
    void testObjectMapperEstandarFallaAnteCamposDesconocidos() {
        String jsonConCampoDesconocido = """
            {
                "envioId": 300,
                "codigoSeguimiento": "RTX-2026-TEST03",
                "campoNoReconocido": "valor"
            }
            """;

        ObjectMapper mapperEstandar = new ObjectMapper();
        assertThrows(UnrecognizedPropertyException.class, () -> {
            mapperEstandar.readValue(jsonConCampoDesconocido, EtiquetaPayload.class);
        });
    }
}
