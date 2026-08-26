package ar.com.ciudaddigital.tasas.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Tasas del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente las tasas del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface TasaRepository extends JpaRepository<TasaEntity, Long> {

    List<TasaEntity> findByNumeroCuentaOrderByCreadoEnDesc(String numeroCuenta);

    Optional<TasaEntity> findByReferenciaExternaPago(String referenciaExternaPago);
}
