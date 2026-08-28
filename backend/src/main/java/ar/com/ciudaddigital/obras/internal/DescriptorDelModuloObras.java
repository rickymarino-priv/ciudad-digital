package ar.com.ciudaddigital.obras.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code obras} ante el catálogo de entitlement (ADR
 * 0012 §1). Registrar una obra y actualizar su estado requieren sesión y
 * el permiso {@code obras.gestionar}: el registro lo origina el
 * municipio, nunca el vecino (ADR 0023 §2), así que este módulo no
 * declara ninguna {@code rutaDeEscrituraPublica()} — a diferencia de
 * {@code reclamos} y {@code multas}, no hay mutación pública/anónima de
 * ningún tipo acá.
 */
@Component
class DescriptorDelModuloObras implements DescriptorDeModulo {

    static final String CODIGO = "obras";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Obras Públicas";
    }

    @Override
    public String descripcion() {
        return "Registro público de obras en curso del municipio: nombre, tipo, ubicación y "
                + "estado de avance, con alta protegida por el municipio y lectura pública sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/obras");
    }

    /** Solo el listado con filtros es público (ADR 0023 §2). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/obras");
    }
}
