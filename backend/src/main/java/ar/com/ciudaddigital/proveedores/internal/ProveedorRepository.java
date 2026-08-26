package ar.com.ciudaddigital.proveedores.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Proveedores del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los proveedores del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar —incluida
 * la consulta pública por token de seguimiento (ADR 0017): un token real
 * de otro municipio no encuentra nada, porque la búsqueda corre contra
 * otra base— ni la unicidad de {@code cuit}, que por el mismo motivo es
 * solo dentro de esta base, no cross-tenant.
 */
interface ProveedorRepository extends JpaRepository<ProveedorEntity, Long> {

    Optional<ProveedorEntity> findByCuit(String cuit);

    List<ProveedorEntity> findAllByOrderByCreadoEnDesc();

    Optional<ProveedorEntity> findByTokenHash(String tokenHash);
}
