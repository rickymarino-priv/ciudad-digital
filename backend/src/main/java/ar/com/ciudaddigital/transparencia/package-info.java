/**
 * Transparencia activa básica: presupuesto (partidas y montos) y escala
 * salarial (cargos y montos, sin datos de personas) del municipio, con
 * consulta pública (backlog R11).
 *
 * <p>Es, otra vez, el mismo patrón que {@code boletin} (R7) y
 * {@code cementerio} (R8): lectura pública ({@code GET
 * /api/transparencia/presupuesto}, {@code GET /api/transparencia/sueldos})
 * sin sesión, y escritura protegida ({@code POST
 * /api/transparencia/presupuesto}, {@code POST /api/transparencia/sueldos})
 * con sesión y el permiso {@code transparencia.publicar}, asignado solo a
 * administrador (mismo criterio que {@code boletin.publicar}: publicar un
 * dato de transparencia institucional es un acto de mayor sensibilidad que
 * la operación diaria de reclamos/cementerio). Sin estado ni transiciones:
 * publicar un dato de transparencia es un alta y listo, igual que
 * {@code cementerio}. No hay ADR nuevo: se reutilizan tal cual
 * {@code rutasDeLecturaPublica()} (ADR 0012 §1) y los permisos granulares
 * (ADR 0011).
 */
package ar.com.ciudaddigital.transparencia;
