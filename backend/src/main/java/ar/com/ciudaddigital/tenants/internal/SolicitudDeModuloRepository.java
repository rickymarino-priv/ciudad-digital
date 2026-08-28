package ar.com.ciudaddigital.tenants.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Acceso a {@code solicitud_modulo}, en la base de control.
 *
 * <p>Todas las consultas filtran por {@code tenantId}: es la única barrera
 * de aislamiento entre municipios para esta tabla, que —a diferencia de las
 * entidades de {@code municipio}— vive en una base compartida entre todos
 * los tenants (ADR 0022 §2).
 */
interface SolicitudDeModuloRepository extends JpaRepository<SolicitudDeModuloEntity, Long> {

    List<SolicitudDeModuloEntity> findByTenantIdOrderByCreadaEnDesc(UUID tenantId);

    long countByTenantIdAndEstado(UUID tenantId, EstadoDeSolicitudDeModulo estado);

    Optional<SolicitudDeModuloEntity> findByIdAndTenantId(Long id, UUID tenantId);
}
