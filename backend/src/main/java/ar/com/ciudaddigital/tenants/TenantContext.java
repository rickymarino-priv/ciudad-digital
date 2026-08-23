package ar.com.ciudaddigital.tenants;

import java.util.Optional;

import ar.com.ciudaddigital.tenants.internal.TenantHolder;

/**
 * Acceso de solo lectura al municipio del request en curso.
 *
 * <p>Es el punto de entrada que usan los demás módulos para saber sobre qué
 * tenant están trabajando. Quién lo establece y cuándo se limpia es asunto
 * interno del módulo de tenants: desde afuera solo se lee.
 */
public final class TenantContext {

    private TenantContext() {
    }

    /**
     * Municipio del request en curso, vacío fuera de un request con tenant
     * resuelto (por ejemplo, en tareas de fondo).
     */
    public static Optional<TenantInfo> actual() {
        return TenantHolder.actual();
    }

    /**
     * Municipio del request en curso, asumiendo que hay uno.
     *
     * @throws IllegalStateException si no hay tenant resuelto, lo que indica
     *         un uso fuera del alcance de un request de portal municipal.
     */
    public static TenantInfo requerido() {
        return actual().orElseThrow(() -> new IllegalStateException(
                "No hay tenant resuelto para el request en curso"));
    }
}
