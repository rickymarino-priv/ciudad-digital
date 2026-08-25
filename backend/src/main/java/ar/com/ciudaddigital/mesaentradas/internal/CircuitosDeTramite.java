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
     * sin vuelta atrás. Único circuito de esta rebanada (backlog R9).
     */
    private static final CircuitoDeTramite CERTIFICADO_DOMICILIO = new CircuitoDeTramite(
            EstadoDeExpediente.INICIADO,
            new EnumMap<>(Map.of(
                    EstadoDeExpediente.INICIADO, EnumSet.of(EstadoDeExpediente.EN_REVISION),
                    EstadoDeExpediente.EN_REVISION,
                            EnumSet.of(EstadoDeExpediente.APROBADO, EstadoDeExpediente.RECHAZADO),
                    EstadoDeExpediente.APROBADO, EnumSet.noneOf(EstadoDeExpediente.class),
                    EstadoDeExpediente.RECHAZADO, EnumSet.noneOf(EstadoDeExpediente.class))));

    private static final Map<TipoDeTramite, CircuitoDeTramite> CIRCUITOS =
            Map.of(TipoDeTramite.CERTIFICADO_DOMICILIO, CERTIFICADO_DOMICILIO);

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
