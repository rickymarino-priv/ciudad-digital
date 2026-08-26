package ar.com.ciudaddigital.proveedores.internal;

/**
 * Rubro fijo elegido por quien se registra como proveedor, mismo criterio
 * que {@code CategoriaReclamo} de {@code reclamos}: no hay catálogo de
 * rubros configurable por municipio en esta rebanada.
 */
enum RubroProveedor {
    CONSTRUCCION,
    SERVICIOS,
    INSUMOS_Y_SUMINISTROS,
    PROFESIONALES,
    TECNOLOGIA,
    OTRO
}
