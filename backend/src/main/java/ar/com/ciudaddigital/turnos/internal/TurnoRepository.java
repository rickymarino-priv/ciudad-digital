package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reservas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las reservas del municipio resuelto por el
 * {@code Host}. Sin lectura pública equivalente (ADR 0026 §5): solo la
 * usa {@code GestionDeReservas}, detrás del permiso {@code turnos.gestionar}
 * para el listado de gestión.
 */
interface TurnoRepository extends JpaRepository<TurnoEntity, Long> {

    /**
     * Chequeo temprano de duplicado (caso común, sin carrera) en
     * {@code GestionDeReservas#reservar}, antes de tocar el cupo: la
     * barrera real bajo concurrencia es la restricción {@code unique
     * (franja_id, dni_solicitante)} de la base (ADR 0026 §4).
     */
    boolean existsByFranjaIdAndDniSolicitante(Long franjaId, String dniSolicitante);

    List<TurnoEntity> findByFranjaIdOrderByCreadoEnAsc(Long franjaId);
}
