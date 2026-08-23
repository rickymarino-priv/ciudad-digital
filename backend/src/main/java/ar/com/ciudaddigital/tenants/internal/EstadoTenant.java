package ar.com.ciudaddigital.tenants.internal;

/**
 * Ciclo de vida del alta de un municipio (ADR 0005).
 *
 * <p>En R1 los tenants se siembran ya {@link #ACTIVO}; los estados de
 * aprovisionamiento se usan de verdad en R2.
 */
public enum EstadoTenant {

    PENDIENTE,
    APROVISIONANDO,
    ACTIVO,
    SUSPENDIDO,
    ERROR;

    /** Solo un tenant activo puede atender requests de su portal. */
    public boolean puedeAtender() {
        return this == ACTIVO;
    }
}
