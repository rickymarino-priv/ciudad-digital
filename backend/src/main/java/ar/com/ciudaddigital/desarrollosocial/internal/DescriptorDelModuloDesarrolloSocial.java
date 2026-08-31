package ar.com.ciudaddigital.desarrollosocial.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code desarrollosocial} ante el catálogo de
 * entitlement (ADR 0012 §1). El catálogo de programas tiene lectura
 * pública y alta protegida (ADR 0025 §3), mismo mecanismo que
 * {@code obras}. Las inscripciones tienen alta pública anónima (ADR 0025
 * §5, mismo mecanismo que {@code reclamos}) pero, a diferencia de
 * cualquier otro módulo con estado propio del proyecto, ningún listado
 * público: la única lectura sin sesión es la consulta puntual por token
 * de seguimiento (ADR 0025 §6, ADR 0017 §4).
 */
@Component
class DescriptorDelModuloDesarrolloSocial implements DescriptorDeModulo {

    static final String CODIGO = "desarrollosocial";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Desarrollo Social";
    }

    @Override
    public String descripcion() {
        return "Catálogo público de programas sociales del municipio, con inscripción pública anónima "
                + "y datos personales minimizados, y bandeja de gestión de inscripciones para el municipio.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/desarrollosocial");
    }

    /**
     * El listado de programas (institucional, sin dato personal) y la
     * consulta puntual por token de seguimiento (variable de path, ADR
     * 0017 §4) son públicos. El listado de inscripciones no está acá a
     * propósito: no existe ninguna lectura pública de
     * {@code InscripcionSocialEntity} más allá del token (ADR 0025 §6).
     */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of(
                "/api/desarrollosocial/programas",
                "/api/desarrollosocial/inscripciones/seguimiento/{token}");
    }

    /** Solo el alta de inscripciones: un vecino se inscribe sin cuenta (ADR 0025 §5). */
    @Override
    public List<String> rutasDeEscrituraPublica() {
        return List.of("/api/desarrollosocial/inscripciones");
    }
}
