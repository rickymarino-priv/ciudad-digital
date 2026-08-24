import { useEffect, useState } from 'react'

import { pedir } from '../acceso/api'

/**
 * Un módulo del catálogo comercial, con su estado en el municipio de este
 * subdominio.
 *
 * El `codigo` es el mismo que prefija los permisos del módulo
 * (`ejemplo` ↔ `ejemplo.usar`, ADR 0012 §6), lo que permite cruzar
 * entitlement y permisos sin una tabla de mapeo.
 */
export type Modulo = {
  codigo: string
  nombre: string
  descripcion: string
  habilitado: boolean
}

export type EstadoModulos =
  | { estado: 'cargando' }
  | { estado: 'listo'; modulos: Modulo[] }
  | { estado: 'error'; mensaje: string }

/**
 * Catálogo completo de módulos contratables, con el flag `habilitado` que
 * indica si el municipio de este subdominio lo tiene contratado.
 *
 * Es un endpoint público (ADR 0012 §7): el portal lo necesita para armar
 * la navegación antes de que exista sesión, y qué módulos ofrece el
 * producto —y cuáles tiene contratados un municipio, visible en su propio
 * portal de todos modos— no es información protegida. Ocultar acá es
 * comodidad de navegación, no el enforcement: eso lo hace el backend en
 * cada request (ADR 0009).
 */
export function useModulos(): EstadoModulos {
  const [estado, setEstado] = useState<EstadoModulos>({ estado: 'cargando' })

  useEffect(() => {
    let vigente = true

    pedir<Modulo[]>('/api/modulos', 'No se pudo cargar el catálogo de módulos.')
      .then((modulos) => {
        if (vigente) {
          setEstado({ estado: 'listo', modulos })
        }
      })
      .catch((error: unknown) => {
        if (vigente) {
          setEstado({
            estado: 'error',
            mensaje: error instanceof Error ? error.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigente = false
    }
  }, [])

  return estado
}
