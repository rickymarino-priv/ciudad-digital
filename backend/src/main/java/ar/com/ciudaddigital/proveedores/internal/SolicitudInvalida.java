package ar.com.ciudaddigital.proveedores.internal;

/** El registro de proveedor, o el cambio de estado pedido, no es válido. */
class SolicitudInvalida extends RuntimeException {

    SolicitudInvalida(String mensaje) {
        super(mensaje);
    }
}
