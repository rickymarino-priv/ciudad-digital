package ar.com.ciudaddigital.defensacivil.internal;

/**
 * Motivo de una alerta de Defensa Civil (ADR 0031 §4): enum cerrado que
 * alcanza para separar los motivos más comunes sin inventar un
 * nomenclador más fino, mismo criterio que {@code CategoriaDeGacetilla}/
 * {@code TipoDeActividad}.
 */
enum TipoDeAlerta {
    METEOROLOGICA,
    INUNDACION,
    OLA_DE_CALOR,
    INCENDIO,
    OTRA
}
