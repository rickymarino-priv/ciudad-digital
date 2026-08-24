package ar.com.ciudaddigital.tenants.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica credenciales de la API de administración (ADR 0010).
 *
 * <p>Corre contra la base de control: el gestor de transacciones por
 * defecto ya es el de control (ver {@code ConfiguracionDePersistencia}),
 * así que no hace falta nombrarlo, a diferencia de
 * {@code acceso.internal.AutenticacionDeMunicipio}.
 */
@Service
class AutenticacionDePlataforma {

    private final UsuarioPlataformaRepository usuarios;
    private final PasswordEncoder encoder;

    /**
     * Hash de una contraseña que nadie conoce, contra el que se verifica
     * cuando el email no existe: sin esto, un email inexistente respondería
     * más rápido que uno existente, y ese tiempo alcanza para enumerar
     * quién puede operar la plataforma.
     */
    private final String hashDeRelleno;

    AutenticacionDePlataforma(UsuarioPlataformaRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.hashDeRelleno = encoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    UsuarioPlataformaAutenticado autenticar(String email, String password) {
        Optional<UsuarioPlataformaEntity> encontrado =
                usuarios.findByEmailIgnoreCase(email == null ? "" : email.trim());

        String hash = encontrado.map(UsuarioPlataformaEntity::getHashPassword).orElse(hashDeRelleno);
        boolean contrasenaCorrecta = encoder.matches(password == null ? "" : password, hash);

        UsuarioPlataformaEntity usuario = encontrado
                .filter(u -> contrasenaCorrecta && u.isActivo())
                .orElseThrow(CredencialesDePlataformaInvalidas::new);

        usuario.registrarAcceso(Instant.now());
        usuarios.save(usuario);
        return UsuarioPlataformaAutenticado.de(usuario);
    }

    @Transactional(readOnly = true)
    Optional<UsuarioPlataformaAutenticado> refrescar(Long id) {
        return usuarios.findById(id)
                .filter(UsuarioPlataformaEntity::isActivo)
                .map(UsuarioPlataformaAutenticado::de);
    }
}
