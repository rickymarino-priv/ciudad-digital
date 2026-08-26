package ar.com.ciudaddigital.multas.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
