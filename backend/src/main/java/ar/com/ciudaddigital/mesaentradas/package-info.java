/**
 * Mesa de Entradas digital: inicio y gestión de trámites del municipio
 * (backlog R9, CD-17).
 *
 * <p>Primer consumidor del motor de expediente/workflow mínimo (ADR 0015):
 * un circuito de estados fijo por tipo de trámite, definido en código y
 * catálogo de producto, no editable por el municipio. El alta pública y
 * anónima reutiliza tal cual el mecanismo de la alta de reclamos
 * ({@link ar.com.ciudaddigital.entitlement.DescriptorDeModulo#rutasDeEscrituraPublica()},
 * ADR 0014 §1): un vecino inicia un trámite sin cuenta, y el municipio lo
 * tramita con sesión y permiso.
 *
 * <p>Esta rebanada implementa un único {@code TipoDeTramite}
 * ({@code CERTIFICADO_DOMICILIO}) de los 3-5 que el roadmap de Fase 1
 * prevé para el subset de Trámites a Distancia (habilitación comercial
 * simple, permiso de obra menor). Sumar un tipo nuevo es agregar un valor
 * de enum y su {@code CircuitoDeTramite} propio (ADR 0015 §1); el
 * <b>avance de estado</b> es agnóstico al tipo y no toca el motor. El
 * <b>alta</b> ({@code GestionDeExpedientes#iniciar}, el controller) hoy
 * expone como parámetros explícitos los campos propios del único tipo
 * existente, así que un segundo tipo con campos distintos sí requiere
 * tocar esa firma, hasta que se resuelva la forma de los datos variables
 * por tipo (ADR 0015 §3, "Pendiente de definir").
 */
package ar.com.ciudaddigital.mesaentradas;
