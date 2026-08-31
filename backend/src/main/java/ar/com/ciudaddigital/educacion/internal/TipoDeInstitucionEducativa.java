package ar.com.ciudaddigital.educacion.internal;

/**
 * Clasificación de una institución educativa municipal, acotada a la
 * competencia municipal real en educación en Argentina (ADR 0028 §3): a
 * propósito, no incluye {@code ESCUELA_PRIMARIA} ni
 * {@code ESCUELA_SECUNDARIA}, competencia provincial que la enorme mayoría
 * de los municipios no tiene.
 */
enum TipoDeInstitucionEducativa {
    JARDIN_MATERNAL,
    JARDIN_DE_INFANTES,
    CENTRO_DE_FORMACION_PROFESIONAL,
    OTRA
}
