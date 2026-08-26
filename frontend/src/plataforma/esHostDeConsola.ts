/**
 * Decide si el host actual corresponde a la consola del proveedor
 * (cross-tenant, ADR 0019) en vez de a un portal municipal.
 *
 * En desarrollo el host es exactamente `admin.localhost`; en producción va
 * a ser un subdominio de administración sobre el dominio real. No se
 * hardcodea ningún dominio específico: alcanza con que el host empiece con
 * `admin.` para cubrir ambos casos.
 */
export function esHostDeConsola(hostname: string): boolean {
  return hostname === 'admin.localhost' || hostname.startsWith('admin.')
}
