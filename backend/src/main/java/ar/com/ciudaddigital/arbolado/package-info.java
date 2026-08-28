/**
 * Arbolado Urbano: padrón público de árboles registrados por el municipio,
 * con alta protegida y estado sanitario propio actualizable (R20, ADR 0024).
 *
 * <p>Segunda rebanada de Fase 4 (Gestión territorial), mismo patrón que
 * {@code obras} (ADR 0023): alta protegida + lectura pública + estado
 * propio mutable con una tabla de transiciones codificada en el servicio,
 * sin entidad de historial ni motor de expediente/workflow configurable de
 * {@code mesaentradas} (ADR 0015). A diferencia de {@code obras}, no hay un
 * enum de {@code tipo}: {@code especie} y {@code ubicacion} son texto libre
 * (ADR 0024 §3), y las transiciones del estado sanitario son propias de
 * este dominio (ADR 0024 §4).
 *
 * <p>No depende de ningún otro módulo funcional, ni siquiera de
 * {@code obras}: son dos instancias independientes del mismo patrón, sin
 * abstracción compartida todavía (ADR 0024 §1/§7).
 */
package ar.com.ciudaddigital.arbolado;
