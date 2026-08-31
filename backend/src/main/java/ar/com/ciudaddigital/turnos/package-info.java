/**
 * Turnos para actividades municipales: catálogo público de actividades de
 * deporte, cultura y turismo, franjas horarias con cupo, y reserva pública
 * anónima con decremento atómico de cupo (R22, ADR 0026).
 *
 * <p>Primera rebanada de Fase 6 (Áreas de imagen y control de gestión).
 * Tres entidades con perfiles de riesgo distintos: {@code ActividadEntity}
 * y {@code FranjaHorariaEntity} (catálogo institucional, sin dato personal,
 * mismo perfil que {@code ObraPublicaEntity}/{@code ArbolUrbanoEntity}/
 * {@code ProgramaSocialEntity}) y {@code TurnoEntity} (datos personales de
 * un vecino, del mismo nivel que Mesa de Entradas/Reclamos, sin lectura
 * pública — ADR 0026 §5).
 *
 * <p>Primer módulo del proyecto con un recurso de cupo compartido entre
 * solicitantes públicos anónimos concurrentes: el mecanismo de reserva
 * resuelve la carrera con un {@code UPDATE} condicional atómico en vez de
 * un {@code SELECT} seguido de un {@code UPDATE} (ADR 0026 §4), y es el
 * primer uso de 409 Conflict como código de error de negocio del proyecto
 * (ADR 0026 §7).
 *
 * <p>No depende de ningún otro módulo funcional: ni {@code obras}, ni
 * {@code arbolado}, ni {@code reclamos}, ni {@code mesaentradas}, ni
 * {@code desarrollosocial} (ADR 0026 §1).
 */
package ar.com.ciudaddigital.turnos;
