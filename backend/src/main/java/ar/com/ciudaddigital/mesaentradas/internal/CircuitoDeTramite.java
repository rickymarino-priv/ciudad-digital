package ar.com.ciudaddigital.mesaentradas.internal;

import java.util.Map;
import java.util.Set;

/**
 * Circuito de estados de un trámite: estado inicial y transiciones
 * válidas, sin conocer a qué {@link TipoDeTramite} pertenece (ADR 0015
 * §1) — esa asociación vive en {@link CircuitosDeTramite}, no acá, para
 * que esta clase sea una pieza reutilizable entre tipos de trámite.
 *
 * <p>Mismo estilo {@code EnumMap}/{@code EnumSet} que
 * {@code GestionDeReclamos.TRANSICIONES_VALIDAS}, elevado a su propio tipo
 * porque acá hay más de un circuito, uno por tipo de trámite.
 */
record CircuitoDeTramite(
        EstadoDeExpediente estadoInicial, Map<EstadoDeExpediente, Set<EstadoDeExpediente>> transicionesValidas) {
}
