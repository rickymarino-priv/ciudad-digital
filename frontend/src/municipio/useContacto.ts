import { useEffect, useState } from 'react'

/**
 * Datos de contacto del municipio, leídos de su propia base de datos.
 *
 * A diferencia del tema —que vive en la base de control junto al registro
 * de municipios— esto sale de la base del municipio. Es lo que hace
 * visible que cada uno guarda sus datos por separado.
 */
export type Contacto = {
  direccion: string
  telefono: string
  email: string
}

export type EstadoContacto =
  | { estado: 'cargando' }
  | { estado: 'listo'; contacto: Contacto }
  | { estado: 'sin-datos' }

export function useContacto(): EstadoContacto {
  const [estado, setEstado] = useState<EstadoContacto>({ estado: 'cargando' })

  useEffect(() => {
    const abort = new AbortController()

    fetch('/api/municipio/contacto', { signal: abort.signal })
      .then(async (respuesta) => {
        // 204: el municipio existe pero todavía no cargó sus datos. No es
        // un error, así que el portal simplemente no muestra la sección.
        if (respuesta.status === 204 || !respuesta.ok) {
          setEstado({ estado: 'sin-datos' })
          return
        }
        setEstado({ estado: 'listo', contacto: (await respuesta.json()) as Contacto })
      })
      .catch((error: unknown) => {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }
        setEstado({ estado: 'sin-datos' })
      })

    return () => abort.abort()
  }, [])

  return estado
}
