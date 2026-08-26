/**
 * Multas de tránsito del Juzgado de Faltas: alta protegida por un agente,
 * búsqueda/pago/descargo públicos, resolución del descargo por un
 * administrador (R17, ADR 0021).
 *
 * <p>A diferencia de {@code reclamos}/{@code mesaentradas}/{@code
 * proveedores} (el vecino inicia el registro), acá el municipio inicia:
 * un agente de tránsito labra el acta y el vecino solo consulta, paga o
 * impugna después (ADR 0021 §3). El ciclo de vida es un enum fijo con una
 * tabla de transiciones codificada en {@code GestionDeMultas}, no el motor
 * de expediente de {@code mesaentradas} (ADR 0021 §2): no hay hoy un
 * segundo tipo de infracción que justifique esa indirección, y ese motor
 * es de todos modos inalcanzable desde otro módulo (ADR 0015 §5).
 *
 * <p>Depende de {@code pagos.PasarelaDePago} para el cobro, exactamente
 * como {@code tasas} (ADR 0018), y es dueño de su propio estado: la
 * confirmación de pago no pasa por {@code pagos}.
 */
package ar.com.ciudaddigital.multas;
