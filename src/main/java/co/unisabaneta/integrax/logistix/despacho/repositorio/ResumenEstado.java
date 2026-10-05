package co.unisabaneta.integrax.logistix.despacho.repositorio;

import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;

import java.math.BigDecimal;

/**
 * Fila del reporte logistico agrupada por estado (RF-11).
 *
 * <p>La consulta la construye PostgreSQL y la proyecta directo sobre este
 * record: no se traen entidades a memoria para sumarlas en Java.</p>
 */
public record ResumenEstado(
        EstadoDespacho estado,
        Long cantidad,
        BigDecimal pesoKg,
        BigDecimal valorDeclarado,
        BigDecimal flete) {
}
