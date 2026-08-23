package ar.com.ciudaddigital.tenants.internal;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contenido de la columna {@code config} del tenant (ADR 0007): lo variable
 * del municipio, fuera de las columnas estructurales.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TenantConfig(Tema tema, List<String> modulosHabilitados) {

    public TenantConfig {
        modulosHabilitados = modulosHabilitados == null ? List.of() : List.copyOf(modulosHabilitados);
    }

    /**
     * Identidad visual del municipio. Los valores viajan al frontend como
     * tokens de diseño y se aplican en runtime (ADR 0006).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Tema(
            String colorPrimario,
            String colorPrimarioContraste,
            String colorAcento,
            String colorFondo,
            String colorSuperficie,
            String colorTexto,
            String colorTextoTenue,
            String tipografia,
            String logoUrl) {
    }
}
