package ar.com.ciudaddigital.defensacivil.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Recursos de Defensa Civil del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los recursos del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface RecursoDeDefensaCivilRepository extends JpaRepository<RecursoDeDefensaCivilEntity, Long> {

    /**
     * {@code tipo}, {@code estado} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"})
     * y en minúsculas, calculado en {@link GestionDeRecursos#buscar},
     * mismo criterio que {@code AlertaDeDefensaCivilRepository#buscar}.
     */
    @Query("select r from RecursoDeDefensaCivilEntity r "
            + "where (:tipo is null or r.tipo = :tipo) "
            + "and (:estado is null or r.estado = :estado) "
            + "and (:patron is null or lower(r.nombre) like :patron or lower(r.direccion) like :patron) "
            + "order by r.creadoEn desc")
    List<RecursoDeDefensaCivilEntity> buscar(
            @Param("tipo") TipoDeRecurso tipo,
            @Param("estado") EstadoDeRecurso estado,
            @Param("patron") String patron);
}
