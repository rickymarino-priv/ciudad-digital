package ar.com.ciudaddigital.bromatologia.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Inspecciones bromatológicas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las inspecciones del municipio resuelto por el
 * {@code Host}: un {@code comercioId} de otro municipio simplemente no
 * matchea ninguna fila acá, ni falta un filtro que agregar a mano.
 */
interface InspeccionBromatologicaRepository extends JpaRepository<InspeccionBromatologicaEntity, Long> {

    List<InspeccionBromatologicaEntity> findByComercioIdOrderByFechaDesc(Long comercioId);

    /**
     * Conteo agregado por resultado, para
     * {@code FuenteDeMetricasDeBromatologia} (ADR 0034 §3). Un resultado
     * sin ninguna inspección no aparece en el resultado: no se rellena con
     * cero.
     */
    @Query("select i.resultado as etiqueta, count(i) as cantidad from InspeccionBromatologicaEntity i "
            + "group by i.resultado order by i.resultado asc")
    List<ConteoPorEtiqueta> contarPorResultado();

    interface ConteoPorEtiqueta {
        String getEtiqueta();

        long getCantidad();
    }
}
