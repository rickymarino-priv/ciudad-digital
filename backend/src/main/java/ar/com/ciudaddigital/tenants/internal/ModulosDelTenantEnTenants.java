package ar.com.ciudaddigital.tenants.internal;

import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.ModulosDelTenant;

/**
 * Implementación de la SPI de entitlement con lo que este módulo ya sabe
 * del tenant resuelto (ADR 0012 §2): los módulos que
 * {@link TenantResolutionFilter} dejó en {@link TenantModulosHolder} para
 * el request en curso.
 */
@Component
class ModulosDelTenantEnTenants implements ModulosDelTenant {

    @Override
    public Optional<Set<String>> habilitadosDelRequestEnCurso() {
        return TenantModulosHolder.actual().map(Set::copyOf);
    }
}
