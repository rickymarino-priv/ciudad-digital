package ar.com.ciudaddigital.obras.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Obras públicas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las obras del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ObraPublicaRepository extends JpaRepository<ObraPublicaEntity, Long> {

    /**
     * {@code estado}, {@code tipo} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y
     * en minúsculas, calculado en {@link GestionDeObras#buscar}, mismo
     * criterio que {@code NormaRepository#buscar} — con el parámetro
     * {@code null}-eable directamente dentro de {@code lower()}, el driver
     * de PostgreSQL no puede inferir su tipo y falla.
     */
    @Query("select o from ObraPublicaEntity o "
            + "where (:estado is null or o.estado = :estado) "
            + "and (:tipo is null or o.tipo = :tipo) "
            + "and (:patron is null or lower(o.nombre) like :patron or lower(o.ubicacion) like :patron) "
            + "order by o.creadoEn desc")
    List<ObraPublicaEntity> buscar(
            @Param("estado") EstadoDeObra estado, @Param("tipo") TipoDeObra tipo, @Param("patron") String patron);
}
