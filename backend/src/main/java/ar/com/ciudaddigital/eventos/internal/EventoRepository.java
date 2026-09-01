package ar.com.ciudaddigital.eventos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Eventos de la agenda del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los eventos del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface EventoRepository extends JpaRepository<EventoEntity, Long> {

    /**
     * {@code categoria}, {@code estado} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y
     * en minúsculas, calculado en {@link GestionDeEventos#buscar}, mismo
     * criterio que {@code EspacioVerdeRepository#buscar} — con el
     * parámetro {@code null}-eable directamente dentro de {@code lower()},
     * el driver de PostgreSQL no puede inferir su tipo y falla.
     *
     * <p>Orden por {@code fechaInicio} ascendente y, a igual fecha, por
     * {@code nombre} ascendente (ADR 0030 §4) — desviación deliberada del
     * resto del patrón, que ordena por {@code creadoEn} descendente.
     */
    @Query("select e from EventoEntity e "
            + "where (:categoria is null or e.categoria = :categoria) "
            + "and (:estado is null or e.estado = :estado) "
            + "and (:patron is null or lower(e.nombre) like :patron or lower(e.ubicacion) like :patron) "
            + "order by e.fechaInicio asc, e.nombre asc")
    List<EventoEntity> buscar(
            @Param("categoria") CategoriaDeEvento categoria,
            @Param("estado") EstadoDeEvento estado,
            @Param("patron") String patron);
}
