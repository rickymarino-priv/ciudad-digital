package ar.com.ciudaddigital.cementerio.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Sepulturas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las sepulturas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface SepulturaRepository extends JpaRepository<SepulturaEntity, Long> {

    /**
     * {@code tipoParcela} y {@code patronNombreDifunto} son opcionales:
     * {@code null} desactiva el filtro correspondiente. El patrón de
     * {@code LIKE} ya viene armado (p. ej. {@code "%texto%"}) y en
     * minúsculas, calculado en {@link GestionDelCementerio#buscar}, mismo
     * criterio que {@code NormaRepository#buscar}: con el parámetro
     * {@code null}-eable directamente dentro de {@code concat}/{@code lower},
     * el driver de PostgreSQL no puede inferir su tipo y falla con
     * {@code function lower(bytea) does not exist}.
     *
     * <p>Orden alfabético por nombre del difunto, no temporal: la búsqueda
     * típica es "por apellido", como una guía telefónica, a diferencia de
     * {@code NormaRepository#buscar} (por fecha de publicación).
     */
    @Query("select s from SepulturaEntity s "
            + "where (:tipoParcela is null or s.tipoParcela = :tipoParcela) "
            + "and (:patronNombreDifunto is null or lower(s.nombreDifunto) like :patronNombreDifunto) "
            + "order by s.nombreDifunto asc")
    List<SepulturaEntity> buscar(
            @Param("tipoParcela") TipoDeParcela tipoParcela,
            @Param("patronNombreDifunto") String patronNombreDifunto);
}
