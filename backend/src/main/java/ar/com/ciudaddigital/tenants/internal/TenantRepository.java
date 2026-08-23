package ar.com.ciudaddigital.tenants.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Acceso a la base de control.
 *
 * <p>Extiende {@link Repository} y no {@code JpaRepository} para exponer
 * solo lo que el módulo necesita: no hay borrado de municipios, que sería
 * una operación destructiva sin caso de uso todavía.
 */
interface TenantRepository extends Repository<TenantEntity, UUID> {

    TenantEntity save(TenantEntity tenant);

    Optional<TenantEntity> findById(UUID id);

    List<TenantEntity> findAllByOrderBySlugAsc();

    Optional<TenantEntity> findByDominioPersonalizadoIgnoreCase(String dominioPersonalizado);

    Optional<TenantEntity> findBySubdominioIgnoreCase(String subdominio);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySubdominioIgnoreCase(String subdominio);
}
