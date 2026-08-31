package ar.com.ciudaddigital.espaciosverdes.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code espaciosverdes} ante el catálogo de
 * entitlement (ADR 0012 §1). Registrar un espacio verde y actualizar su
 * estado requieren sesión y el permiso {@code espaciosverdes.gestionar}:
 * el registro lo origina el municipio, nunca el vecino (ADR 0029 §2), así
 * que este módulo no declara ninguna {@code rutaDeEscrituraPublica()} — no
 * hay mutación pública/anónima de ningún tipo acá.
 */
@Component
class DescriptorDelModuloEspaciosVerdes implements DescriptorDeModulo {

    static final String CODIGO = "espaciosverdes";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Espacios Verdes";
    }

    @Override
    public String descripcion() {
        return "Padrón público de plazas, parques y paseos registrados por el municipio: nombre, tipo, "
                + "ubicación, superficie y estado, con alta protegida por el municipio y lectura pública "
                + "sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/espaciosverdes");
    }

    /** Solo el listado con filtros es público (ADR 0029 §2). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/espaciosverdes");
    }
}
