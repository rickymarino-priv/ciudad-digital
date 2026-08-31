/**
 * Espacios Verdes: padrón público de plazas, parques y paseos registrados
 * por el municipio, con alta protegida y estado propio actualizable (R25,
 * ADR 0029).
 *
 * <p>Tercera rebanada de Fase 4 (Gestión territorial), mismo patrón que
 * {@code obras} (ADR 0023) y {@code arbolado} (ADR 0024): alta protegida +
 * lectura pública + estado propio mutable con una tabla de transiciones
 * codificada en el servicio, sin entidad de historial ni motor de
 * expediente/workflow configurable de {@code mesaentradas} (ADR 0015). A
 * diferencia de {@code arbolado}, {@code tipo} es un enum cerrado (ADR
 * 0029 §3), mismo criterio que {@code TipoDeInstitucionEducativa}, y suma
 * la primera columna numérica de magnitud del patrón: {@code superficie}
 * en m² (ADR 0029 §4).
 *
 * <p>No depende de ningún otro módulo funcional, ni de {@code obras},
 * {@code arbolado} ni {@code educacion}: son cuatro instancias
 * independientes del mismo patrón, sin abstracción compartida (ADR 0029
 * §1/§8).
 */
package ar.com.ciudaddigital.espaciosverdes;
