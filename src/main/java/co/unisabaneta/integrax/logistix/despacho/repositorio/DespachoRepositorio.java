package co.unisabaneta.integrax.logistix.despacho.repositorio;

import co.unisabaneta.integrax.logistix.despacho.dominio.Despacho;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Acceso a despachos.
 *
 * <p>Las busquedas con filtros combinados (RF-03) se resuelven con
 * {@link JpaSpecificationExecutor} y {@link DespachoEspecificaciones}, no
 * concatenando SQL: asi no hay forma de inyectar SQL por los parametros de la
 * consulta.</p>
 */
@Repository
public interface DespachoRepositorio
        extends JpaRepository<Despacho, UUID>, JpaSpecificationExecutor<Despacho> {

    Optional<Despacho> findByNumero(String numero);

    /** Consulta por numero de guia: es como pregunta el cliente (RF-03, RF-10). */
    Optional<Despacho> findByNumeroGuia(String numeroGuia);

    /**
     * Carga el despacho con su carga en una sola consulta.
     * Sin esto, mostrar 20 despachos dispara 21 consultas (problema N+1).
     */
    @Query("""
            select distinct d from Despacho d
            left join fetch d.items
            where d.despachoId = :id
            """)
    Optional<Despacho> buscarConItems(@Param("id") UUID id);

    @Query("""
            select distinct d from Despacho d
            left join fetch d.historial
            where d.despachoId = :id
            """)
    Optional<Despacho> buscarConHistorial(@Param("id") UUID id);

    @Query("""
            select distinct d from Despacho d
            left join fetch d.novedades
            where d.despachoId = :id
            """)
    Optional<Despacho> buscarConNovedades(@Param("id") UUID id);

    /** Todos los despachos de un pedido de SALES-X (RF-02, RF-03). */
    List<Despacho> findByPedidoIdOrderByCreadoEnDesc(UUID pedidoId);

    /**
     * RN-08: un pedido no puede tener dos despachos vivos. Se comprueba aqui
     * para dar un mensaje claro; la garantia dura la pone el indice unico
     * parcial {@code uk_despacho_pedido_activo}.
     */
    @Query("""
            select d from Despacho d
            where d.pedidoId = :pedidoId
              and d.estado not in (
                  co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.ENTREGADO,
                  co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.DEVUELTO,
                  co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.CANCELADO)
            """)
    Optional<Despacho> buscarActivoDelPedido(@Param("pedidoId") UUID pedidoId);

    /** Historial de entregas de un cliente (RF-06). */
    Page<Despacho> findByClienteIdOrderByCreadoEnDesc(UUID clienteId, Pageable pageable);

    /** Siguiente consecutivo interno. Lo entrega la secuencia de PostgreSQL. */
    @Query(value = "select nextval('logistix.seq_despacho_numero')", nativeQuery = true)
    Long siguienteNumeroSecuencia();

    /** Consecutivo del numero de guia (RF-07). */
    @Query(value = "select nextval('logistix.seq_guia_consecutivo')", nativeQuery = true)
    Long siguienteGuiaSecuencia();

    /**
     * Resumen por estado para el reporte logistico (RF-11). Se agrega en la
     * base de datos, no en memoria: traer 50.000 despachos a Java para
     * contarlos es la forma segura de incumplir el RNF-04 (respuesta &lt; 3 s).
     */
    @Query("""
            select new co.unisabaneta.integrax.logistix.despacho.repositorio.ResumenEstado(
                d.estado, count(d), coalesce(sum(d.pesoTotalKg), 0),
                coalesce(sum(d.valorDeclarado), 0), coalesce(sum(d.costoFlete), 0))
            from Despacho d
            where d.creadoEn >= :desde and d.creadoEn < :hasta
            group by d.estado
            """)
    List<ResumenEstado> resumirPorEstado(@Param("desde") OffsetDateTime desde,
                                         @Param("hasta") OffsetDateTime hasta);

    /** Desempeno por transportador en el rango (RF-11). */
    @Query("""
            select new co.unisabaneta.integrax.logistix.despacho.repositorio.DesempenoTransportador(
                t.id, t.nombre, t.empresa,
                count(d),
                sum(case when d.estado = co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.ENTREGADO
                         then 1 else 0 end),
                sum(case when d.estado in (
                             co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.FALLIDO,
                             co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.DEVUELTO)
                         then 1 else 0 end),
                coalesce(sum(d.pesoTotalKg), 0))
            from Despacho d join d.transportador t
            where d.creadoEn >= :desde and d.creadoEn < :hasta
            group by t.id, t.nombre, t.empresa
            order by count(d) desc
            """)
    List<DesempenoTransportador> desempenoPorTransportador(@Param("desde") OffsetDateTime desde,
                                                           @Param("hasta") OffsetDateTime hasta);

    /** Causas de no entrega, agrupadas por tipo de novedad (RF-08 + RF-11). */
    @Query("""
            select new co.unisabaneta.integrax.logistix.despacho.repositorio.CausaNoEntrega(
                n.tipo, count(n),
                sum(case when n.resuelta = true then 1 else 0 end))
            from Novedad n
            where n.ocurridaEn >= :desde and n.ocurridaEn < :hasta
            group by n.tipo
            order by count(n) desc
            """)
    List<CausaNoEntrega> causasDeNoEntrega(@Param("desde") OffsetDateTime desde,
                                           @Param("hasta") OffsetDateTime hasta);

    /**
     * Indicador de cumplimiento: entregas hechas dentro de la fecha prometida
     * y horas promedio en ruta.
     */
    @Query("""
            select new co.unisabaneta.integrax.logistix.despacho.repositorio.Cumplimiento(
                count(d),
                sum(case when d.entregadoEn is not null and d.fechaEstimadaEntrega is not null
                          and d.fechaEstimadaEntrega >= cast(d.entregadoEn as date)
                         then 1 else 0 end))
            from Despacho d
            where d.estado = co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.ENTREGADO
              and d.entregadoEn >= :desde and d.entregadoEn < :hasta
            """)
    Cumplimiento cumplimiento(@Param("desde") OffsetDateTime desde,
                              @Param("hasta") OffsetDateTime hasta);

    boolean existsByPedidoId(UUID pedidoId);

    /**
     * Si el transportador tiene carga encima ahora mismo. Se pregunta con una
     * consulta y no trayendo todos los despachos a memoria: es la diferencia
     * entre una comprobacion instantanea y una que se degrada con el tiempo.
     */
    @Query("""
            select count(d) > 0 from Despacho d
            where d.transportador.id = :transportadorId
              and d.estado in (
                  co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.EN_RUTA,
                  co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho.FALLIDO)
            """)
    boolean tieneCargaEnCalle(@Param("transportadorId") UUID transportadorId);

    long countByEstado(EstadoDespacho estado);
}
