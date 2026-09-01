/**
 * Eventos: agenda pública de eventos culturales, turísticos y deportivos
 * publicados por el municipio, con alta protegida y cancelación (R26, ADR
 * 0030).
 *
 * <p>Tercera rebanada construible de Fase 6 (Cultura, Turismo y Deportes),
 * después de {@code turnos} (ADR 0026) y {@code prensa} (ADR 0027). Quinto
 * caso del patrón "alta protegida + lectura pública + estado propio
 * mutable" (después de {@code obras}, {@code arbolado}, {@code educacion}
 * y {@code espaciosverdes}), pero con la topología de transición más
 * simple hasta ahora: un único salto sin retorno,
 * {@code PROGRAMADO → CANCELADO} (ADR 0030 §3), y el primer módulo del
 * proyecto que ordena su listado público por un campo de fecha propio
 * ({@code fechaInicio}) en vez de por {@code creadoEn} (ADR 0030 §4).
 *
 * <p>No depende de {@code turnos} ni de ningún otro módulo funcional:
 * {@code CategoriaDeEvento} y {@code EstadoDeEvento} se definen desde cero
 * acá, sin reutilizar {@code TipoDeActividad} de {@code turnos} ni ninguna
 * clase de {@code obras}/{@code arbolado}/{@code educacion}/
 * {@code espaciosverdes} (ADR 0030 §1/§2/§7).
 */
package ar.com.ciudaddigital.eventos;
