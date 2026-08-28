/**
 * Obras Públicas: registro público de obras en curso, con alta protegida
 * por el municipio y estado propio actualizable (R19, ADR 0023).
 *
 * <p>Primera rebanada de Fase 4 (Gestión territorial). A diferencia de
 * {@code boletin}/{@code transparencia}, donde un registro publicado no se
 * edita, el registro de esta obra sí muta después de creado, pero solo en
 * su campo {@code estado} (ADR 0023 §4): un ciclo de vida fijo con una
 * tabla de transiciones codificada en {@code GestionDeObras}, mismo
 * criterio que {@code reclamos} (ADR 0014 §3), no el motor de
 * expediente/workflow configurable de {@code mesaentradas} (ADR 0015).
 *
 * <p>No depende de ningún otro módulo funcional: ni {@code mesaentradas},
 * ni {@code multas}, ni {@code reclamos} (ADR 0023 §1).
 */
package ar.com.ciudaddigital.obras;
