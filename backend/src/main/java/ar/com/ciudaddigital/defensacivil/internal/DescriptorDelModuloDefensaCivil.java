package ar.com.ciudaddigital.defensacivil.internal;

import java.util.List;

import org.springframework.stereotype.Component;

import ar.com.ciudaddigital.entitlement.DescriptorDeModulo;

/**
 * Declara el módulo {@code defensacivil} ante el catálogo de entitlement
 * (ADR 0012 §1). Publicar una alerta y registrar o actualizar el estado
 * de un recurso requieren sesión y el permiso
 * {@code defensacivil.gestionar}: ambos registros los origina el
 * municipio, nunca el vecino (ADR 0031 §2), así que este módulo no
 * declara ninguna {@code rutaDeEscrituraPublica()} — no hay mutación
 * pública/anónima de ningún tipo acá.
 */
@Component
class DescriptorDelModuloDefensaCivil implements DescriptorDeModulo {

    static final String CODIGO = "defensacivil";

    @Override
    public String codigo() {
        return CODIGO;
    }

    @Override
    public String nombre() {
        return "Defensa Civil";
    }

    @Override
    public String descripcion() {
        return "Alertas públicas y recursos de Defensa Civil (refugios, puntos de encuentro, centros de "
                + "acopio) del municipio, con alta protegida por el municipio y lectura pública sin sesión.";
    }

    @Override
    public List<String> prefijosDeApi() {
        return List.of("/api/defensacivil");
    }

    /** Ambos listados son públicos (ADR 0031 §2). */
    @Override
    public List<String> rutasDeLecturaPublica() {
        return List.of("/api/defensacivil/alertas", "/api/defensacivil/recursos");
    }
}
