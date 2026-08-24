package ar.com.ciudaddigital.acceso.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Usuarios del municipio del request en curso.
 *
 * <p>Va contra el datasource ruteado por tenant (ADR 0001), así que cada
 * consulta ve únicamente los usuarios del municipio resuelto por el
 * {@code Host}. No hay filtro por tenant que se pueda olvidar.
 */
interface UsuarioRepository extends JpaRepository<UsuarioEntity, Long> {

    /**
     * Busca por email sin distinguir mayúsculas, igual que el índice único
     * de la tabla: si la búsqueda fuera sensible a mayúsculas, un usuario
     * podría no poder entrar con el mismo email con el que fue dado de alta.
     */
    Optional<UsuarioEntity> findByEmailIgnoreCase(String email);
}
