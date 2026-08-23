import { useEffect, useState } from 'react'

import { aplicarTema, type TenantTema } from './tema'

export type EstadoTenant =
  | { estado: 'cargando' }
  | { estado: 'listo'; tenant: TenantTema }
  | { estado: 'error'; mensaje: string }

/**
 * Carga la identidad del municipio al arrancar y aplica su tema.
 *
 * El municipio se deduce del host en el backend: el frontend no lo elige ni
 * lo manda como parámetro, para que no exista forma de pedir la marca (ni,
 * más adelante, los datos) de otro municipio.
 */
export function useTenant(): EstadoTenant {
  const [estado, setEstado] = useState<EstadoTenant>({ estado: 'cargando' })

  useEffect(() => {
    const abort = new AbortController()

    fetch('/api/tenant/tema', { signal: abort.signal })
      .then(async (respuesta) => {
        if (respuesta.status === 404) {
          throw new Error('No hay ningún municipio publicado en esta dirección.')
        }
        if (respuesta.status === 503) {
          throw new Error('El portal de este municipio no está disponible en este momento.')
        }
        if (!respuesta.ok) {
          throw new Error('No se pudo cargar el portal del municipio.')
        }
        return (await respuesta.json()) as TenantTema
      })
      .then((tenant) => {
        if (tenant.tema) {
          aplicarTema(tenant.tema)
        }
        document.title = `Portal de ${tenant.nombreMunicipio}`
        setEstado({ estado: 'listo', tenant })
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setEstado({
          estado: 'error',
          mensaje: error instanceof Error ? error.message : 'Error inesperado.',
        })
      })

    return () => abort.abort()
  }, [])

  return estado
}
