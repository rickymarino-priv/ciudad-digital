package ar.com.ciudaddigital.defensacivil.internal;

/**
 * Nivel de severidad de una alerta de Defensa Civil: no es una escala
 * inventada para este producto, es la clasificación de alertas
 * meteorológicas ya en uso público en Argentina (Servicio Meteorológico
 * Nacional), lo que evita el riesgo de inventar un criterio que un
 * municipio piloto real después contradiga (ADR 0031 §4).
 */
enum NivelDeAlerta {
    AMARILLO,
    NARANJA,
    ROJO
}
