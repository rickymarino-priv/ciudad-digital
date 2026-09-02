package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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

    /**
     * Conteo agregado de turnos reservados por el nombre de la actividad a
     * la que pertenece su franja, para {@code FuenteDeMetricasDeTurnos}
     * (ADR 0034 §3). {@code TurnoEntity}, {@code FranjaHorariaEntity} y
     * {@code ActividadEntity} viven las tres en este módulo, así que el
     * join por igualdad de id es válido sin relación {@code @ManyToOne}
     * declarada (id informativo, no referencial, mismo criterio que
     * {@code RegistroAuditoriaEntity}). Una actividad sin ningún turno
     * reservado no aparece en el resultado: no se rellena con cero.
     */
    @Query("select a.nombre as etiqueta, count(t) as cantidad "
            + "from TurnoEntity t, FranjaHorariaEntity f, ActividadEntity a "
            + "where f.id = t.franjaId and a.id = f.actividadId "
            + "group by a.nombre order by a.nombre asc")
    List<ConteoPorEtiqueta> contarPorActividad();

    interface ConteoPorEtiqueta {
        String getEtiqueta();

        long getCantidad();
    }
}
