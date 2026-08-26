import { useCallback, useEffect, useState } from 'react'

import { enviar, pedir } from '../acceso/api'

export type UsuarioDePlataforma = {
  id: number
  nombre: string
  email: string
}

export type EstadoSesionDePlataforma =
  | { estado: 'cargando' }
  | { estado: 'anonimo' }
  | { estado: 'autenticado'; usuario: UsuarioDePlataforma }

type SesionResponse = {
  autenticado: boolean
  usuario: UsuarioDePlataforma | null
}

export type SesionDePlataforma = {
  estado: EstadoSesionDePlataforma
  iniciar: (email: string, password: string) => Promise<void>
  cerrar: () => Promise<void>
}

/**
 * Sesión de usuario de plataforma en la consola del proveedor (ADR 0019).
 *
 * Mismo patrón que {@link ../acceso/useSesion}, apuntando a
 * `/api/admin/sesion` en vez de `/api/sesion`. El usuario no tiene
 * `permisos`: la sesión de plataforma es todo-o-nada (ADR 0019 §4).
 */
export function useSesionDePlataforma(): SesionDePlataforma {
  const [estado, setEstado] = useState<EstadoSesionDePlataforma>({ estado: 'cargando' })

  const aplicar = useCallback((respuesta: SesionResponse) => {
    setEstado(
      respuesta.autenticado && respuesta.usuario
        ? { estado: 'autenticado', usuario: respuesta.usuario }
        : { estado: 'anonimo' },
    )
  }, [])

  useEffect(() => {
    let vigente = true

    pedir<SesionResponse>('/api/admin/sesion', 'No se pudo consultar la sesión.')
      .then((respuesta) => {
        if (vigente) {
          aplicar(respuesta)
        }
      })
      .catch(() => {
        if (vigente) {
          setEstado({ estado: 'anonimo' })
        }
      })

    return () => {
      vigente = false
    }
  }, [aplicar])

  const iniciar = useCallback(
    async (email: string, password: string) => {
      const respuesta = await enviar<SesionResponse>(
        '/api/admin/sesion',
        'POST',
        { email, password },
        'No se pudo iniciar sesión.',
      )
      if (respuesta) {
        aplicar(respuesta)
      }
    },
    [aplicar],
  )

  const cerrar = useCallback(async () => {
    await enviar('/api/admin/sesion', 'DELETE', undefined, 'No se pudo cerrar la sesión.')
    setEstado({ estado: 'anonimo' })
  }, [])

  return { estado, iniciar, cerrar }
}
