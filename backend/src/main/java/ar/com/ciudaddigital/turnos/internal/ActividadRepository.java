package ar.com.ciudaddigital.turnos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Actividades municipales del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las actividades del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ActividadRepository extends JpaRepository<ActividadEntity, Long> {

    /**
     * {@code tipo}, {@code estado} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"})
     * y en minúsculas, calculado en {@link GestionDeAgenda#buscarActividades},
     * mismo criterio que {@code ObraPublicaRepository#buscar}.
     */
    @Query("select a from ActividadEntity a "
            + "where (:tipo is null or a.tipo = :tipo) "
            + "and (:estado is null or a.estado = :estado) "
            + "and (:patron is null or lower(a.nombre) like :patron or lower(a.descripcion) like :patron) "
            + "order by a.creadoEn desc")
    List<ActividadEntity> buscar(
            @Param("tipo") TipoDeActividad tipo, @Param("estado") EstadoDeActividad estado,
            @Param("patron") String patron);
}
