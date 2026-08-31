package ar.com.ciudaddigital.turnos.internal;

/**
 * Área a la que pertenece una actividad municipal (ADR 0026 §1/§2): enum
 * cerrado, alcanza para separar las tres áreas del catálogo funcional sin
 * inventar un nomenclador más fino. Nunca salud ni un trámite
 * administrativo — eso queda fuera del alcance de este módulo.
 */
enum TipoDeActividad {
    DEPORTE,
    CULTURA,
    TURISMO
}
