package ar.com.ciudaddigital.bromatologia.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;

/**
 * Aporta al tablero de reportes el conteo de comercios bromatológicos por
 * estado sanitario y de inspecciones por resultado (ADR 0034 §3): implementa
 * la SPI {@code reportes.FuenteDeMetricas} en vez de que {@code reportes}
 * conozca este módulo, misma inversión de dependencia que ya usa
 * {@code FuenteDeMetricasDeReclamos} (ADR 0033 §3).
 */
@Component
class FuenteDeMetricasDeBromatologia implements FuenteDeMetricas {

    private final ComercioBromatologicoRepository comercios;
    private final InspeccionBromatologicaRepository inspecciones;

    FuenteDeMetricasDeBromatologia(
            ComercioBromatologicoRepository comercios, InspeccionBromatologicaRepository inspecciones) {

        this.comercios = comercios;
        this.inspecciones = inspecciones;
    }

    @Override
    public String moduloCodigo() {
        return DescriptorDelModuloBromatologia.CODIGO;
    }

    @Override
    public String moduloNombre() {
        return "Bromatología";
    }

    @Override
    public List<SerieDeMetricas> series() {
        List<PuntoDeMetrica> puntosPorEstado = comercios.contarPorEstado().stream()
                .map(conteo -> new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad()))
                .toList();
        List<PuntoDeMetrica> puntosPorResultado = inspecciones.contarPorResultado().stream()
                .map(conteo -> new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad()))
                .toList();

        return List.of(
                new SerieDeMetricas("Comercios bromatológicos por estado sanitario", puntosPorEstado),
                new SerieDeMetricas("Inspecciones por resultado", puntosPorResultado));
    }
}
