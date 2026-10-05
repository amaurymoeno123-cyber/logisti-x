package co.unisabaneta.integrax.logistix.despacho.repositorio;

import co.unisabaneta.integrax.logistix.despacho.dominio.Despacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Filtros combinables para la busqueda de despachos (RF-03).
 *
 * <p>Cada filtro es una {@link Specification} independiente que devuelve
 * {@code null} cuando el parametro no viene; Spring Data ignora los nulos al
 * encadenarlas con {@code and}. Resultado: un solo metodo de busqueda soporta
 * cualquier combinacion de filtros sin un solo {@code if} armando SQL a mano
 * y sin superficie de inyeccion SQL, porque los valores viajan como
 * parametros preparados.</p>
 */
public final class DespachoEspecificaciones {

    private DespachoEspecificaciones() {
    }

    public static Specification<Despacho> conEstado(EstadoDespacho estado) {
        return estado == null ? null
                : (raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado);
    }

    public static Specification<Despacho> conPedido(UUID pedidoId) {
        return pedidoId == null ? null
                : (raiz, consulta, cb) -> cb.equal(raiz.get("pedidoId"), pedidoId);
    }

    public static Specification<Despacho> conCliente(UUID clienteId) {
        return clienteId == null ? null
                : (raiz, consulta, cb) -> cb.equal(raiz.get("clienteId"), clienteId);
    }

    public static Specification<Despacho> conTransportador(UUID transportadorId) {
        return transportadorId == null ? null
                : (raiz, consulta, cb) -> cb.equal(raiz.get("transportador").get("id"), transportadorId);
    }

    public static Specification<Despacho> enCiudad(String ciudad) {
        if (ciudad == null || ciudad.isBlank()) {
            return null;
        }
        String valor = ciudad.trim().toLowerCase();
        return (raiz, consulta, cb) -> cb.equal(cb.lower(raiz.get("ciudadEntrega")), valor);
    }

    public static Specification<Despacho> creadoDesde(OffsetDateTime desde) {
        return desde == null ? null
                : (raiz, consulta, cb) -> cb.greaterThanOrEqualTo(raiz.get("creadoEn"), desde);
    }

    public static Specification<Despacho> creadoHasta(OffsetDateTime hasta) {
        return hasta == null ? null
                : (raiz, consulta, cb) -> cb.lessThan(raiz.get("creadoEn"), hasta);
    }

    /** Busqueda libre por numero de despacho, guia o nombre del cliente. */
    public static Specification<Despacho> conTexto(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String patron = "%" + texto.trim().toLowerCase() + "%";
        return (raiz, consulta, cb) -> cb.or(
                cb.like(cb.lower(raiz.get("numero")), patron),
                cb.like(cb.lower(cb.coalesce(raiz.get("numeroGuia"), "")), patron),
                cb.like(cb.lower(raiz.get("clienteNombre")), patron),
                cb.like(cb.lower(cb.coalesce(raiz.get("pedidoNumero"), "")), patron),
                cb.like(cb.lower(raiz.get("direccionEntrega")), patron));
    }
}
