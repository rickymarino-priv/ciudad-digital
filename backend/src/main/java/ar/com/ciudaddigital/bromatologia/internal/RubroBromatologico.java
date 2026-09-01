package ar.com.ciudaddigital.bromatologia.internal;

/**
 * Rubro de un comercio bromatológico, enum cerrado que cubre los rubros más
 * comunes de un comercio de alimentos sin inventar un nomenclador más fino
 * (ADR 0032 §2), mismo criterio que {@code TipoDeAlerta}/
 * {@code CategoriaDeGacetilla}.
 */
enum RubroBromatologico {
    VERDULERIA,
    CARNICERIA,
    PANADERIA,
    RESTAURANTE,
    ALMACEN,
    OTRO
}
