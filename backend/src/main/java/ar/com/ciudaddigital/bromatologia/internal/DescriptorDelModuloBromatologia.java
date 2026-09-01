package ar.com.ciudaddigital.bromatologia.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code bromatologia} ante el catálogo de entitlement
 * (ADR 0012 §1). Registrar un comercio, registrar una inspección y leer el
 * historial de inspecciones requieren sesión y el permiso
 * {@code bromatologia.gestionar} (ADR 0032 §5). Solo el padrón de
 * comercios es lectura pública: el historial de inspecciones queda
 * deliberadamente fuera de {@link #rutasDeLecturaPublica()} (ADR 0032 §4).
 */
@Component
class DescriptorDelModuloBromatologia implements DescriptorDeModulo {

    static final String CODIGO = "bromatologia";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Bromatología";
    }

    @Override
    public String descripcion() {
        return "Padrón público de comercios habilitados (verdulerías, carnicerías, restaurantes y afines) "
                + "y su historial de inspecciones, con alta protegida por el municipio.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/bromatologia");
    }

    /**
     * Únicamente el padrón de comercios es público. El historial de
     * inspecciones ({@code /comercios/{id}/inspecciones}) no está acá a
     * propósito: queda protegido por defecto, más el {@code @PreAuthorize}
     * del controller (ADR 0032 §4).
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/bromatologia/comercios");
    }
}
