package ar.com.ciudaddigital.tenants.internal;

/**
 * Visibilidad manual del estado de cuenta de un municipio (ADR 0019). Se
 * edita a mano desde la consola del proveedor; nada en el sistema lo cambia
 * solo, y no interactúa con el entitlement de módulos (ADR 0009).
 */
enum EstadoFacturacion {

    AL_DIA,
    ATRASADO;
}
