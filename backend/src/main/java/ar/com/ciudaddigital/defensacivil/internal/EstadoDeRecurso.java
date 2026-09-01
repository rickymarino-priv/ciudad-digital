package ar.com.ciudaddigital.defensacivil.internal;

/**
 * Estado operativo de un recurso de Defensa Civil (ADR 0031 §5):
 * transición libre en ambos sentidos, mismo criterio que
 * {@code EstadoDePrograma} en Desarrollo Social (ADR 0025 §3) — un
 * refugio se activa y se desactiva según la situación, no hay una
 * progresión unidireccional que modelar.
 */
enum EstadoDeRecurso {
    ACTIVO,
    INACTIVO
}
