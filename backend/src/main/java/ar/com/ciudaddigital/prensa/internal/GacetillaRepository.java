package ar.com.ciudaddigital.prensa.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Gacetillas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las gacetillas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface GacetillaRepository extends JpaRepository<GacetillaEntity, Long> {

    /**
     * {@code categoria} y {@code patronTitulo} son opcionales: {@code null}
     * desactiva el filtro correspondiente, en vez de exponer dos o tres
     * métodos derivados distintos para cada combinación.
     *
     * <p>{@code patronTitulo} ya viene armado como patrón de {@code LIKE}
     * (p. ej. {@code "%texto%"}) y en minúsculas, calculado en
     * {@link GestionDePrensa#buscar}, en vez de resolver
     * {@code lower(concat('%', :texto, '%'))} acá: con el parámetro
     * {@code null}-eable directamente dentro de {@code concat}/{@code lower},
     * el driver de PostgreSQL no puede inferir su tipo y falla con
     * {@code function lower(bytea) does not exist} — un problema conocido de
     * tipado de parámetros sin contexto, no de la lógica de búsqueda.
     */
    @Query("select g from GacetillaEntity g "
            + "where (:categoria is null or g.categoria = :categoria) "
            + "and (:patronTitulo is null or lower(g.titulo) like :patronTitulo) "
            + "order by g.fechaPublicacion desc")
    List<GacetillaEntity> buscar(
            @Param("categoria") CategoriaDeGacetilla categoria, @Param("patronTitulo") String patronTitulo);
}
