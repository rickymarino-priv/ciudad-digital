package ar.com.ciudaddigital.tenants.internal;

import java.util.Optional;

import ar.com.ciudaddigital.tenants.TenantInfo;

/**
 * Guarda el municipio resuelto mientras dura el request.
 *
 * <p>Solo el filtro de resolución escribe acá, y siempre limpia al terminar:
 * los hilos se reutilizan entre requests y un tenant que sobrevive al suyo
 * es exactamente el tipo de fuga entre municipios que el producto no puede
 * permitirse.
 */
public final class TenantHolder {

    private static final ThreadLocal<TenantInfo> ACTUAL = new ThreadLocal<>();

    private TenantHolder() {
    }

    public static Optional<TenantInfo> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    public static void establecer(TenantInfo tenant) {
        ACTUAL.set(tenant);
    }

    public static void limpiar() {
        ACTUAL.remove();
    }
}
