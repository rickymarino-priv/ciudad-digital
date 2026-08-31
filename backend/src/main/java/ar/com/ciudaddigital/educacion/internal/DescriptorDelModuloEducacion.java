package ar.com.ciudaddigital.educacion.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code educacion} ante el catálogo de entitlement
 * (ADR 0012 §1). Registrar una institución y actualizar su estado
 * requieren sesión y el permiso {@code educacion.gestionar}: el registro
 * lo origina el municipio, nunca la institución misma ni un vecino (ADR
 * 0028 §2), así que este módulo no declara ninguna
 * {@code rutaDeEscrituraPublica()} — mismo criterio que {@code obras} y
 * {@code arbolado}, a diferencia de {@code reclamos} y {@code multas}.
 */
@Component
class DescriptorDelModuloEducacion implements DescriptorDeModulo {

    static final String CODIGO = "educacion";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Educación municipal";
    }

    @Override
    public String descripcion() {
        return "Padrón público de instituciones educativas de gestión municipal (jardines maternales/de "
                + "infantes, centros de formación profesional): nombre, tipo, ubicación y estado, con alta "
                + "protegida por el municipio y lectura pública sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/educacion");
    }

    /** Solo el listado con filtros es público (ADR 0028 §2). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/educacion");
    }
}
