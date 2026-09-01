package ar.com.ciudaddigital.bromatologia.internal;

/**
 * Estado sanitario de un comercio bromatológico. Se reutiliza tal cual como
 * tipo del campo {@code resultado} de {@link InspeccionBromatologicaEntity}:
 * no se define un segundo enum con nombres distintos para el mismo conjunto
 * de valores, evita una tabla de mapeo entre "resultado de inspección" y
 * "estado de comercio" que no aportaría nada (ADR 0032 §3).
 *
 * <p>Un comercio nace siempre {@code HABILITADO} (ADR 0032 §2) y no tiene
 * ningún {@code PATCH} que cambie este campo directamente: la única vía de
 * cambio es registrar una inspección con este mismo enum como
 * {@code resultado} ({@code GestionDeBromatologia#registrarInspeccion}).
 */
enum EstadoBromatologico {
    HABILITADO,
    OBSERVADO,
    CLAUSURADO
}
