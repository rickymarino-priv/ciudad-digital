package ar.com.ciudaddigital.ejemplo.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code ejemplo} ante el catálogo de entitlement
 * (ADR 0012 §1). Es la única pieza que un módulo funcional necesita
 * publicar para volverse contratable: código, nombre, descripción y los
 * prefijos de ruta que le pertenecen.
 */
@Component
class DescriptorDelModuloEjemplo implements DescriptorDeModulo {

    static final String CODIGO = "ejemplo";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Módulo de ejemplo";
    }

    @Override
    public String descripcion() {
        return "Módulo de demostración del mecanismo de contratación de módulos; "
                + "no es funcionalidad de producto (ADR 0012 §10).";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/ejemplo");
    }

    /** El ping lo tiene que poder ver un vecino anónimo; el eco requiere sesión y permiso. */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/ejemplo/ping");
    }
}
