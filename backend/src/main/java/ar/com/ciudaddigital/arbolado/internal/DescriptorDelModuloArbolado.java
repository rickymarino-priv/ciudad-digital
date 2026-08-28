package ar.com.ciudaddigital.arbolado.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code arbolado} ante el catálogo de entitlement (ADR
 * 0012 §1). Registrar un árbol y actualizar su estado sanitario requieren
 * sesión y el permiso {@code arbolado.gestionar}: el registro lo origina
 * el municipio, nunca el vecino (ADR 0024 §2), así que este módulo no
 * declara ninguna {@code rutaDeEscrituraPublica()} — no hay mutación
 * pública/anónima de ningún tipo acá.
 */
@Component
class DescriptorDelModuloArbolado implements DescriptorDeModulo {

    static final String CODIGO = "arbolado";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Arbolado Urbano";
    }

    @Override
    public String descripcion() {
        return "Padrón público de árboles urbanos registrados por el municipio: especie, ubicación y "
                + "estado sanitario, con alta protegida por el municipio y lectura pública sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/arbolado");
    }

    /** Solo el listado con filtros es público (ADR 0024 §2). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/arbolado");
    }
}
