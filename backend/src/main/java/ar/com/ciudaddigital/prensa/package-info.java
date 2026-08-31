/**
 * Prensa y Comunicación: gacetillas y comunicados de prensa publicados por
 * el municipio, buscables por cualquiera (R23, ADR 0027).
 *
 * <p>Es, en forma, un calco de {@code boletin} (R7): la lectura
 * ({@code GET /api/prensa}) es pública y la escritura
 * ({@code POST /api/prensa}) requiere sesión y un permiso propio del
 * módulo. Reutiliza el mismo mecanismo de {@code rutasDeLecturaPublica()}
 * ({@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo}, ADR 0012
 * §1) sin agregar nada nuevo. Ver ADR 0027 para el resto de las
 * decisiones de esta rebanada, incluida la de que
 * {@code prensa.publicar} se asigna a {@code administrador} y
 * {@code agente} — a diferencia de {@code boletin.publicar} (ADR 0027 §3).
 */
package ar.com.ciudaddigital.prensa;
