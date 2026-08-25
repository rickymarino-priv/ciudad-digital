package ar.com.ciudaddigital.mesaentradas.internal;

/**
 * Agrupa los campos propios de los tres tipos de trámite (ADR 0016), para
 * no explotar la firma de {@code ExpedienteEntity.nuevo(...)} /
 * {@code GestionDeExpedientes.iniciar(...)} a un parámetro por campo.
 *
 * <p>Todos los componentes son nullable: cada {@link TipoDeTramite} usa
 * solo los suyos ({@code domicilioACertificar} para
 * {@code CERTIFICADO_DOMICILIO}; {@code rubroComercial}/
 * {@code direccionLocal} para {@code HABILITACION_COMERCIAL_SIMPLE};
 * {@code direccionObra}/{@code descripcionObra} para
 * {@code PERMISO_OBRA_MENOR}), y quién exige cuáles son obligatorios es
 * {@code GestionDeExpedientes.iniciar}, no este record — mismo criterio
 * que columnas explícitas nullable en base, con el {@code check} que las
 * exige solo para su tipo (ADR 0016).
 */
record DatosPropiosDelTramite(
        String domicilioACertificar,
        String rubroComercial,
        String direccionLocal,
        String direccionObra,
        String descripcionObra) {
}
