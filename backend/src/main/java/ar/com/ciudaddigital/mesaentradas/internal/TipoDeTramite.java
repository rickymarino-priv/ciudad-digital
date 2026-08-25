package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * Catálogo de trámites que Mesa de Entradas sabe tramitar (ADR 0015 §1).
 *
 * <p>Agregar un tipo de trámite nuevo es agregar un valor acá, su
 * {@link CircuitoDeTramite} propio registrado en {@link CircuitosDeTramite},
 * y sus campos propios agrupados en {@link DatosPropiosDelTramite}
 * (ADR 0016). El <b>avance de estado</b> es agnóstico al tipo de trámite y
 * no toca el motor ({@code ExpedienteEntity}, {@code
 * MovimientoDeExpedienteEntity}, {@link GestionDeExpedientes#avanzar}). El
 * <b>alta</b> tampoco goza de un desacople total —agregar un tipo sigue
 * tocando {@link DatosPropiosDelTramite}, el controller y el frontend—,
 * pero desde ADR 0016 ya no expone un único campo hardcodeado
 * ({@code domicilioACertificar}) como parámetro explícito de {@link
 * GestionDeExpedientes#iniciar}: los datos propios de los tres tipos
 * viajan agrupados en ese record, con columnas explícitas nullable en
 * {@code expediente} y un {@code check} que exige las que corresponden a
 * cada tipo.
 */
enum TipoDeTramite {
    CERTIFICADO_DOMICILIO,
    HABILITACION_COMERCIAL_SIMPLE,
    PERMISO_OBRA_MENOR
}
