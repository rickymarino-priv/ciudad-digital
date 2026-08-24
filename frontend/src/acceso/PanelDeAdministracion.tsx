import { useEffect, useRef } from 'react'

import { PanelDeRoles } from './PanelDeRoles'
import { PanelDeUsuarios } from './PanelDeUsuarios'
import type { Usuario } from './useSesion'

type Props = {
  usuario: Usuario
  onVolver: () => void
}

/**
 * Administración del municipio: usuarios y roles.
 *
 * Es su propia vista, separada del portal público (ADR 0011): antes vivía
 * inline en la página del portal, pero la administración no es algo que
 * tenga sentido mostrarle a un vecino que solo quiere ver el portal.
 */
export function PanelDeAdministracion({ usuario, onVolver }: Props) {
  const puede = (permiso: string) => usuario.permisos.includes(permiso)

  const veUsuarios = puede('usuarios.ver') || puede('usuarios.administrar')
  const veRoles = puede('roles.ver') || puede('usuarios.administrar')

  const titulo = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Administración
      </h1>
      <p className="contenido__bajada">
        Usuarios y roles con acceso al portal de este municipio.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {veUsuarios && <PanelDeUsuarios puedeAdministrar={puede('usuarios.administrar')} />}
      {veRoles && <PanelDeRoles puedeAdministrar={puede('roles.administrar')} />}

      {!veUsuarios && !veRoles && (
        <p role="status">No tenés permisos para administrar usuarios ni roles.</p>
      )}
    </main>
  )
}
