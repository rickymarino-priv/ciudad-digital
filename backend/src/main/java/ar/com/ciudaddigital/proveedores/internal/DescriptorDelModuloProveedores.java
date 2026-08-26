package ar.com.ciudaddigital.proveedores.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code proveedores} ante el catálogo de entitlement
 * (ADR 0012 §1). Funcionalidad de producto real y contratable, no canon
 * base (ADR 0014 §2): un municipio puede no tenerlo, igual que cualquier
 * otro módulo de área.
 */
@Component
class DescriptorDelModuloProveedores implements DescriptorDeModulo {

    static final String CODIGO = "proveedores";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Portal de proveedores";
    }

    @Override
    public String descripcion() {
        return "Registro de proveedores del municipio, con la documentación que declaran tener "
                + "(constancia de AFIP, seguro de responsabilidad civil, certificado de antecedentes).";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/proveedores");
    }

    /**
     * El listado sigue sin ser público: mismo criterio que el resto de los
     * módulos de gestión (ADR 0014 §6). La única lectura pública es la
     * consulta por posesión de un token no adivinable (ADR 0017).
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/proveedores/seguimiento/{token}");
    }

    /** Solo el alta: una empresa se registra sin cuenta (ADR 0014 §1). */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/proveedores");
    }
}
