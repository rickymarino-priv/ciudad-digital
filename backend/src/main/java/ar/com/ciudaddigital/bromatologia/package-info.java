/**
 * Bromatología: padrón público de comercios habilitados y su historial de
 * inspecciones, protegido (R28, ADR 0032).
 *
 * <p>Segunda y última rebanada del Epic "Sin fase fija" (CD-36), después de
 * {@code defensacivil} (ADR 0031). Recorta a propósito el circuito
 * administrativo real de una inspección bromatológica (acta, infracción
 * tipificada, plazo de subsanación, expediente sancionatorio): eso depende
 * de normativa municipal/provincial que varía por jurisdicción y no hay
 * municipio piloto real que la valide (ADR 0032, Contexto). Lo que sí
 * construye es el patrón ya conocido del proyecto (alta protegida, lectura
 * pública, estado propio, ver {@code obras}/{@code arbolado}/
 * {@code espaciosverdes}) más un registro de auditoría —la inspección— que
 * <strong>motiva</strong> el cambio de estado del comercio como efecto de
 * su alta, en vez de un {@code PATCH} directo de estado (ADR 0032 §3).
 *
 * <p>Primer módulo del proyecto con dos entidades relacionadas por clave
 * foránea real donde una ({@code ComercioBromatologicoEntity}) es pública y
 * la otra ({@code InspeccionBromatologicaEntity}) es enteramente protegida:
 * el estado agregado del comercio es información de transparencia para el
 * vecino, pero el detalle de cada inspección (observaciones de texto libre
 * sobre un tercero privado, sin debido proceso formal detrás) no se publica
 * (ADR 0032, Contexto). También es el primer registro append-only del
 * proyecto: una inspección, una vez creada, no se edita ni se borra.
 *
 * <p>No depende de {@code proveedores}: un comercio bromatológico es una
 * empresa que el municipio fiscaliza, no una que le vende algo al
 * municipio, dos ciclos de vida sin relación de negocio necesaria (ADR
 * 0032, Contexto). Sin dato nominal de manipuladores de alimentos (dato de
 * salud de una persona identificable, sin mecanismo de datos sensibles
 * maduro en el producto todavía).
 */
package ar.com.ciudaddigital.bromatologia;
