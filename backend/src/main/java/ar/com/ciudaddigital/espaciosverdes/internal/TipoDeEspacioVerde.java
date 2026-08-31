package ar.com.ciudaddigital.espaciosverdes.internal;

/**
 * Clasificación de un espacio verde municipal, fija e igual para todos los
 * municipios (ADR 0029 §3): conjunto chico y estable (plaza, parque,
 * paseo) más una salida genérica para no bloquear un caso real que no
 * encaje, mismo criterio que {@code TipoDeInstitucionEducativa}, no el de
 * {@code especie} en Arbolado (texto libre, sin catálogo cerrado posible).
 */
enum TipoDeEspacioVerde {
    PLAZA,
    PARQUE,
    PASEO,
    OTRA
}
