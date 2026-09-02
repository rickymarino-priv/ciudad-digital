import { useEffect, useRef } from 'react'

import { PanelDeAuditoria } from './PanelDeAuditoria'
import { PanelDeMiMunicipio } from './PanelDeMiMunicipio'
import { PanelDeReportes } from './PanelDeReportes'
import { PanelDeRoles } from './PanelDeRoles'
import { PanelDeUsuarios } from './PanelDeUsuarios'
import type { Usuario } from './useSesion'

type Props = {
  usuario: Usuario
  onVolver: () => void
}

/**
 * Administración del municipio: usuarios, roles y registro de auditoría.
 *
 * Es su propia vista, separada del portal público (ADR 0011): antes vivía
 * inline en la página del portal, pero la administración no es algo que
 * tenga sentido mostrarle a un vecino que solo quiere ver el portal.
 */
export function PanelDeAdministracion({ usuario, onVolver }: Props) {
  const puede = (permiso: string) => usuario.permisos.includes(permiso)

  const veUsuarios = puede('usuarios.ver') || puede('usuarios.administrar')
  const veRoles = puede('roles.ver') || puede('usuarios.administrar')
  const veAuditoria = puede('auditoria.ver')
  const veMiMunicipio = puede('municipio.verContrato')
  const veReportes = puede('reportes.ver')

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
        Usuarios y roles con acceso al portal de este municipio, y el
        registro de lo que se hizo.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {veUsuarios && <PanelDeUsuarios puedeAdministrar={puede('usuarios.administrar')} />}
      {veRoles && <PanelDeRoles puedeAdministrar={puede('roles.administrar')} />}
      {veAuditoria && <PanelDeAuditoria />}
      {veMiMunicipio && (
        <PanelDeMiMunicipio puedeSolicitar={puede('municipio.solicitarModulo')} />
      )}
      {veReportes && <PanelDeReportes />}

      {!veUsuarios && !veRoles && !veAuditoria && !veMiMunicipio && !veReportes && (
        <p role="status">
          No tenés permisos para administrar usuarios, roles ni auditoría, ni ver el contrato del
          municipio, ni ver reportes.
        </p>
      )}
    </main>
  )
}
