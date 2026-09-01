package ar.com.ciudaddigital.bromatologia.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
