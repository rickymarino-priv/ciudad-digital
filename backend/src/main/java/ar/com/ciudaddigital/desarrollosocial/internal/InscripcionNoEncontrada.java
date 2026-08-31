package ar.com.ciudaddigital.desarrollosocial.internal;

/**
 * No existe ninguna inscripción con el id indicado. Se usa al cambiar el
 * estado de un id inventado o de otro municipio; mapea a {@code 404},
 * nunca a {@link SolicitudInvalida} (400) — mismo patrón que
 * {@code ProgramaNoEncontrado}/{@code ObraNoEncontrada}.
 */
class InscripcionNoEncontrada extends RuntimeException {

    InscripcionNoEncontrada(String mensaje) {
        super(mensaje);
    }
}
