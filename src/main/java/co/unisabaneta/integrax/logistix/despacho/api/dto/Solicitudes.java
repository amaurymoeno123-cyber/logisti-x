package co.unisabaneta.integrax.logistix.despacho.api.dto;

import co.unisabaneta.integrax.logistix.despacho.dominio.TipoNovedad;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Cuerpos de peticion de la API de despachos.
 *
 * <p><b>Regla que atraviesa todos estos objetos:</b> aqui no entra ni un peso
 * total, ni un flete, ni un numero de guia. El cliente HTTP dice <i>que</i>
 * pedido se despacha y <i>a donde</i>; el peso lo calcula el servidor desde la
 * carga, el flete lo calcula el servidor desde la ruta y la guia la genera el
 * servidor. Si el flete se aceptara del cliente, cualquiera se mandaria un
 * envio nacional por mil pesos cambiando el JSON.</p>
 */
public final class Solicitudes {

    private Solicitudes() {
    }

    @Schema(description = "Datos para registrar un despacho contra un pedido de SALES-X")
    public record CrearDespacho(

            @Schema(description = "pedidoId registrado en SALES-X",
                    example = "55555555-0001-4000-8000-000000000001")
            @NotNull(message = "El pedidoId es obligatorio")
            UUID pedidoId,

            @Schema(description = "Direccion de entrega. Si se omite, se toma la de CRM-X.",
                    example = "Carrera 48 # 32-45, Bodega 3")
            @Size(max = 300, message = "La direccion no puede pasar de 300 caracteres")
            String direccionEntrega,

            @Schema(description = "Ciudad de entrega. Si se omite, se toma la de CRM-X.",
                    example = "Medellin")
            @Size(max = 80)
            String ciudadEntrega,

            @Size(max = 80)
            String departamentoEntrega,

            @Schema(description = "Punto de referencia para encontrar la direccion",
                    example = "Porton azul, al lado de la panaderia")
            @Size(max = 200)
            String referenciaDireccion,

            @Size(max = 500)
            String observaciones,

            @Schema(description = "Que se despacha. Si viene vacio, se copia la carga "
                    + "completa del pedido de SALES-X.")
            @Valid
            List<LineaCarga> items) {
    }

    @Schema(description = "Producto y cantidad que van en la carga")
    public record LineaCarga(

            @Schema(description = "productoId registrado en INVENTA-X",
                    example = "aaaaaaaa-0001-4000-8000-000000000001")
            @NotNull(message = "El productoId es obligatorio")
            UUID productoId,

            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad minima es 1")
            @Max(value = 100000, message = "La cantidad maxima por linea es 100000")
            Integer cantidad,

            @Schema(description = "Peso unitario en kilogramos. Solo se usa si el "
                    + "producto no viene con peso desde el pedido.", example = "50.000")
            @DecimalMin(value = "0.000", message = "El peso no puede ser negativo")
            BigDecimal pesoUnitarioKg) {
    }

    @Schema(description = "Nueva cantidad de una linea de carga")
    public record CambiarCantidad(
            @NotNull(message = "La cantidad es obligatoria")
            @Min(value = 1, message = "La cantidad minima es 1")
            @Max(value = 100000, message = "La cantidad maxima por linea es 100000")
            Integer cantidad) {
    }

    @Schema(description = "Correccion del destino (solo antes de asignar transportador)")
    public record ActualizarDestino(
            @Size(max = 300) String direccionEntrega,
            @Size(max = 80) String ciudadEntrega,
            @Size(max = 80) String departamentoEntrega,
            @Size(max = 200) String referenciaDireccion,
            @Size(max = 40) String telefonoContacto,
            @Size(max = 500) String observaciones) {
    }

    @Schema(description = "Asignacion de transportador y ruta. Genera el numero de guia (RF-07).")
    public record AsignarTransportador(

            @NotNull(message = "El transportadorId es obligatorio")
            UUID transportadorId,

            @Schema(description = "Ruta de entrega. Si se omite, se busca una ruta activa "
                    + "hacia la ciudad de destino.")
            UUID rutaId,

            @Schema(description = "Fecha prometida al cliente. Si se omite, se calcula "
                    + "con los dias estimados de la ruta.", example = "2026-09-18")
            LocalDate fechaEstimadaEntrega) {
    }

    @Schema(description = "Salida a ruta")
    public record Despachar(
            @Schema(description = "Desde donde sale la carga", example = "Centro de distribucion Itagui")
            @Size(max = 160) String ubicacion) {
    }

    @Schema(description = "Evidencia de la entrega (RF-09)")
    public record ConfirmarEntrega(

            @NotEmpty(message = "Hay que registrar quien recibio la mercancia")
            @Size(max = 160)
            String receptorNombre,

            @Schema(description = "Documento de quien recibe", example = "1017234567")
            @Size(max = 40)
            String receptorDocumento,

            @Size(max = 500) String observacion,

            @Schema(description = "Donde se entrego", example = "Medellin")
            @Size(max = 160) String ubicacion) {
    }

    @Schema(description = "Novedad de transporte (RF-08)")
    public record RegistrarNovedad(

            @NotNull(message = "El tipo de novedad es obligatorio")
            TipoNovedad tipo,

            @NotEmpty(message = "La descripcion de la novedad es obligatoria")
            @Size(max = 500)
            String descripcion,

            @Size(max = 160) String ubicacion,

            @Schema(description = "Si la novedad impide la entrega. Si se omite, manda el "
                    + "valor por omision del tipo de novedad.")
            Boolean bloqueante) {
    }

    @Schema(description = "Cierre de una novedad")
    public record ResolverNovedad(
            @NotEmpty(message = "Escriba como se resolvio la novedad")
            @Size(max = 500)
            String resolucion) {
    }

    @Schema(description = "Nuevo intento de entrega")
    public record Reprogramar(
            @Schema(description = "Nueva fecha prometida", example = "2026-09-19")
            LocalDate fechaEstimadaEntrega) {
    }

    @Schema(description = "Motivo de una devolucion o cancelacion")
    public record MotivoSolicitud(
            @NotEmpty(message = "El motivo es obligatorio")
            @Size(max = 300)
            String motivo) {
    }

    @Schema(description = "Cambio de estado solicitado por otro microproyecto")
    public record CambiarEstado(

            @Schema(description = "Estado destino", example = "EN_RUTA")
            @NotNull(message = "El estado destino es obligatorio")
            String estado,

            @Size(max = 300) String motivo,

            @Size(max = 160) String ubicacion,

            @Schema(description = "Sistema que solicita el cambio", example = "SALES-X")
            @Size(max = 30) String origen) {
    }
}
