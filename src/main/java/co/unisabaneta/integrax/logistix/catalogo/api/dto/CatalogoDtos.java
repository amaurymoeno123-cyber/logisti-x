package co.unisabaneta.integrax.logistix.catalogo.api.dto;

import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import co.unisabaneta.integrax.logistix.catalogo.dominio.TipoVehiculo;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cuerpos de peticion y respuesta del maestro logistico: transportadores
 * (RF-05) y rutas de entrega.
 *
 * <p>Van en un solo archivo porque son las dos caras de la misma idea —quien
 * lleva la carga y por donde— y separarlos en ocho archivos de diez lineas
 * solo agrega ruido para quien lee el proyecto.</p>
 */
public final class CatalogoDtos {

    private CatalogoDtos() {
    }

    // ------------------------------------------------------------ transportador

    @Schema(description = "Alta o actualizacion de un transportador")
    public record GuardarTransportador(

            @NotEmpty(message = "El documento es obligatorio")
            @Size(max = 40)
            String documento,

            @NotEmpty(message = "El nombre es obligatorio")
            @Size(max = 160)
            String nombre,

            @Size(max = 40) String telefono,
            @Size(max = 160) String empresa,

            @Schema(example = "CAMIONETA") TipoVehiculo tipoVehiculo,

            @Size(max = 10) String placa,

            @Schema(description = "Tope de carga del vehiculo en kilogramos", example = "1200.000")
            @DecimalMin(value = "0.001", message = "La capacidad debe ser mayor que cero")
            BigDecimal capacidadKg,

            @Schema(description = "Zona en la que opera", example = "Sur del Valle de Aburra")
            @Size(max = 80) String zona) {
    }

    @Schema(description = "Transportador")
    public record TransportadorDto(
            UUID id,
            String documento,
            String nombre,
            String telefono,
            String empresa,
            TipoVehiculo tipoVehiculo,
            String placa,
            BigDecimal capacidadKg,
            String zona,
            boolean activo) {

        public static TransportadorDto de(Transportador t) {
            return new TransportadorDto(t.getId(), t.getDocumento(), t.getNombre(),
                    t.getTelefono(), t.getEmpresa(), t.getTipoVehiculo(), t.getPlaca(),
                    t.getCapacidadKg(), t.getZona(), t.isActivo());
        }
    }

    // -------------------------------------------------------------------- ruta

    @Schema(description = "Alta o actualizacion de una ruta de entrega")
    public record GuardarRuta(

            @NotEmpty(message = "El codigo es obligatorio")
            @Size(max = 20)
            @Schema(example = "MED-BEL")
            String codigo,

            @NotEmpty(message = "El nombre es obligatorio")
            @Size(max = 120)
            String nombre,

            @NotEmpty(message = "La ciudad de origen es obligatoria")
            @Size(max = 80)
            String ciudadOrigen,

            @NotEmpty(message = "La ciudad de destino es obligatoria")
            @Size(max = 80)
            String ciudadDestino,

            @Size(max = 80) String zona,

            @Schema(description = "Dias habiles de transito", example = "1")
            @Min(value = 0, message = "Los dias no pueden ser negativos")
            @Max(value = 60, message = "Una ruta de mas de 60 dias no es una ruta")
            Integer diasEstimados,

            @Schema(description = "Tarifa fija del trayecto, sin contar el peso", example = "8000.00")
            @DecimalMin(value = "0.00", message = "La tarifa no puede ser negativa")
            BigDecimal tarifaBase) {
    }

    @Schema(description = "Ruta de entrega")
    public record RutaDto(
            UUID id,
            String codigo,
            String nombre,
            String ciudadOrigen,
            String ciudadDestino,
            String zona,
            Integer diasEstimados,
            BigDecimal tarifaBase,
            boolean activa) {

        public static RutaDto de(Ruta r) {
            return new RutaDto(r.getId(), r.getCodigo(), r.getNombre(), r.getCiudadOrigen(),
                    r.getCiudadDestino(), r.getZona(), r.getDiasEstimados(),
                    r.getTarifaBase(), r.isActiva());
        }
    }
}
