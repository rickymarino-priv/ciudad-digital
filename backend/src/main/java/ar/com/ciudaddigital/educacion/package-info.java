/**
 * Educación municipal: padrón público de instituciones educativas de
 * gestión municipal (jardines maternales/de infantes, centros de
 * formación profesional), con alta protegida y estado propio actualizable
 * (R24, ADR 0028).
 *
 * <p>Segunda rebanada de Fase 5 (Áreas sociales), tercer caso del mismo
 * patrón que {@code obras} (ADR 0023) y {@code arbolado} (ADR 0024): alta
 * protegida + lectura pública + estado propio mutable con una tabla de
 * transiciones codificada en el servicio, sin entidad de historial ni
 * motor de expediente/workflow configurable de {@code mesaentradas} (ADR
 * 0015). A diferencia de ambos, {@code tipo} es un enum cerrado acotado a
 * la competencia municipal real en educación (ADR 0028 §3), y esta entidad
 * no tiene ningún campo de fecha propio.
 *
 * <p>No depende de ningún otro módulo funcional, ni de {@code obras} ni de
 * {@code arbolado}: tercera instancia independiente del mismo patrón, sin
 * abstracción compartida (ADR 0028, Contexto).
 */
package ar.com.ciudaddigital.educacion;
