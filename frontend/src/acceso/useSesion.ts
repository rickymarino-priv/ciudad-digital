import { useCallback, useEffect, useState } from 'react'

import { enviar, pedir } from './api'

export type Usuario = {
  id: number
  nombre: string
  email: string
  permisos: string[]
}

export type EstadoSesion =
  | { estado: 'cargando' }
  | { estado: 'anonimo' }
  | { estado: 'autenticado'; usuario: Usuario }

type SesionResponse = {
  autenticado: boolean
  usuario: Usuario | null
}

export type Sesion = {
  estado: EstadoSesion
  iniciar: (email: string, password: string) => Promise<void>
  cerrar: () => Promise<void>
}

/**
 * Sesión del usuario en el municipio de este subdominio.
 *
 * No hay ningún parámetro de municipio: la sesión se abre en el municipio
 * del host, y el backend no acepta que se le pida otro (ADR 0010).
 */
export function useSesion(): Sesion {
  const [estado, setEstado] = useState<EstadoSesion>({ estado: 'cargando' })

  const aplicar = useCallback((respuesta: SesionResponse) => {
    setEstado(
      respuesta.autenticado && respuesta.usuario
        ? { estado: 'autenticado', usuario: respuesta.usuario }
        : { estado: 'anonimo' },
    )
  }, [])

  // Al arrancar hay que preguntar si ya hay sesión: la cookie es HttpOnly,
  // así que el frontend no puede saberlo por su cuenta. De paso, la
  // respuesta trae la cookie con el token CSRF que después necesita el
  // login.
  useEffect(() => {
    let vigente = true

    pedir<SesionResponse>('/api/sesion', 'No se pudo consultar la sesión.')
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
        '/api/sesion',
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
    await enviar('/api/sesion', 'DELETE', undefined, 'No se pudo cerrar la sesión.')
    setEstado({ estado: 'anonimo' })
  }, [])

  return { estado, iniciar, cerrar }
}
