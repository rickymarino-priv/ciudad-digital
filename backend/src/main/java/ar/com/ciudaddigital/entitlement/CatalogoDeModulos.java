package ar.com.ciudaddigital.entitlement;

import java.util.List;

/**
 * Consulta de solo lectura sobre el catálogo de módulos contratables y su
 * estado para el tenant del request en curso (ADR 0012).
 *
 * <p>Es el único punto por el que el resto del sistema pregunta por los
 * módulos: el filtro de gating, el controller público del catálogo, y el
 * módulo {@code tenants} para validar los códigos que le llegan al
 * escribir la lista de un municipio. Nadie más arma la lista de
 * descriptores por su cuenta.
 */
public interface CatalogoDeModulos {

    /** Catálogo completo de módulos declarados, ordenado por código. */
    List<DescriptorDeModulo> catalogo();

    /**
     * Si el municipio del request en curso tiene habilitado el módulo
     * {@code codigo}. Fail-closed (ADR 0012 §3): si no se puede determinar
     * qué tiene habilitado el tenant, devuelve {@code false}.
     */
    boolean habilitado(String codigo);
}
