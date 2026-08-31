package ar.com.ciudaddigital.desarrollosocial.internal;

/**
 * Categorías amplias de elegibilidad autodeclaradas por el vecino, nunca
 * un monto de ingreso ni un comprobante (ADR 0025 §4): mismo criterio que
 * ADR 0019 nunca vincula un sueldo a una identidad, aplicado acá a la
 * elegibilidad.
 */
enum SituacionDeclarada {
    DESOCUPADO,
    EMPLEO_INFORMAL,
    EMPLEO_FORMAL,
    JUBILADO_O_PENSIONADO,
    OTRO
}
