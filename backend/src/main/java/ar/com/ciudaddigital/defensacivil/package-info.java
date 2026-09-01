/**
 * Defensa Civil: alertas públicas y recursos de emergencia (refugios,
 * puntos de encuentro, centros de acopio) publicados por el municipio, con
 * alta protegida y lectura pública (R27, ADR 0031).
 *
 * <p>Primera rebanada de "Seguridad / Defensa Civil", sin fase fija en el
 * roadmap: el módulo se llama {@code defensacivil}, no {@code seguridad},
 * porque no cubre vigilancia/CCTV/protocolos policiales, solo el recorte
 * de información institucional sin hardware ni normativa municipal
 * específica (ADR 0031, Contexto). No construye un canal de reporte
 * ciudadano: eso ya lo cubre {@code reclamos} (ADR 0014).
 *
 * <p>Sexto caso del patrón "alta protegida + lectura pública + estado
 * propio mutable" (después de {@code obras}, {@code arbolado},
 * {@code educacion}, {@code espaciosverdes} y {@code eventos}), y primero
 * con dos entidades independientes bajo un único módulo y un único
 * permiso ({@code defensacivil.gestionar}, ADR 0031 §3), mismo criterio
 * que evitó separar {@code arbolado.gestionar}/{@code obras.gestionar}
 * entre alta y cambio de estado (ADR 0024 §5). {@code AlertaDeDefensaCivilEntity}
 * es, además, el primer contenido público del proyecto con estado de
 * <strong>vigencia</strong> ({@code VIGENTE}/{@code FINALIZADA}) en vez de
 * estado de gestión (Reclamos/Multas/Obras/Arbolado) o publicación
 * inmutable (Boletín/Prensa) (ADR 0031, Consecuencias).
 *
 * <p>No depende de ningún otro módulo funcional, ni siquiera de
 * {@code reclamos}, {@code obras}, {@code arbolado}, {@code educacion},
 * {@code espaciosverdes} o {@code eventos}: {@code AlertaDeDefensaCivilEntity}
 * y {@code RecursoDeDefensaCivilEntity} se definen desde cero acá, sin
 * relación de esquema entre sí (ADR 0031 §1).
 */
package ar.com.ciudaddigital.defensacivil;
