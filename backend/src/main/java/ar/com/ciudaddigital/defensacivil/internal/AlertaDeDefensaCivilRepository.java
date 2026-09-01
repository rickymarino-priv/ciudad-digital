package ar.com.ciudaddigital.defensacivil.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Alertas de Defensa Civil del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las alertas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface AlertaDeDefensaCivilRepository extends JpaRepository<AlertaDeDefensaCivilEntity, Long> {

    /**
     * {@code tipo}, {@code nivel}, {@code estado} y {@code patron} son
     * opcionales: {@code null} desactiva el filtro correspondiente.
     * {@code patron} ya viene armado como patrón de {@code LIKE} (p. ej.
     * {@code "%texto%"}) y en minúsculas, calculado en
     * {@link GestionDeAlertas#buscar}, mismo criterio que
     * {@code EventoRepository#buscar} — con el parámetro {@code null}-eable
     * directamente dentro de {@code lower()}, el driver de PostgreSQL no
     * puede inferir su tipo y falla.
     */
    @Query("select a from AlertaDeDefensaCivilEntity a "
            + "where (:tipo is null or a.tipo = :tipo) "
            + "and (:nivel is null or a.nivel = :nivel) "
            + "and (:estado is null or a.estado = :estado) "
            + "and (:patron is null or lower(a.titulo) like :patron or lower(a.descripcion) like :patron) "
            + "order by a.creadoEn desc")
    List<AlertaDeDefensaCivilEntity> buscar(
            @Param("tipo") TipoDeAlerta tipo,
            @Param("nivel") NivelDeAlerta nivel,
            @Param("estado") EstadoDeAlerta estado,
            @Param("patron") String patron);
}
