package ar.com.ciudaddigital.reclamos.internal;

/**
 * Ciclo de vida fijo del reclamo, igual para todos los municipios
 * (ADR 0014 §3): no es una entidad de un motor de workflow configurable.
 */
enum EstadoReclamo {
    NUEVO,
    EN_PROCESO,
    RESUELTO,
    RECHAZADO
}
