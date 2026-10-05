package co.unisabaneta.integrax.logistix.despacho.aplicacion;

import co.unisabaneta.integrax.logistix.comun.Dinero;
import co.unisabaneta.integrax.logistix.comun.Peso;
import co.unisabaneta.integrax.logistix.despacho.dominio.EstadoDespacho;
import co.unisabaneta.integrax.logistix.despacho.repositorio.CausaNoEntrega;
import co.unisabaneta.integrax.logistix.despacho.repositorio.Cumplimiento;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DesempenoTransportador;
import co.unisabaneta.integrax.logistix.despacho.repositorio.DespachoRepositorio;
import co.unisabaneta.integrax.logistix.despacho.repositorio.ResumenEstado;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Reportes logisticos (RF-11).
 *
 * <p>Todo se agrega en PostgreSQL y aqui solo se arma la respuesta. Traer los
 * despachos a memoria para contarlos funcionaria con los veinte de la
 * demostracion y se caeria con los de un mes real: es exactamente el error que
 * el RNF-04 (respuesta menor a 3 segundos) obliga a no cometer.</p>
 *
 * <p>Los cuatro cortes del reporte responden preguntas distintas y por eso
 * estan los cuatro: <i>en que va la operacion</i> (por estado),
 * <i>quien responde</i> (por transportador), <i>por que se cae</i> (causas de
 * no entrega) y <i>si estamos cumpliendo</i> (promesa de entrega).</p>
 */
@Service
public class ReporteServicio {

    private static final ZoneId ZONA = ZoneId.of("America/Bogota");

    private final DespachoRepositorio repositorio;

    public ReporteServicio(DespachoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Transactional(readOnly = true)
    public ReporteLogistico generar(LocalDate desde, LocalDate hasta) {
        LocalDate inicio = desde == null ? LocalDate.now().withDayOfMonth(1) : desde;
        LocalDate fin = hasta == null ? LocalDate.now() : hasta;

        OffsetDateTime desdeHora = inicio.atStartOfDay(ZONA).toOffsetDateTime();
        // El limite superior es exclusivo y por eso se suma un dia: si no, los
        // despachos de la ultima tarde del rango se quedan por fuera del
        // reporte y nadie entiende por que no cuadra con el tablero.
        OffsetDateTime hastaHora = fin.plusDays(1).atStartOfDay(ZONA).toOffsetDateTime();

        List<ResumenEstado> porEstado = repositorio.resumirPorEstado(desdeHora, hastaHora);
        List<DesempenoTransportador> porTransportador =
                repositorio.desempenoPorTransportador(desdeHora, hastaHora);
        List<CausaNoEntrega> causas = repositorio.causasDeNoEntrega(desdeHora, hastaHora);
        Cumplimiento cumplimiento = repositorio.cumplimiento(desdeHora, hastaHora);

        long total = porEstado.stream().mapToLong(ResumenEstado::cantidad).sum();
        long entregados = porEstado.stream()
                .filter(r -> r.estado() == EstadoDespacho.ENTREGADO)
                .mapToLong(ResumenEstado::cantidad).sum();
        long enCalle = porEstado.stream()
                .filter(r -> r.estado() == EstadoDespacho.EN_RUTA
                          || r.estado() == EstadoDespacho.FALLIDO)
                .mapToLong(ResumenEstado::cantidad).sum();

        BigDecimal pesoTotal = porEstado.stream()
                .map(ResumenEstado::pesoKg)
                .reduce(Peso.CERO, Peso::sumar);
        BigDecimal fleteTotal = porEstado.stream()
                .map(ResumenEstado::flete)
                .reduce(Dinero.CERO, Dinero::sumar);

        return new ReporteLogistico(inicio, fin, total, entregados, enCalle,
                pesoTotal, fleteTotal,
                cumplimiento == null ? BigDecimal.ZERO : cumplimiento.porcentaje(),
                porEstado,
                porTransportador.stream().map(FilaTransportador::de).toList(),
                causas);
    }

    @Schema(description = "Reporte logistico del periodo (RF-11)")
    public record ReporteLogistico(
            LocalDate desde,
            LocalDate hasta,
            long totalDespachos,
            long entregados,
            @Schema(description = "En ruta o con entrega fallida: la carga que esta afuera")
            long enCalle,
            BigDecimal pesoTotalKg,
            BigDecimal fleteTotal,
            @Schema(description = "Porcentaje de entregas dentro de la fecha prometida")
            BigDecimal cumplimiento,
            List<ResumenEstado> porEstado,
            List<FilaTransportador> porTransportador,
            List<CausaNoEntrega> causasDeNoEntrega) {
    }

    @Schema(description = "Desempeno de un transportador, con su efectividad calculada")
    public record FilaTransportador(
            java.util.UUID transportadorId,
            String nombre,
            String empresa,
            Long despachos,
            Long entregados,
            Long fallidos,
            BigDecimal pesoKg,
            BigDecimal efectividad) {

        static FilaTransportador de(DesempenoTransportador d) {
            return new FilaTransportador(d.transportadorId(), d.nombre(), d.empresa(),
                    d.despachos(), d.entregados(), d.fallidos(), d.pesoKg(), d.efectividad());
        }
    }
}
