package co.unisabaneta.integrax.logistix.catalogo.aplicacion;

import co.unisabaneta.integrax.logistix.auditoria.AuditoriaServicio;
import co.unisabaneta.integrax.logistix.catalogo.api.dto.CatalogoDtos;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Ruta;
import co.unisabaneta.integrax.logistix.catalogo.dominio.Transportador;
import co.unisabaneta.integrax.logistix.catalogo.repositorio.RutaRepositorio;
import co.unisabaneta.integrax.logistix.catalogo.repositorio.TransportadorRepositorio;
import co.unisabaneta.integrax.logistix.comun.excepciones.RecursoNoEncontradoException;
import co.unisabaneta.integrax.logistix.comun.excepciones.ReglaNegocioException;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoRepositorio;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Maestro logistico: transportadores (RF-05) y rutas de entrega.
 *
 * <p>Regla que atraviesa las dos entidades: <b>no se borran, se inactivan</b>.
 * Un transportador borrado dejaria despachos historicos apuntando al vacio y
 * la trazabilidad (RF-10) dejaria de poder responder quien llevo esa carga.</p>
 */
@Service
public class CatalogoServicio {

    private final TransportadorRepositorio transportadores;
    private final RutaRepositorio rutas;
    private final DespachoRepositorio despachos;
    private final AuditoriaServicio auditoria;

    public CatalogoServicio(TransportadorRepositorio transportadores,
                            RutaRepositorio rutas,
                            DespachoRepositorio despachos,
                            AuditoriaServicio auditoria) {
        this.transportadores = transportadores;
        this.rutas = rutas;
        this.despachos = despachos;
        this.auditoria = auditoria;
    }

    // ------------------------------------------------------------ transportador

    @Transactional
    public CatalogoDtos.TransportadorDto crearTransportador(CatalogoDtos.GuardarTransportador datos) {
        if (transportadores.existsByDocumento(datos.documento())) {
            throw new ReglaNegocioException("TRANSPORTADOR_DUPLICADO",
                    "Ya existe un transportador con el documento " + datos.documento() + ".");
        }
        Transportador transportador = transportadores.save(new Transportador(
                datos.documento(), datos.nombre(), datos.telefono(), datos.empresa(),
                datos.tipoVehiculo(), datos.placa(), datos.capacidadKg(), datos.zona()));

        auditoria.registrar("TRANSPORTADOR", transportador.getId(), AuditoriaServicio.CREAR,
                null, CatalogoDtos.TransportadorDto.de(transportador));
        return CatalogoDtos.TransportadorDto.de(transportador);
    }

    @Transactional
    public CatalogoDtos.TransportadorDto actualizarTransportador(
            UUID id, CatalogoDtos.GuardarTransportador datos) {
        Transportador transportador = buscarTransportador(id);
        var antes = CatalogoDtos.TransportadorDto.de(transportador);

        transportador.actualizar(datos.nombre(), datos.telefono(), datos.empresa(),
                datos.tipoVehiculo(), datos.placa(), datos.capacidadKg(), datos.zona());

        var despues = CatalogoDtos.TransportadorDto.de(transportador);
        auditoria.registrar("TRANSPORTADOR", id, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    /**
     * Inactiva un transportador. Se rechaza si tiene carga en la calle: dar de
     * baja a quien lleva un despacho encima deja esa entrega sin responsable.
     */
    @Transactional
    public CatalogoDtos.TransportadorDto inactivarTransportador(UUID id) {
        Transportador transportador = buscarTransportador(id);

        if (despachos.tieneCargaEnCalle(id)) {
            throw new ReglaNegocioException("TRANSPORTADOR_CON_CARGA",
                    "El transportador " + transportador.getNombre() + " tiene despachos en ruta. "
                            + "Cierre o reasigne esas entregas antes de inactivarlo.");
        }

        transportador.inactivar();
        auditoria.registrar("TRANSPORTADOR", id, AuditoriaServicio.ACTUALIZAR, null,
                CatalogoDtos.TransportadorDto.de(transportador));
        return CatalogoDtos.TransportadorDto.de(transportador);
    }

    @Transactional
    public CatalogoDtos.TransportadorDto activarTransportador(UUID id) {
        Transportador transportador = buscarTransportador(id);
        transportador.activar();
        return CatalogoDtos.TransportadorDto.de(transportador);
    }

    @Transactional(readOnly = true)
    public List<CatalogoDtos.TransportadorDto> listarTransportadores(boolean soloActivos, String zona) {
        List<Transportador> resultado;
        if (zona != null && !zona.isBlank()) {
            resultado = transportadores.findByActivoTrueAndZonaIgnoreCaseOrderByNombreAsc(zona.trim());
        } else if (soloActivos) {
            resultado = transportadores.findByActivoTrueOrderByNombreAsc();
        } else {
            resultado = transportadores.findAll();
        }
        return resultado.stream().map(CatalogoDtos.TransportadorDto::de).toList();
    }

    @Transactional(readOnly = true)
    public CatalogoDtos.TransportadorDto obtenerTransportador(UUID id) {
        return CatalogoDtos.TransportadorDto.de(buscarTransportador(id));
    }

    // -------------------------------------------------------------------- ruta

    @Transactional
    public CatalogoDtos.RutaDto crearRuta(CatalogoDtos.GuardarRuta datos) {
        if (rutas.existsByCodigoIgnoreCase(datos.codigo())) {
            throw new ReglaNegocioException("RUTA_DUPLICADA",
                    "Ya existe una ruta con el codigo " + datos.codigo() + ".");
        }
        Ruta ruta = rutas.save(new Ruta(datos.codigo(), datos.nombre(), datos.ciudadOrigen(),
                datos.ciudadDestino(), datos.zona(), datos.diasEstimados(), datos.tarifaBase()));

        auditoria.registrar("RUTA", ruta.getId(), AuditoriaServicio.CREAR,
                null, CatalogoDtos.RutaDto.de(ruta));
        return CatalogoDtos.RutaDto.de(ruta);
    }

    @Transactional
    public CatalogoDtos.RutaDto actualizarRuta(UUID id, CatalogoDtos.GuardarRuta datos) {
        Ruta ruta = buscarRuta(id);
        var antes = CatalogoDtos.RutaDto.de(ruta);

        ruta.actualizar(datos.nombre(), datos.ciudadOrigen(), datos.ciudadDestino(),
                datos.zona(), datos.diasEstimados(), datos.tarifaBase());

        var despues = CatalogoDtos.RutaDto.de(ruta);
        auditoria.registrar("RUTA", id, AuditoriaServicio.ACTUALIZAR, antes, despues);
        return despues;
    }

    @Transactional
    public CatalogoDtos.RutaDto inactivarRuta(UUID id) {
        Ruta ruta = buscarRuta(id);
        ruta.inactivar();
        auditoria.registrar("RUTA", id, AuditoriaServicio.ACTUALIZAR, null,
                CatalogoDtos.RutaDto.de(ruta));
        return CatalogoDtos.RutaDto.de(ruta);
    }

    @Transactional(readOnly = true)
    public List<CatalogoDtos.RutaDto> listarRutas(boolean soloActivas, String ciudadDestino) {
        List<Ruta> resultado;
        if (ciudadDestino != null && !ciudadDestino.isBlank()) {
            resultado = rutas.findByActivaTrueAndCiudadDestinoIgnoreCaseOrderByDiasEstimadosAsc(
                    ciudadDestino.trim());
        } else if (soloActivas) {
            resultado = rutas.findByActivaTrueOrderByCodigoAsc();
        } else {
            resultado = rutas.findAll();
        }
        return resultado.stream().map(CatalogoDtos.RutaDto::de).toList();
    }

    @Transactional(readOnly = true)
    public CatalogoDtos.RutaDto obtenerRuta(UUID id) {
        return CatalogoDtos.RutaDto.de(buscarRuta(id));
    }

    // ------------------------------------------------------------------ apoyo

    private Transportador buscarTransportador(UUID id) {
        return transportadores.findById(id)
                .orElseThrow(() -> RecursoNoEncontradoException.transportador(id));
    }

    private Ruta buscarRuta(UUID id) {
        return rutas.findById(id).orElseThrow(() -> RecursoNoEncontradoException.ruta(id));
    }
}
