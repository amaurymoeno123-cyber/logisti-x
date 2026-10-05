package co.unisabaneta.integrax.logistix.catalogo.api;

import co.unisabaneta.integrax.logistix.catalogo.api.dto.CatalogoDtos;
import co.unisabaneta.integrax.logistix.catalogo.aplicacion.CatalogoServicio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

/**
 * Maestro logistico: transportadores (RF-05) y rutas de entrega.
 *
 * <p>Ninguno de los dos se borra: se inactivan. Por eso no hay {@code DELETE}
 * que destruya, sino uno que da de baja — la ruta HTTP es la misma que espera
 * quien consume la API, pero el efecto conserva la historia.</p>
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Maestro logistico", description = "Transportadores y rutas de entrega")
@SecurityRequirement(name = "bearer-jwt")
public class CatalogoControlador {

    private final CatalogoServicio servicio;

    public CatalogoControlador(CatalogoServicio servicio) {
        this.servicio = servicio;
    }

    // ------------------------------------------------------------ transportador

    @Operation(summary = "Listar transportadores (RF-05)")
    @GetMapping("/transportadores")
    public List<CatalogoDtos.TransportadorDto> transportadores(
            @RequestParam(defaultValue = "true") boolean soloActivos,
            @RequestParam(required = false) String zona) {
        return servicio.listarTransportadores(soloActivos, zona);
    }

    @Operation(summary = "Consultar un transportador")
    @GetMapping("/transportadores/{id}")
    public CatalogoDtos.TransportadorDto transportador(@PathVariable UUID id) {
        return servicio.obtenerTransportador(id);
    }

    @Operation(summary = "Registrar un transportador")
    @PostMapping("/transportadores")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public ResponseEntity<CatalogoDtos.TransportadorDto> crearTransportador(
            @Valid @RequestBody CatalogoDtos.GuardarTransportador datos,
            UriComponentsBuilder uri) {
        var creado = servicio.crearTransportador(datos);
        return ResponseEntity
                .created(uri.path("/api/v1/transportadores/{id}").build(creado.id()))
                .body(creado);
    }

    @Operation(summary = "Actualizar un transportador")
    @PutMapping("/transportadores/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public CatalogoDtos.TransportadorDto actualizarTransportador(
            @PathVariable UUID id,
            @Valid @RequestBody CatalogoDtos.GuardarTransportador datos) {
        return servicio.actualizarTransportador(id, datos);
    }

    @Operation(summary = "Dar de baja a un transportador",
            description = "No lo borra: lo inactiva, para no romper la trazabilidad de los "
                    + "despachos que ya llevo. Se rechaza si tiene carga en la calle.")
    @DeleteMapping("/transportadores/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogoDtos.TransportadorDto inactivarTransportador(@PathVariable UUID id) {
        return servicio.inactivarTransportador(id);
    }

    @Operation(summary = "Reactivar un transportador")
    @PostMapping("/transportadores/{id}/activar")
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogoDtos.TransportadorDto activarTransportador(@PathVariable UUID id) {
        return servicio.activarTransportador(id);
    }

    // -------------------------------------------------------------------- ruta

    @Operation(summary = "Listar rutas de entrega")
    @GetMapping("/rutas")
    public List<CatalogoDtos.RutaDto> rutas(
            @RequestParam(defaultValue = "true") boolean soloActivas,
            @RequestParam(required = false) String ciudadDestino) {
        return servicio.listarRutas(soloActivas, ciudadDestino);
    }

    @Operation(summary = "Consultar una ruta")
    @GetMapping("/rutas/{id}")
    public CatalogoDtos.RutaDto ruta(@PathVariable UUID id) {
        return servicio.obtenerRuta(id);
    }

    @Operation(summary = "Crear una ruta de entrega")
    @PostMapping("/rutas")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public ResponseEntity<CatalogoDtos.RutaDto> crearRuta(
            @Valid @RequestBody CatalogoDtos.GuardarRuta datos,
            UriComponentsBuilder uri) {
        var creada = servicio.crearRuta(datos);
        return ResponseEntity
                .created(uri.path("/api/v1/rutas/{id}").build(creada.id()))
                .body(creada);
    }

    @Operation(summary = "Actualizar una ruta")
    @PutMapping("/rutas/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','COORDINADOR')")
    public CatalogoDtos.RutaDto actualizarRuta(@PathVariable UUID id,
                                               @Valid @RequestBody CatalogoDtos.GuardarRuta datos) {
        return servicio.actualizarRuta(id, datos);
    }

    @Operation(summary = "Dar de baja una ruta")
    @DeleteMapping("/rutas/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public CatalogoDtos.RutaDto inactivarRuta(@PathVariable UUID id) {
        return servicio.inactivarRuta(id);
    }
}
