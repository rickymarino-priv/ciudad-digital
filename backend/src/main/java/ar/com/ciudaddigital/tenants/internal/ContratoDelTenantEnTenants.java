package ar.com.ciudaddigital.tenants.internal;

import java.util.Optional;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.tenants.ContratoDelTenant;
import ar.com.ciudaddigital.tenants.TenantContext;

/**
 * Implementación de la SPI de contrato con lo que este módulo ya tiene del
 * tenant resuelto (ADR 0022 §1): busca la fila de {@code tenant} del
 * municipio del request en curso y expone tramo poblacional y estado de
 * facturación, sin la nota interna.
 */
@Component
class ContratoDelTenantEnTenants implements ContratoDelTenant {

    private final TenantRepository repositorio;

    ContratoDelTenantEnTenants(TenantRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public Optional<Contrato> actual() {
        return repositorio.findById(TenantContext.requerido().id())
                .map(tenant -> new Contrato(
                        tenant.getTramoPoblacional().name(), tenant.getEstadoFacturacion().name()));
    }
}
