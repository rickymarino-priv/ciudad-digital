package ar.com.ciudaddigital.reportes;

/**
 * Un valor agregado de una {@link SerieDeMetricas}: cuántos registros caen
 * bajo {@code etiqueta} (ADR 0033 §2). Una etiqueta sin ningún dato no
 * genera un punto —no se rellena con cero—: es el agregado real de lo que
 * hay, no un catálogo fijo pre-poblado (ADR 0033 §3).
 */
public record PuntoDeMetrica(String etiqueta, long cantidad) {
}
