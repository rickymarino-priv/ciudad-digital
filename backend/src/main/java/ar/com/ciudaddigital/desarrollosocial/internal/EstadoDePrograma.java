package ar.com.ciudaddigital.desarrollosocial.internal;

/**
 * Ciclo de vida de un programa social (ADR 0025 §3): un municipio abre y
 * cierra una convocatoria, sin progresión unidireccional que modelar —
 * ambas transiciones son válidas en los dos sentidos.
 */
enum EstadoDePrograma {
    ABIERTO,
    CERRADO
}
