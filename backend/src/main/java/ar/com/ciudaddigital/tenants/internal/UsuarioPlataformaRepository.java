package ar.com.ciudaddigital.tenants.internal;

import java.util.Optional;

import org.springframework.data.repository.Repository;

/**
 * Acceso a los usuarios de plataforma, en la base de control.
 *
 * <p>Extiende {@link Repository} y no {@code JpaRepository} a propósito,
 * igual que {@link TenantRepository}: no hay borrado, solo lo que este
 * módulo necesita.
 */
interface UsuarioPlataformaRepository extends Repository<UsuarioPlataformaEntity, Long> {

    UsuarioPlataformaEntity save(UsuarioPlataformaEntity usuario);

    Optional<UsuarioPlataformaEntity> findById(Long id);

    Optional<UsuarioPlataformaEntity> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long count();
}
