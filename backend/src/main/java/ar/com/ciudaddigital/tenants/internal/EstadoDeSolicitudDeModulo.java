package ar.com.ciudaddigital.tenants.internal;

/**
 * Estado de una {@link SolicitudDeModuloEntity}: nace {@code PENDIENTE} y la
 * plataforma la marca {@code ATENDIDA} después de resolverla por fuera, sin
 * que eso module el entitlement (ADR 0022 §3).
 */
enum EstadoDeSolicitudDeModulo {
    PENDIENTE,
    ATENDIDA
}
