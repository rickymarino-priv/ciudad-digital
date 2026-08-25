/**
 * Reclamos ciudadanos (311): baches, alumbrado, poda y arbolado,
 * recolección de residuos, animales sueltos (ADR 0014).
 *
 * <p>Primer módulo funcional real de Fase 1 —contratable, no de
 * demostración— y primer consumidor de la alta pública anónima
 * ({@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo#rutasDeEscrituraPublica()}):
 * un vecino carga un reclamo sin cuenta, y el municipio lo atiende con un
 * ciclo de vida fijo (nuevo → en proceso → resuelto/rechazado), no con el
 * motor de expediente/workflow configurable que Fase 1 sigue difiriendo
 * (ADR 0014 §3).
 */
package ar.com.ciudaddigital.reclamos;
