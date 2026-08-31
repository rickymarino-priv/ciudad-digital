/**
 * Desarrollo Social: catálogo público de programas sociales y alta pública
 * de inscripciones con datos personales minimizados (R21, ADR 0025).
 *
 * <p>Primera rebanada de Fase 5 (Áreas sociales). Dos entidades
 * independientes con perfiles de riesgo muy distintos:
 * {@code ProgramaSocialEntity} (catálogo institucional, sin dato
 * personal, mismo perfil que {@code ObraPublicaEntity}/
 * {@code ArbolUrbanoEntity}) e {@code InscripcionSocialEntity} (datos
 * personales de un vecino, con controles de acceso más estrictos que
 * cualquier módulo anterior). A diferencia de todos los módulos con
 * estado propio construidos hasta ahora, {@code InscripcionSocialEntity}
 * no tiene ningún endpoint de lectura pública: la única lectura sin
 * sesión es la consulta puntual por token de seguimiento (ADR 0025 §6),
 * reutilizando {@code seguimientoanonimo.TokenDeSeguimiento} tal cual.
 *
 * <p>No depende de ningún otro módulo funcional: ni {@code obras}, ni
 * {@code arbolado}, ni {@code reclamos}, ni {@code mesaentradas} (ADR
 * 0025 §1).
 */
package ar.com.ciudaddigital.desarrollosocial;
