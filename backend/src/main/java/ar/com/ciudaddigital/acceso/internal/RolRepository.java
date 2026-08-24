package ar.com.ciudaddigital.acceso.internal;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

/** Roles del municipio del request en curso (ADR 0011). */
interface RolRepository extends JpaRepository<RolEntity, Long> {

    List<RolEntity> findAllByOrderByNombreAsc();

    List<RolEntity> findByIdIn(Set<Long> ids);

    Optional<RolEntity> findByCodigoIgnoreCase(String codigo);

    boolean existsByCodigoIgnoreCase(String codigo);
}
