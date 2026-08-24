package ar.com.ciudaddigital.acceso.internal;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.com.ciudaddigital.acceso.UsuarioCreado;

/**
 * Alta y edición de los usuarios del municipio del request en curso
 * (ADR 0011).
 *
 * <p>No hay borrado: un usuario que deja el municipio se desactiva, no se
 * elimina — perder el registro de que existió complicaría cualquier
 * auditoría futura (R5) sobre qué hizo mientras estuvo activo.
 */
@Service
class AdministracionDeUsuarios {

    private static final Pattern EMAIL_VALIDO =
            Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private static final int LARGO_MINIMO_DE_PASSWORD = 12;

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher eventos;

    AdministracionDeUsuarios(UsuarioRepository usuarios, RolRepository roles,
            PasswordEncoder encoder, ApplicationEventPublisher eventos) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.encoder = encoder;
        this.eventos = eventos;
    }

    @Transactional("tenantTransactionManager")
    UsuarioEntity crear(String nombre, String email, String password, Set<Long> idsDeRoles) {
        String emailNormalizado = validarNombreYEmail(nombre, email);
        if (password == null || password.length() < LARGO_MINIMO_DE_PASSWORD) {
            throw new SolicitudInvalida(
                    "La contraseña tiene que tener al menos "
                            + LARGO_MINIMO_DE_PASSWORD + " caracteres.");
        }
        if (usuarios.existsByEmailIgnoreCase(emailNormalizado)) {
            throw new SolicitudInvalida(
                    "Ya hay un usuario con el correo " + emailNormalizado + " en este municipio.");
        }

        UsuarioEntity usuario =
                UsuarioEntity.nuevo(nombre.trim(), emailNormalizado, encoder.encode(password));
        usuario.asignarRoles(Set.copyOf(resolverRoles(idsDeRoles)));
        usuario = usuarios.save(usuario);

        // Todavía dentro de la transacción: @TransactionalEventListener(AFTER_COMMIT)
        // descarta por defecto los eventos publicados fuera de una
        // transacción en curso (ADR 0013 §2).
        eventos.publishEvent(eventoDeAlta(usuario));

        return usuario;
    }

    @Transactional("tenantTransactionManager")
    UsuarioEntity editar(Long id, String nombre, boolean activo, Set<Long> idsDeRoles) {
        UsuarioEntity usuario = usuarios.findById(id)
                .orElseThrow(() -> new SolicitudInvalida("No existe el usuario " + id + "."));

        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del usuario.");
        }

        usuario.renombrar(nombre.trim());
        usuario.asignarRoles(Set.copyOf(resolverRoles(idsDeRoles)));
        if (activo) {
            usuario.activar();
        } else {
            usuario.desactivar();
        }
        return usuarios.save(usuario);
    }

    /**
     * El actor de la creación no es el usuario creado: es quien tiene la
     * sesión abierta y ejecutó el alta. Solo {@code SecurityContextHolder}
     * sabe quién hace el request (ADR 0013, alternativas consideradas).
     */
    private UsuarioCreado eventoDeAlta(UsuarioEntity usuarioCreado) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        UsuarioAutenticado actor = (UsuarioAutenticado) autenticacion.getPrincipal();

        return new UsuarioCreado(
                usuarioCreado.getId(), usuarioCreado.getNombre(), usuarioCreado.getEmail(),
                actor.id(), actor.nombre(), actor.email());
    }

    private String validarNombreYEmail(String nombre, String email) {
        if (nombre == null || nombre.isBlank()) {
            throw new SolicitudInvalida("Hay que indicar el nombre del usuario.");
        }
        if (email == null || !EMAIL_VALIDO.matcher(email.trim()).matches()) {
            throw new SolicitudInvalida("El correo electrónico no es válido.");
        }
        return email.trim();
    }

    private List<RolEntity> resolverRoles(Set<Long> idsDeRoles) {
        Set<Long> ids = idsDeRoles == null ? Set.of() : idsDeRoles;
        List<RolEntity> encontrados = roles.findByIdIn(ids);
        if (encontrados.size() != ids.size()) {
            throw new SolicitudInvalida("Alguno de los roles indicados no existe.");
        }
        return encontrados;
    }
}
