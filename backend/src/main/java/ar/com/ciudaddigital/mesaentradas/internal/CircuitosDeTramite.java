package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;

/**
 * El motor propiamente dicho (ADR 0015 §1): registro estático de qué
 * {@link CircuitoDeTramite} le corresponde a cada {@link TipoDeTramite}.
 * {@code GestionDeExpedientes} usa {@link #de(TipoDeTramite)} para validar
 * cualquier transición sin conocer el circuito concreto.
 *
 * <p>Agregar un tipo de trámite es agregar una entrada acá, nunca tocar la
 * lógica de {@code GestionDeExpedientes}.
 */
final class CircuitosDeTramite {

    /**
     * Certificado de domicilio: iniciado → en revisión → aprobado/rechazado,
     * sin vuelta atrás. Único circuito de la rebanada R9; permiso de obra
     * menor (R10, backlog CD-18) reutiliza este mismo circuito, ver más
     * abajo.
     */
    private static final CircuitoDeTramite CERTIFICADO_DOMICILIO = new CircuitoDeTramite(
            EstadoDeExpediente.INICIADO,
            new EnumMap<>(Map.of(
                    EstadoDeExpediente.INICIADO, EnumSet.of(EstadoDeExpediente.EN_REVISION),
                    EstadoDeExpediente.EN_REVISION,
                            EnumSet.of(EstadoDeExpediente.APROBADO, EstadoDeExpediente.RECHAZADO),
                    EstadoDeExpediente.APROBADO, EnumSet.noneOf(EstadoDeExpediente.class),
                    EstadoDeExpediente.RECHAZADO, EnumSet.noneOf(EstadoDeExpediente.class))));

    /**
     * Habilitación comercial simple (backlog R10, ADR 0016): un paso más
     * que certificado de domicilio, {@code INSPECCION} entre la revisión y
     * la resolución final, aunque también se puede rechazar directo desde
     * revisión sin pasar por inspección.
     */
    private static final CircuitoDeTramite HABILITACION_COMERCIAL_SIMPLE = new CircuitoDeTramite(
            EstadoDeExpediente.INICIADO,
            new EnumMap<>(Map.of(
                    EstadoDeExpediente.INICIADO, EnumSet.of(EstadoDeExpediente.EN_REVISION),
                    EstadoDeExpediente.EN_REVISION,
                            EnumSet.of(EstadoDeExpediente.INSPECCION, EstadoDeExpediente.RECHAZADO),
                    EstadoDeExpediente.INSPECCION,
                            EnumSet.of(EstadoDeExpediente.APROBADO, EstadoDeExpediente.RECHAZADO),
                    EstadoDeExpediente.APROBADO, EnumSet.noneOf(EstadoDeExpediente.class),
                    EstadoDeExpediente.RECHAZADO, EnumSet.noneOf(EstadoDeExpediente.class))));

    /**
     * Permiso de obra menor (backlog R10): mismo circuito que certificado
     * de domicilio, sin necesidad de un paso adicional solo para que se
     * vea distinto.
     */
    private static final CircuitoDeTramite PERMISO_OBRA_MENOR = CERTIFICADO_DOMICILIO;

    private static final Map<TipoDeTramite, CircuitoDeTramite> CIRCUITOS = Map.of(
            TipoDeTramite.CERTIFICADO_DOMICILIO, CERTIFICADO_DOMICILIO,
            TipoDeTramite.HABILITACION_COMERCIAL_SIMPLE, HABILITACION_COMERCIAL_SIMPLE,
            TipoDeTramite.PERMISO_OBRA_MENOR, PERMISO_OBRA_MENOR);

    private CircuitosDeTramite() {
    }

    static CircuitoDeTramite de(TipoDeTramite tipo) {
        CircuitoDeTramite circuito = CIRCUITOS.get(tipo);
        if (circuito == null) {
            // No debería pasar con un TipoDeTramite válido: significaría que
            // se agregó un valor al enum sin registrar su circuito acá
            // (ADR 0015 §1), un error de código, no una solicitud inválida.
            throw new IllegalStateException("No hay un circuito registrado para el tipo de trámite " + tipo + ".");
        }
        return circuito;
    }
}
