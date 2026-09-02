package ar.com.ciudaddigital.reclamos.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.reclamos.internal.ReclamoRepository.ConteoPorEtiqueta;
import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;

/**
 * Aporta al tablero de reportes el conteo de reclamos por estado (ADR 0033
 * §3): implementa la SPI {@code reportes.FuenteDeMetricas} en vez de que
 * {@code reportes} conozca este módulo, misma inversión de dependencia que
 * ya usa {@code entitlement.DescriptorDeModulo} (ADR 0012 §2).
 */
@Component
class FuenteDeMetricasDeReclamos implements FuenteDeMetricas {

    private final ReclamoRepository reclamos;

    FuenteDeMetricasDeReclamos(ReclamoRepository reclamos) {
        this.reclamos = reclamos;
    }

    @Override
    public String moduloCodigo() {
        return DescriptorDelModuloReclamos.CODIGO;
    }

    @Override
    public String moduloNombre() {
        return "Reclamos ciudadanos";
    }

    @Override
    public List<SerieDeMetricas> series() {
        List<PuntoDeMetrica> puntos = reclamos.contarPorEstado().stream()
                .map(FuenteDeMetricasDeReclamos::puntoDe)
                .toList();
        return List.of(new SerieDeMetricas("Reclamos por estado", puntos));
    }

    private static PuntoDeMetrica puntoDe(ConteoPorEtiqueta conteo) {
        return new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad());
    }
}
