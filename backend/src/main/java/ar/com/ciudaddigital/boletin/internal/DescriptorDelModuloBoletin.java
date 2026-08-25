package ar.com.ciudaddigital.boletin.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code boletin} ante el catálogo de entitlement
 * (ADR 0012 §1). Es, a propósito, el complemento de
 * {@code DescriptorDelModuloReclamos} (R6): ahí la escritura era pública y
 * la lectura protegida, acá es al revés.
 */
@Component
class DescriptorDelModuloBoletin implements DescriptorDeModulo {

    static final String CODIGO = "boletin";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Boletín Oficial";
    }

    @Override
    public String descripcion() {
        return "Boletín Oficial digital: ordenanzas, decretos, resoluciones y comunicados "
                + "publicados por el municipio, buscables por cualquiera.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/boletin");
    }

    /**
     * Solo el listado/búsqueda es público: no hay endpoint de detalle por
     * id en esta rebanada. Publicar requiere sesión y el permiso
     * {@code boletin.publicar}, así que no se declara como escritura
     * pública.
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/boletin");
    }
}
