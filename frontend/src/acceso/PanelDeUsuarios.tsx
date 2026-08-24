import { useEffect, useState } from 'react'

import { pedir } from './api'

type UsuarioDelMunicipio = {
  id: number
  nombre: string
  email: string
  activo: boolean
  ultimoAcceso: string | null
  roles: string[]
}

type Estado =
  | { estado: 'cargando' }
  | { estado: 'listo'; usuarios: UsuarioDelMunicipio[] }
  | { estado: 'error'; mensaje: string }

const FECHA = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

/**
 * Usuarios del municipio.
 *
 * Solo se muestra a quien tiene el permiso {@code usuarios.ver}, pero eso
 * es comodidad: el backend lo verifica igual, porque esconder una pantalla
 * no protege nada (ADR 0011).
 */
export function PanelDeUsuarios() {
  const [estado, setEstado] = useState<Estado>({ estado: 'cargando' })

  useEffect(() => {
    let vigente = true

    pedir<UsuarioDelMunicipio[]>('/api/usuarios', 'No se pudo cargar la lista de usuarios.')
      .then((usuarios) => {
        if (vigente) {
          setEstado({ estado: 'listo', usuarios })
        }
      })
      .catch((fallo: unknown) => {
        if (vigente) {
          setEstado({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigente = false
    }
  }, [])

  return (
    <section aria-labelledby="titulo-usuarios">
      <h2 id="titulo-usuarios">Usuarios del municipio</h2>

      {estado.estado === 'cargando' && <p role="status">Cargando los usuarios…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>
              Usuarios que pueden entrar al portal de este municipio, con los
              roles que tiene cada uno.
            </caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Correo electrónico</th>
                <th scope="col">Roles</th>
                <th scope="col">Estado</th>
                <th scope="col">Último acceso</th>
              </tr>
            </thead>
            <tbody>
              {estado.usuarios.map((usuario) => (
                <tr key={usuario.id}>
                  <th scope="row">{usuario.nombre}</th>
                  <td>{usuario.email}</td>
                  <td>{usuario.roles.join(', ') || 'Sin roles'}</td>
                  {/* El estado va como texto, no como color: el color solo
                      no lo percibe todo el mundo. */}
                  <td>{usuario.activo ? 'Activo' : 'Desactivado'}</td>
                  <td>
                    {usuario.ultimoAcceso
                      ? FECHA.format(new Date(usuario.ultimoAcceso))
                      : 'Nunca entró'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
