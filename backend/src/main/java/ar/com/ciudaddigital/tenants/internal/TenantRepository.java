package ar.com.ciudaddigital.tenants.internal;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Lectura de la base de control.
 *
 * <p>Extiende {@link Repository} y no {@code JpaRepository} a propósito: en
 * R1 los tenants se siembran por migración y nada debería poder escribirlos.
 * Las operaciones de alta llegan en R2, con el módulo de aprovisionamiento.
 */
interface TenantRepository extends Repository<TenantEntity, java.util.UUID> {

    Optional<TenantEntity> findById(java.util.UUID id);

    Optional<TenantEntity> findByDominioPersonalizadoIgnoreCase(String dominioPersonalizado);

    Optional<TenantEntity> findBySubdominioIgnoreCase(String subdominio);
}
