package ar.com.ciudaddigital.boletin.internal;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Normas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las normas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface NormaRepository extends JpaRepository<NormaEntity, Long> {

    /**
     * {@code tipo} y {@code patronTitulo} son opcionales: {@code null}
     * desactiva el filtro correspondiente, en vez de exponer dos o tres
     * métodos derivados distintos para cada combinación.
     *
     * <p>{@code patronTitulo} ya viene armado como patrón de {@code LIKE}
     * (p. ej. {@code "%texto%"}) y en minúsculas, calculado en
     * {@link GestionDelBoletin#buscar}, en vez de resolver
     * {@code lower(concat('%', :texto, '%'))} acá: con el parámetro
     * {@code null}-eable directamente dentro de {@code concat}/{@code lower},
     * el driver de PostgreSQL no puede inferir su tipo y falla con
     * {@code function lower(bytea) does not exist} — un problema conocido de
     * tipado de parámetros sin contexto, no de la lógica de búsqueda.
     */
    @Query("select n from NormaEntity n "
            + "where (:tipo is null or n.tipo = :tipo) "
            + "and (:patronTitulo is null or lower(n.titulo) like :patronTitulo) "
            + "order by n.fechaPublicacion desc")
    List<NormaEntity> buscar(@Param("tipo") TipoDeNorma tipo, @Param("patronTitulo") String patronTitulo);
}
