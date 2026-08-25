/**
 * Boletín Oficial digital del municipio: ordenanzas, decretos,
 * resoluciones y comunicados publicados y buscables por cualquiera
 * (backlog R7).
 *
 * <p>Es, a propósito, el complemento de {@code reclamos} (R6): ahí la
 * escritura era pública y la lectura protegida; acá la lectura
 * ({@code GET /api/boletin}) es pública y la escritura
 * ({@code POST /api/boletin}) requiere sesión y el permiso
 * {@code boletin.publicar}. Las dos combinaciones ya estaban cubiertas por
 * {@code rutasDeLecturaPublica()}
 * ({@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo}, ADR 0012
 * §1) y el modelo de permisos granulares (ADR 0011), así que esta
 * rebanada no agrega ningún mecanismo nuevo.
 */
package ar.com.ciudaddigital.boletin;
