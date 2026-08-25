package ar.com.ciudaddigital.reclamos.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Reclamos del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los reclamos del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ReclamoRepository extends JpaRepository<ReclamoEntity, Long> {

    List<ReclamoEntity> findAllByOrderByCreadoEnDesc();
}
