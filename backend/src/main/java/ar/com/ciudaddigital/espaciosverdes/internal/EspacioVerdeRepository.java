package ar.com.ciudaddigital.espaciosverdes.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Espacios verdes del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los espacios verdes del municipio resuelto por
 * el {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface EspacioVerdeRepository extends JpaRepository<EspacioVerdeEntity, Long> {

    /**
     * {@code estado}, {@code tipo} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y
     * en minúsculas, calculado en {@link GestionDeEspaciosVerdes#buscar},
     * mismo criterio que {@code ObraPublicaRepository#buscar} — con el
     * parámetro {@code null}-eable directamente dentro de {@code lower()},
     * el driver de PostgreSQL no puede inferir su tipo y falla.
     */
    @Query("select e from EspacioVerdeEntity e "
            + "where (:estado is null or e.estado = :estado) "
            + "and (:tipo is null or e.tipo = :tipo) "
            + "and (:patron is null or lower(e.nombre) like :patron or lower(e.ubicacion) like :patron) "
            + "order by e.creadoEn desc")
    List<EspacioVerdeEntity> buscar(
            @Param("estado") EstadoDeEspacioVerde estado,
            @Param("tipo") TipoDeEspacioVerde tipo,
            @Param("patron") String patron);
}
