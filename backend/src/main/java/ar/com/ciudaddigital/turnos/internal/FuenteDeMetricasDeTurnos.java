package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;
import ar.com.ciudaddigital.turnos.internal.TurnoRepository.ConteoPorEtiqueta;

/**
 * Aporta al tablero de reportes el conteo de turnos reservados por actividad
 * (ADR 0034 §3): implementa la SPI {@code reportes.FuenteDeMetricas} en vez
 * de que {@code reportes} conozca este módulo, misma inversión de
 * dependencia que ya usa {@code FuenteDeMetricasDeReclamos} (ADR 0033 §3).
 */
@Component
class FuenteDeMetricasDeTurnos implements FuenteDeMetricas {

    private final TurnoRepository turnos;

    FuenteDeMetricasDeTurnos(TurnoRepository turnos) {
        this.turnos = turnos;
    }

    @Override
    public String moduloCodigo() {
        return DescriptorDelModuloTurnos.CODIGO;
    }

    @Override
    public String moduloNombre() {
        return "Turnos y actividades municipales";
    }

    @Override
    public List<SerieDeMetricas> series() {
        List<PuntoDeMetrica> puntos = turnos.contarPorActividad().stream()
                .map(FuenteDeMetricasDeTurnos::puntoDe)
                .toList();
        return List.of(new SerieDeMetricas("Turnos reservados por actividad", puntos));
    }

    private static PuntoDeMetrica puntoDe(ConteoPorEtiqueta conteo) {
        return new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad());
    }
}
