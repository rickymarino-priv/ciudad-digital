package ar.com.ciudaddigital.defensacivil.internal;

/**
 * Vigencia de una alerta de Defensa Civil (ADR 0031 §4): único salto sin
 * retorno, {@code VIGENTE → FINALIZADA}, misma topología de un solo
 * escalón que {@code EstadoDeEvento} (ADR 0030 §"decide también una
 * topología de estado nueva"). Una alerta finalizada no vuelve a estar
 * vigente: si la situación se repite, el municipio publica una alerta
 * nueva.
 */
enum EstadoDeAlerta {
    VIGENTE,
    FINALIZADA
}
