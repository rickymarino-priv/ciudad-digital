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
 * <p>Corre contra la base de control, nombrada explícitamente (ADR 0013
 * §1): el gestor de transacciones por defecto pasó a ser el de tenant
 * desde R5, porque el registro persistente de eventos de Spring Modulith
 * —código de terceros que no conoce las dos unidades de persistencia de
 * este proyecto— siempre usa el que esté marcado {@code @Primary}, y ese
 * mecanismo tiene que escribir en la base del municipio (ver
 * {@code ConfiguracionDePersistencia}).
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

    @Transactional("controlTransactionManager")
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

    @Transactional(transactionManager = "controlTransactionManager", readOnly = true)
    Optional<UsuarioPlataformaAutenticado> refrescar(Long id) {
        return usuarios.findById(id)
                .filter(UsuarioPlataformaEntity::isActivo)
                .map(UsuarioPlataformaAutenticado::de);
    }
}
