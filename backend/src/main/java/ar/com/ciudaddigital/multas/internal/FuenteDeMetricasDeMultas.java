package ar.com.ciudaddigital.multas.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.multas.internal.MultaRepository.ConteoPorEtiqueta;
import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;

/**
 * Aporta al tablero de reportes el conteo de multas por estado (ADR 0034
 * §3): implementa la SPI {@code reportes.FuenteDeMetricas} en vez de que
 * {@code reportes} conozca este módulo, misma inversión de dependencia que
 * ya usa {@code FuenteDeMetricasDeReclamos} (ADR 0033 §3).
 */
@Component
class FuenteDeMetricasDeMultas implements FuenteDeMetricas {

    private final MultaRepository multas;

    FuenteDeMetricasDeMultas(MultaRepository multas) {
        this.multas = multas;
    }

    @Override
    public String moduloCodigo() {
        return DescriptorDelModuloMultas.CODIGO;
    }

    @Override
    public String moduloNombre() {
        return "Multas de tránsito";
    }

    @Override
    public List<SerieDeMetricas> series() {
        List<PuntoDeMetrica> puntos = multas.contarPorEstado().stream()
                .map(FuenteDeMetricasDeMultas::puntoDe)
                .toList();
        return List.of(new SerieDeMetricas("Multas por estado", puntos));
    }

    private static PuntoDeMetrica puntoDe(ConteoPorEtiqueta conteo) {
        return new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad());
    }
}
