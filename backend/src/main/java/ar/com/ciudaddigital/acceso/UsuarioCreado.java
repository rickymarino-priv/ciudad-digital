package ar.com.ciudaddigital.acceso;

/**
 * Se dio de alta un usuario nuevo en el municipio del request en curso
 * (ADR 0011, ADR 0013 §3).
 *
 * <p>Primera API pública de {@code acceso}: los módulos que reaccionan al
 * alta de un usuario (hoy {@code auditoria}, más adelante
 * {@code notificaciones}) declaran su propio listener sobre este evento en
 * vez de que {@code acceso} conozca de antemano quién los consume.
 *
 * <p>Lleva tanto los datos del usuario creado como los del actor —quien
 * hizo el alta—, porque no son la misma persona: un administrador crea a
 * otro usuario, no se crea a sí mismo. El actor sale de
 * {@code SecurityContextHolder}, el único lugar que sabe quién hace el
 * request (ADR 0013, alternativas consideradas).
 */
public record UsuarioCreado(
        Long idUsuario,
        String nombreUsuario,
        String emailUsuario,
        Long idActor,
        String nombreActor,
        String emailActor) {
}
