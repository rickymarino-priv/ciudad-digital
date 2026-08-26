/**
 * Portal de proveedores del municipio (backlog R14).
 *
 * <p>Módulo funcional contratable (no canon base, ADR 0014 §2): una
 * empresa se registra sin cuenta —razón social, CUIT, rubro, contacto y
 * qué documentación declara tener, sin subir ningún archivo— y el
 * municipio, con el permiso {@code proveedores.gestionar}, la aprueba o
 * la rechaza. Tercer consumidor de {@code seguimientoanonimo} (ADR 0017),
 * junto con {@code reclamos} y {@code mesaentradas}: la empresa consulta
 * después el estado de su registro por posesión del token que recibió al
 * registrarse, sin necesitar cuenta ni sesión.
 */
package ar.com.ciudaddigital.proveedores;
