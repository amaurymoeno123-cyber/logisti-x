package co.unisabaneta.integrax.logistix.despacho.repositorio;

import co.unisabaneta.integrax.logistix.despacho.dominio.TipoNovedad;

/**
 * Cuantas veces se reporto cada tipo de novedad y cuantas se resolvieron
 * (RF-08 + RF-11).
 *
 * <p>Es la razon por la que {@link TipoNovedad} es una lista cerrada: con
 * texto libre esta agrupacion no existiria.</p>
 */
public record CausaNoEntrega(
        TipoNovedad tipo,
        Long veces,
        Long resueltas) {
}
