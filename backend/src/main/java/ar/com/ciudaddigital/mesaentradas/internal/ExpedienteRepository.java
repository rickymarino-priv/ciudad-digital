package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Expedientes del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los expedientes del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar —incluida la
 * consulta pública por token de seguimiento (ADR 0017): un token real de
 * otro municipio no encuentra nada, porque la búsqueda corre contra otra
 * base.
 */
interface ExpedienteRepository extends JpaRepository<ExpedienteEntity, Long> {

    List<ExpedienteEntity> findAllByOrderByCreadoEnDesc();

    Optional<ExpedienteEntity> findByTokenHash(String tokenHash);

    /**
     * Conteo agregado por tipo de trámite, para
     * {@code FuenteDeMetricasDeMesaEntradas} (ADR 0033 §3). Un tipo sin
     * ningún expediente no aparece en el resultado: no se rellena con cero.
     */
    @Query("select e.tipo as etiqueta, count(e) as cantidad from ExpedienteEntity e "
            + "group by e.tipo order by e.tipo asc")
    List<ConteoPorEtiqueta> contarPorTipo();

    /**
     * Conteo agregado por estado, mismo criterio que {@link #contarPorTipo()}.
     */
    @Query("select e.estado as etiqueta, count(e) as cantidad from ExpedienteEntity e "
            + "group by e.estado order by e.estado asc")
    List<ConteoPorEtiqueta> contarPorEstado();

    interface ConteoPorEtiqueta {
        String getEtiqueta();

        long getCantidad();
    }
}
