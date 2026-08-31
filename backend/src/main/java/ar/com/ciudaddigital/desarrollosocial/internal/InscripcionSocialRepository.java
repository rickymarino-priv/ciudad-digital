package ar.com.ciudaddigital.desarrollosocial.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Inscripciones a programas sociales del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las inscripciones del municipio resuelto por el
 * {@code Host} — incluida la consulta pública por token de seguimiento
 * (ADR 0017): un token real de otro municipio no encuentra nada, porque
 * la búsqueda corre contra otra base.
 */
interface InscripcionSocialRepository extends JpaRepository<InscripcionSocialEntity, Long> {

    Optional<InscripcionSocialEntity> findByTokenHash(String tokenHash);

    /**
     * {@code programaId} y {@code estado} son opcionales: {@code null}
     * desactiva el filtro correspondiente. Sin lectura pública equivalente
     * (ADR 0025 §6): esta consulta solo la usa
     * {@link GestionDeInscripcionesSociales#listarParaGestion}, detrás del
     * permiso {@code desarrollosocial.revisarInscripciones}.
     */
    @Query("select i from InscripcionSocialEntity i "
            + "where (:programaId is null or i.programaId = :programaId) "
            + "and (:estado is null or i.estado = :estado) "
            + "order by i.creadoEn desc")
    List<InscripcionSocialEntity> listarParaGestion(
            @Param("programaId") Long programaId, @Param("estado") EstadoDeInscripcion estado);
}
