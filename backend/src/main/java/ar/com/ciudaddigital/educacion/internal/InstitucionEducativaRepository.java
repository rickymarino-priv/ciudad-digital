package ar.com.ciudaddigital.educacion.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Instituciones educativas municipales del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las instituciones del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface InstitucionEducativaRepository extends JpaRepository<InstitucionEducativaEntity, Long> {

    /**
     * {@code estado}, {@code tipo} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y
     * en minúsculas, calculado en {@link GestionDeEducacion#buscar}, mismo
     * criterio que {@code ObraPublicaRepository#buscar} — con el parámetro
     * {@code null}-eable directamente dentro de {@code lower()}, el driver
     * de PostgreSQL no puede inferir su tipo y falla.
     */
    @Query("select i from InstitucionEducativaEntity i "
            + "where (:estado is null or i.estado = :estado) "
            + "and (:tipo is null or i.tipo = :tipo) "
            + "and (:patron is null or lower(i.nombre) like :patron or lower(i.ubicacion) like :patron) "
            + "order by i.creadoEn desc")
    List<InstitucionEducativaEntity> buscar(
            @Param("estado") EstadoDeInstitucion estado,
            @Param("tipo") TipoDeInstitucionEducativa tipo,
            @Param("patron") String patron);
}
