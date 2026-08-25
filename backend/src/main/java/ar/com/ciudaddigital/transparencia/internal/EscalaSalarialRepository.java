package ar.com.ciudaddigital.transparencia.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Entradas de escala salarial del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las entradas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface EscalaSalarialRepository extends JpaRepository<EscalaSalarialEntity, Long> {

    /**
     * Mismo criterio que {@link PartidaPresupuestariaRepository#buscar}:
     * {@code anio} y {@code patronDeTexto} opcionales, patrón de
     * {@code LIKE} ya armado en minúsculas desde
     * {@link GestionDeTransparencia#buscarCargos}. El texto busca en
     * {@code area} o {@code cargo}.
     */
    @Query("select e from EscalaSalarialEntity e "
            + "where (:anio is null or e.anio = :anio) "
            + "and (:patronDeTexto is null "
            + "     or lower(e.area) like :patronDeTexto "
            + "     or lower(e.cargo) like :patronDeTexto) "
            + "order by e.anio desc, e.creadoEn desc")
    List<EscalaSalarialEntity> buscar(@Param("anio") Integer anio, @Param("patronDeTexto") String patronDeTexto);
}
