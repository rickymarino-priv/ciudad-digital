package ar.com.ciudaddigital.acceso.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifica credenciales contra los usuarios del municipio del request en
 * curso (ADR 0010).
 *
 * <p>No hay parámetro de municipio en ningún lado: el repositorio va contra
 * la base del tenant resuelto, así que las credenciales de otro municipio
 * no es que se rechacen, es que no existen acá.
 */
@Service
class AutenticacionDeMunicipio {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;

    /**
     * Hash de una contraseña que nadie conoce, contra el que se verifica
     * cuando el email no existe.
     *
     * <p>Sin esto, un email inexistente responde mucho más rápido que uno
     * existente —no llega a calcular bcrypt— y ese tiempo alcanza para
     * enumerar qué usuarios tiene el municipio.
     */
    private final String hashDeRelleno;

    AutenticacionDeMunicipio(UsuarioRepository usuarios, PasswordEncoder encoder) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.hashDeRelleno = encoder.encode(UUID.randomUUID().toString());
    }

    /*
     * El gestor de transacciones por defecto es el de la base de control:
     * sin nombrar el del tenant, la escritura de último acceso iría a la
     * base equivocada.
     */
    @Transactional("tenantTransactionManager")
    UsuarioAutenticado autenticar(String email, String password) {
        Optional<UsuarioEntity> encontrado = usuarios.findByEmailIgnoreCase(normalizar(email));

        String hash = encontrado.map(UsuarioEntity::getHashPassword).orElse(hashDeRelleno);
        boolean contrasenaCorrecta = encoder.matches(password == null ? "" : password, hash);

        UsuarioEntity usuario = encontrado
                .filter(u -> contrasenaCorrecta && u.isActivo())
                .orElseThrow(CredencialesInvalidas::new);

        usuario.registrarAcceso(Instant.now());
        return UsuarioAutenticado.de(usuario);
    }

    /** Datos frescos del usuario de una sesión ya abierta. */
    @Transactional(value = "tenantTransactionManager", readOnly = true)
    Optional<UsuarioAutenticado> refrescar(Long idDeUsuario) {
        return usuarios.findById(idDeUsuario)
                .filter(UsuarioEntity::isActivo)
                .map(UsuarioAutenticado::de);
    }

    private String normalizar(String email) {
        return email == null ? "" : email.trim();
    }
}
