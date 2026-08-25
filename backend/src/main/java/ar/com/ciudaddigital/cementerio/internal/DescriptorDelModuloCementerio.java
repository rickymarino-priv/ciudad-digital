package ar.com.ciudaddigital.cementerio.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code cementerio} ante el catálogo de entitlement
 * (ADR 0012 §1). Mismo patrón que {@code DescriptorDelModuloBoletin} (R7):
 * lectura pública, escritura protegida.
 */
@Component
class DescriptorDelModuloCementerio implements DescriptorDeModulo {

    static final String CODIGO = "cementerio";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Cementerio";
    }

    @Override
    public String descripcion() {
        return "Registro de sepulturas del cementerio municipal: parcelas, nichos y panteones, "
                + "con búsqueda pública por nombre del difunto.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/cementerio");
    }

    /**
     * Solo el listado/búsqueda es público: no hay endpoint de detalle por
     * id en esta rebanada. Registrar una sepultura requiere sesión y el
     * permiso {@code cementerio.registrar}, así que no se declara como
     * escritura pública.
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/cementerio");
    }
}
