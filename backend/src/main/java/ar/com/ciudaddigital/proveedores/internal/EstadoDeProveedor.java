package ar.com.ciudaddigital.proveedores.internal;

/**
 * Ciclo de vida fijo del registro de un proveedor: a diferencia de
 * {@code EstadoReclamo} (4 estados, con "en proceso" intermedio), acá
 * alcanza con 3, porque la revisión de un proveedor es una única decisión
 * del municipio, no un ciclo con pasos intermedios que valga la pena
 * reflejar en el estado. Transiciones válidas codificadas en
 * {@link GestionDeProveedores}, no acá (ADR 0014 §3).
 */
enum EstadoDeProveedor {
    PENDIENTE,
    APROBADO,
    RECHAZADO
}
