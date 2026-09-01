package ar.com.ciudaddigital.bromatologia.internal;

/**
 * No existe ningún comercio con el id indicado en este tenant. Se usa al
 * registrar o buscar inspecciones sobre un id inventado o de otro
 * municipio; mapea a {@code 404}, nunca a {@link SolicitudInvalida} (400):
 * no es un problema de la forma de la solicitud, es que ese comercio no
 * está (mismo patrón que {@code RecursoNoEncontrado} en {@code defensacivil}).
 * No revela si el comercio existe en otro municipio (ADR 0001).
 */
class ComercioNoEncontrado extends RuntimeException {

    ComercioNoEncontrado(String mensaje) {
        super(mensaje);
    }
}
