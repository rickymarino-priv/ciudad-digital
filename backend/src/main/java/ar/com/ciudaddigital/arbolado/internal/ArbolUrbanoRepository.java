package ar.com.ciudaddigital.arbolado.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Árboles urbanos del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los árboles del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ArbolUrbanoRepository extends JpaRepository<ArbolUrbanoEntity, Long> {

    /**
     * {@code estado} y {@code patron} son opcionales: {@code null}
     * desactiva el filtro correspondiente. {@code patron} ya viene armado
     * como patrón de {@code LIKE} (p. ej. {@code "%texto%"}) y en
     * minúsculas, calculado en {@link GestionDeArbolado#buscar}, mismo
     * criterio que {@code ObraPublicaRepository#buscar} — con el parámetro
     * {@code null}-eable directamente dentro de {@code lower()}, el driver
     * de PostgreSQL no puede inferir su tipo y falla.
     */
    @Query("select a from ArbolUrbanoEntity a "
            + "where (:estado is null or a.estado = :estado) "
            + "and (:patron is null or lower(a.especie) like :patron or lower(a.ubicacion) like :patron) "
            + "order by a.creadoEn desc")
    List<ArbolUrbanoEntity> buscar(@Param("estado") EstadoDeArbol estado, @Param("patron") String patron);
}
