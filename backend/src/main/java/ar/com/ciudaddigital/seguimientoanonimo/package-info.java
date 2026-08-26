/**
 * Generación y verificación del token de seguimiento anónimo (ADR 0017).
 *
 * <p>Módulo canon base, con el mismo estatus que {@code persistencia}/
 * {@code acceso}/{@code entitlement}: no es contratable, no tiene
 * {@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo} propio y no
 * persiste nada. {@code reclamos} y {@code mesaentradas} lo usan para que
 * un vecino sin cuenta pueda volver a consultar el estado de su reclamo o
 * trámite: cada módulo consumidor guarda su propio hash del token en su
 * propia tabla y hace su propia consulta, este módulo solo provee el
 * algoritmo compartido (generación con {@code SecureRandom} y hash con
 * SHA-256) para no duplicarlo entre los dos consumidores reales que ya
 * existen desde el día uno de esta rebanada.
 */
package ar.com.ciudaddigital.seguimientoanonimo;
