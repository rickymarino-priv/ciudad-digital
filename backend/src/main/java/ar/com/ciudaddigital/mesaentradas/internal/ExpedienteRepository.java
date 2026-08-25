package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Expedientes del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los expedientes del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface ExpedienteRepository extends JpaRepository<ExpedienteEntity, Long> {

    List<ExpedienteEntity> findAllByOrderByCreadoEnDesc();
}
