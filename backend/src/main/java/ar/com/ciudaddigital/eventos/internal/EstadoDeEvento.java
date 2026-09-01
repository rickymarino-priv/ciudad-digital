package ar.com.ciudaddigital.eventos.internal;

/**
 * Ciclo de vida fijo del estado de un evento (ADR 0030 §3): la topología
 * de transición más simple del patrón hasta ahora, un único salto sin
 * retorno.
 *
 * <pre>
 * PROGRAMADO → CANCELADO
 * </pre>
 *
 * <p>{@code CANCELADO} es terminal y no hay estado intermedio: a
 * diferencia de {@code EstadoDeEspacioVerde}/{@code EstadoDeObra}/
 * {@code EstadoDeArbol} (tres o cuatro estados, con idas y vueltas), un
 * evento no tiene un estado "a medio camino" real que documentar (ADR 0030
 * §3, §7). No se agrega un tercer estado como {@code FINALIZADO}: no hace
 * falta para esta rebanada.
 */
enum EstadoDeEvento {
    PROGRAMADO,
    CANCELADO
}
