package ar.com.ciudaddigital.reportes;

import java.util.List;

/** Una serie de indicadores dentro de una {@link FuenteDeMetricas} (ADR 0033 §2). */
public record SerieDeMetricas(String nombre, List<PuntoDeMetrica> puntos) {
}
