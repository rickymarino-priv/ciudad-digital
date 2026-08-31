package ar.com.ciudaddigital.desarrollosocial.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Programas sociales del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los programas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ProgramaSocialRepository extends JpaRepository<ProgramaSocialEntity, Long> {

    /**
     * {@code estado} y {@code patron} son opcionales: {@code null}
     * desactiva el filtro correspondiente. {@code patron} ya viene armado
     * como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y en
     * minúsculas, calculado en {@link GestionDeProgramasSociales#buscar},
     * mismo criterio que {@code ObraPublicaRepository#buscar}.
     */
    @Query("select p from ProgramaSocialEntity p "
            + "where (:estado is null or p.estado = :estado) "
            + "and (:patron is null or lower(p.nombre) like :patron or lower(p.descripcion) like :patron) "
            + "order by p.creadoEn desc")
    List<ProgramaSocialEntity> buscar(@Param("estado") EstadoDePrograma estado, @Param("patron") String patron);
}
