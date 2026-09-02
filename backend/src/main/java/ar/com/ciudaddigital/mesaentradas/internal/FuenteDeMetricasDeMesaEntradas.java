package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.mesaentradas.internal.ExpedienteRepository.ConteoPorEtiqueta;
import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;

/**
 * Aporta al tablero de reportes el conteo de expedientes por tipo de
 * trámite y por estado (ADR 0033 §3): implementa la SPI
 * {@code reportes.FuenteDeMetricas} en vez de que {@code reportes} conozca
 * este módulo, misma inversión de dependencia que ya usa
 * {@code entitlement.DescriptorDeModulo} (ADR 0012 §2).
 */
@Component
class FuenteDeMetricasDeMesaEntradas implements FuenteDeMetricas {

    private final ExpedienteRepository expedientes;

    FuenteDeMetricasDeMesaEntradas(ExpedienteRepository expedientes) {
        this.expedientes = expedientes;
    }

    @Override
    public String moduloCodigo() {
        return DescriptorDelModuloMesaDeEntradas.CODIGO;
    }

    @Override
    public String moduloNombre() {
        return "Mesa de Entradas";
    }

    @Override
    public List<SerieDeMetricas> series() {
        return List.of(
                new SerieDeMetricas("Expedientes por tipo de trámite", puntosDe(expedientes.contarPorTipo())),
                new SerieDeMetricas("Expedientes por estado", puntosDe(expedientes.contarPorEstado())));
    }

    private static List<PuntoDeMetrica> puntosDe(List<ConteoPorEtiqueta> conteos) {
        return conteos.stream()
                .map(conteo -> new PuntoDeMetrica(conteo.getEtiqueta(), conteo.getCantidad()))
                .toList();
    }
}
