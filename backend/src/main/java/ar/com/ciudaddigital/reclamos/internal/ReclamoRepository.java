package ar.com.ciudaddigital.reclamos.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Reclamos del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los reclamos del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar —incluida la
 * consulta pública por token de seguimiento (ADR 0017): un token real de
 * otro municipio no encuentra nada, porque la búsqueda corre contra otra
 * base.
 */
interface ReclamoRepository extends JpaRepository<ReclamoEntity, Long> {

    List<ReclamoEntity> findAllByOrderByCreadoEnDesc();

    Optional<ReclamoEntity> findByTokenHash(String tokenHash);

    /**
     * Conteo agregado por estado, para {@code FuenteDeMetricasDeReclamos}
     * (ADR 0033 §3). Un estado sin ningún reclamo no aparece en el
     * resultado: no se rellena con cero.
     */
    @Query("select r.estado as etiqueta, count(r) as cantidad from ReclamoEntity r "
            + "group by r.estado order by r.estado asc")
    List<ConteoPorEtiqueta> contarPorEstado();

    interface ConteoPorEtiqueta {
        String getEtiqueta();

        long getCantidad();
    }
}
