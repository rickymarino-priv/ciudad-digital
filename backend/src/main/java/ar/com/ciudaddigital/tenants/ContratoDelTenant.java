package ar.com.ciudaddigital.tenants;

import java.util.Optional;

/**
 * Contrato mínimo del municipio del request en curso, de solo lectura (ADR
 * 0022 §1): el mismo tramo poblacional y estado de facturación que ya ve la
 * consola del proveedor, sin la nota interna de facturación —esa nota es de
 * seguimiento comercial interno de la plataforma, no una comunicación
 * pensada para el municipio.
 *
 * <p>Mismo patrón que {@link ar.com.ciudaddigital.entitlement.ModulosDelTenant}
 * (ADR 0012 §2): interfaz pública que consultan los módulos que atienden un
 * request de municipio, implementación interna que resuelve el tenant vía
 * {@link TenantContext}.
 */
public interface ContratoDelTenant {

    Optional<Contrato> actual();

    record Contrato(String tramoPoblacional, String estadoFacturacion) {
    }
}
