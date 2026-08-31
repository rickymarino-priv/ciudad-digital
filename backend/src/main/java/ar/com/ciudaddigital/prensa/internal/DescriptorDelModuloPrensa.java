package ar.com.ciudaddigital.prensa.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code prensa} ante el catálogo de entitlement
 * (ADR 0012 §1). Es, a propósito, el mismo patrón que
 * {@code DescriptorDelModuloBoletin} (R7): la lectura es pública y la
 * escritura requiere sesión y permiso.
 */
@Component
class DescriptorDelModuloPrensa implements DescriptorDeModulo {

    static final String CODIGO = "prensa";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Prensa y Comunicación";
    }

    @Override
    public String descripcion() {
        return "Gacetillas y comunicados de prensa publicados por el municipio, buscables por cualquiera.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/prensa");
    }

    /**
     * Solo el listado/búsqueda es público: no hay endpoint de detalle por
     * id en esta rebanada. Publicar requiere sesión y el permiso
     * {@code prensa.publicar}, así que no se declara como escritura
     * pública.
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/prensa");
    }
}
