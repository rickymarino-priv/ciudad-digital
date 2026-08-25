package ar.com.ciudaddigital.transparencia.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Partidas presupuestarias del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las partidas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface PartidaPresupuestariaRepository extends JpaRepository<PartidaPresupuestariaEntity, Long> {

    /**
     * {@code anio} y {@code patronDeTexto} son opcionales: {@code null}
     * desactiva el filtro correspondiente. El patrón de {@code LIKE} ya
     * viene armado (p. ej. {@code "%texto%"}) y en minúsculas, calculado en
     * {@link GestionDeTransparencia#buscarPartidas}, mismo criterio que
     * {@code NormaRepository#buscar}: con el parámetro {@code null}-eable
     * directamente dentro de {@code concat}/{@code lower}, el driver de
     * PostgreSQL no puede inferir su tipo y falla con {@code function
     * lower(bytea) does not exist}. El texto busca en {@code area} o
     * {@code concepto}.
     */
    @Query("select p from PartidaPresupuestariaEntity p "
            + "where (:anio is null or p.anio = :anio) "
            + "and (:patronDeTexto is null "
            + "     or lower(p.area) like :patronDeTexto "
            + "     or lower(p.concepto) like :patronDeTexto) "
            + "order by p.anio desc, p.creadoEn desc")
    List<PartidaPresupuestariaEntity> buscar(@Param("anio") Integer anio, @Param("patronDeTexto") String patronDeTexto);
}
