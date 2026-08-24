package ar.com.ciudaddigital.acceso.internal;

import java.util.List;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Catálogo de permisos disponibles (ADR 0011).
 *
 * <p>Es de solo lectura desde este módulo: el catálogo lo define el
 * sistema por migración, ningún municipio lo edita.
 */
interface PermisoRepository extends JpaRepository<PermisoEntity, String> {

    List<PermisoEntity> findAllByOrderByAreaAscModuloAscAccionAsc();

    List<PermisoEntity> findByCodigoIn(Set<String> codigos);
}
