package ar.com.ciudaddigital.multas.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Multas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las multas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface MultaRepository extends JpaRepository<MultaEntity, Long> {

    List<MultaEntity> findByPatenteOrderByNotificadaEnDesc(String patente);

    List<MultaEntity> findByDniOrderByNotificadaEnDesc(String dni);

    List<MultaEntity> findAllByOrderByNotificadaEnDesc();

    Optional<MultaEntity> findByReferenciaExternaPago(String referenciaExternaPago);

    /**
     * Conteo agregado por estado, para {@code FuenteDeMetricasDeMultas}
     * (ADR 0034 §3). Un estado sin ninguna multa no aparece en el
     * resultado: no se rellena con cero.
     */
    @Query("select m.estado as etiqueta, count(m) as cantidad from MultaEntity m "
            + "group by m.estado order by m.estado asc")
    List<ConteoPorEtiqueta> contarPorEstado();

    interface ConteoPorEtiqueta {
        String getEtiqueta();

        long getCantidad();
    }
}
