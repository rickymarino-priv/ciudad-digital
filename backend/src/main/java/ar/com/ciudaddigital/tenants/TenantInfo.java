package ar.com.ciudaddigital.tenants;

import java.util.UUID;

/**
 * Municipio resuelto para el request en curso.
 *
 * <p>Es la vista que el resto de los módulos tiene de un tenant: identidad y
 * a qué base de datos corresponde. No expone configuración visual ni
 * comercial, que son asunto interno del módulo de tenants.
 */
public record TenantInfo(
        UUID id,
        String slug,
        String nombreMunicipio,
        String nombreBaseDatos) {
}
