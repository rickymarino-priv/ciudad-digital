package ar.com.ciudaddigital.transparencia.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code transparencia} ante el catálogo de entitlement
 * (ADR 0012 §1). Mismo patrón que {@code DescriptorDelModuloBoletin}: la
 * lectura es pública, publicar requiere sesión y el permiso
 * {@code transparencia.publicar}.
 */
@Component
class DescriptorDelModuloTransparencia implements DescriptorDeModulo {

    static final String CODIGO = "transparencia";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Transparencia Activa";
    }

    @Override
    public String descripcion() {
        return "Publicación de presupuesto (partidas y montos) y escala salarial (cargos y "
                + "montos, sin datos de personas) del municipio, con consulta pública.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/transparencia");
    }

    /**
     * Los dos listados son públicos, sin sesión; no hay endpoint de detalle
     * por id en esta rebanada. Publicar requiere sesión y el permiso
     * {@code transparencia.publicar}, así que no se declara como escritura
     * pública.
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/transparencia/presupuesto", "/api/transparencia/sueldos");
    }
}
