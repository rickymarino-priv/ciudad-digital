package ar.com.ciudaddigital.bromatologia.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Comercios bromatológicos del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los comercios del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ComercioBromatologicoRepository extends JpaRepository<ComercioBromatologicoEntity, Long> {

    /**
     * {@code rubro}, {@code estado} y {@code patron} son opcionales:
     * {@code null} desactiva el filtro correspondiente. {@code patron} ya
     * viene armado como patrón de {@code LIKE} (p. ej. {@code "%texto%"})
     * y en minúsculas, calculado en {@link GestionDeBromatologia#buscarComercios},
     * mismo criterio que {@code RecursoDeDefensaCivilRepository#buscar}.
     */
    @Query("select c from ComercioBromatologicoEntity c "
            + "where (:rubro is null or c.rubro = :rubro) "
            + "and (:estado is null or c.estado = :estado) "
            + "and (:patron is null or lower(c.nombre) like :patron or lower(c.direccion) like :patron) "
            + "order by c.creadoEn desc")
    List<ComercioBromatologicoEntity> buscar(
            @Param("rubro") RubroBromatologico rubro,
            @Param("estado") EstadoBromatologico estado,
            @Param("patron") String patron);
}
