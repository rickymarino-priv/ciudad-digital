package ar.com.ciudaddigital.turnos.internal;

/**
 * Ciclo de vida de una actividad municipal (ADR 0026 §2): un municipio la
 * activa e inactiva libremente, sin progresión unidireccional que modelar
 * — ambas transiciones son válidas en los dos sentidos, mismo criterio que
 * {@code EstadoDePrograma} en Desarrollo Social (ADR 0025 §3). Una
 * actividad {@code INACTIVA} sigue visible en el catálogo público pero no
 * admite nuevas reservas en ninguna de sus franjas (ADR 0026 §2).
 */
enum EstadoDeActividad {
    ACTIVA,
    INACTIVA
}
