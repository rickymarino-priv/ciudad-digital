package ar.com.ciudaddigital.tenants.internal;

import java.util.List;
import java.util.Optional;

/**
 * Guarda los códigos de módulo habilitados del tenant resuelto, mientras
 * dura el request (ADR 0012 §2).
 *
 * <p>Es la forma en la que este módulo publica el entitlement del tenant
 * en curso hacia {@code entitlement}, sin exponerlo en {@link
 * ar.com.ciudaddigital.tenants.TenantInfo}: la configuración comercial no
 * es parte de la vista pública del tenant. Solo {@link
 * TenantResolutionFilter} escribe acá, y siempre limpia al terminar, por
 * el mismo motivo que {@link TenantHolder}: un hilo reutilizado que
 * arrastre los módulos del request anterior sería una fuga entre
 * municipios.
 */
final class TenantModulosHolder {

    private static final ThreadLocal<List<String>> ACTUAL = new ThreadLocal<>();

    private TenantModulosHolder() {
    }

    static Optional<List<String>> actual() {
        return Optional.ofNullable(ACTUAL.get());
    }

    static void establecer(List<String> modulos) {
        ACTUAL.set(modulos);
    }

    static void limpiar() {
        ACTUAL.remove();
    }
}
