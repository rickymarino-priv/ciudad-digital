package ar.com.ciudaddigital.reportes.internal;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ar.com.ciudaddigital.entitlement.ModulosDelTenant;
import ar.com.ciudaddigital.reportes.FuenteDeMetricas;
import ar.com.ciudaddigital.reportes.PuntoDeMetrica;
import ar.com.ciudaddigital.reportes.SerieDeMetricas;

/**
 * Tablero de indicadores agregados de los módulos operativos del municipio
 * del request en curso (ADR 0033).
 *
 * <p>No hay {@code DescriptorDeModulo} para {@code reportes}: es canon base,
 * no un módulo contratable (ADR 0033 §1), así que este endpoint no pasa por
 * el gating de entitlement, solo por el permiso {@code reportes.ver}.
 *
 * <p>Recolecta todos los beans {@link FuenteDeMetricas} del contexto de
 * Spring (resueltos por tipo, sin que este controller conozca ningún módulo
 * funcional en particular) y los filtra contra
 * {@link ModulosDelTenant#habilitadosDelRequestEnCurso()}: mostrar el
 * indicador de un módulo que el municipio no contrató sería inconsistente
 * con el resto del sistema, aunque no es una fuga de datos (ADR 0033 §4).
 */
@RestController
@RequestMapping("/api/reportes")
class ReportesController {

    private final List<FuenteDeMetricas> fuentes;
    private final ModulosDelTenant modulosDelTenant;

    ReportesController(List<FuenteDeMetricas> fuentes, ModulosDelTenant modulosDelTenant) {
        this.fuentes = fuentes;
        this.modulosDelTenant = modulosDelTenant;
    }

    @GetMapping("/tablero")
    @PreAuthorize("hasAuthority('reportes.ver')")
    List<FuenteDeMetricasResponse> tablero() {
        Optional<Set<String>> habilitados = modulosDelTenant.habilitadosDelRequestEnCurso();
        if (habilitados.isEmpty()) {
            // No se pudo determinar el tenant del request: fail-closed hacia
            // "no mostrar nada", mismo espíritu que ADR 0012 §3 aplicado acá
            // a un endpoint de lectura informativa, no de gating de
            // escritura (ADR 0033 §4).
            return List.of();
        }

        Set<String> modulosHabilitados = habilitados.get();
        return fuentes.stream()
                .filter(fuente -> modulosHabilitados.contains(fuente.moduloCodigo()))
                .sorted(Comparator.comparing(FuenteDeMetricas::moduloNombre))
                .map(FuenteDeMetricasResponse::de)
                .toList();
    }

    record FuenteDeMetricasResponse(String moduloCodigo, String moduloNombre, List<SerieResponse> series) {

        static FuenteDeMetricasResponse de(FuenteDeMetricas fuente) {
            return new FuenteDeMetricasResponse(
                    fuente.moduloCodigo(), fuente.moduloNombre(),
                    fuente.series().stream().map(SerieResponse::de).toList());
        }
    }

    record SerieResponse(String nombre, List<PuntoResponse> puntos) {

        static SerieResponse de(SerieDeMetricas serie) {
            return new SerieResponse(serie.nombre(), serie.puntos().stream().map(PuntoResponse::de).toList());
        }
    }

    record PuntoResponse(String etiqueta, long cantidad) {

        static PuntoResponse de(PuntoDeMetrica punto) {
            return new PuntoResponse(punto.etiqueta(), punto.cantidad());
        }
    }
}
