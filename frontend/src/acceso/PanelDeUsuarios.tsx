import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, pedir } from './api'

type RolResumen = { id: number; nombre: string }

type UsuarioDelMunicipio = {
  id: number
  nombre: string
  email: string
  activo: boolean
  ultimoAcceso: string | null
  roles: RolResumen[]
}

type RolParaAsignar = { id: number; nombre: string }

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; usuarios: UsuarioDelMunicipio[] }
  | { estado: 'error'; mensaje: string }

type EstadoRolesDisponibles =
  | { estado: 'cargando' }
  | { estado: 'listo'; roles: RolParaAsignar[] }
  | { estado: 'error'; mensaje: string }

type EdicionUsuario = {
  id: number
  activo: boolean
  roles: Set<number>
  enviando: boolean
  error: string | null
}

const FECHA = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

type Props = {
  /** Si puede además dar de alta y editar usuarios, no solo verlos. */
  puedeAdministrar: boolean
}

/**
 * Usuarios del municipio.
 *
 * Se muestra a quien tiene {@code usuarios.ver} o {@code usuarios.administrar},
 * pero eso es comodidad: el backend lo verifica igual en cada ruta, porque
 * esconder un botón no protege nada (ADR 0011).
 */
export function PanelDeUsuarios({ puedeAdministrar }: Props) {
  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })
  const [rolesDisponibles, setRolesDisponibles] = useState<EstadoRolesDisponibles>({
    estado: 'cargando',
  })

  // El componente puede seguir con pedidos en vuelo (crear, editar) después
  // de que el usuario navegó a otro lado: esta bandera evita pisar estado
  // de un componente que ya no está montado.
  const vigente = useRef(true)
  useEffect(() => {
    // El setup también marca "vigente": en StrictMode, React monta,
    // desmonta y vuelve a montar en desarrollo, y sin esto el desmontaje
    // simulado dejaría la bandera en false para siempre.
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarUsuarios = useCallback(async () => {
    try {
      const usuarios = await pedir<UsuarioDelMunicipio[]>(
        '/api/usuarios',
        'No se pudo cargar la lista de usuarios.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', usuarios })
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    void cargarUsuarios()
  }, [cargarUsuarios])

  // Los roles solo hacen falta para armar los checkboxes de alta/edición.
  // El endpoint acepta 'usuarios.administrar' además de 'roles.ver' a
  // propósito, para que esto no falle.
  useEffect(() => {
    if (!puedeAdministrar) {
      return
    }
    pedir<RolParaAsignar[]>('/api/roles', 'No se pudo cargar la lista de roles.')
      .then((roles) => {
        if (vigente.current) {
          setRolesDisponibles({ estado: 'listo', roles })
        }
      })
      .catch((fallo: unknown) => {
        if (vigente.current) {
          setRolesDisponibles({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })
  }, [puedeAdministrar])

  // --- Alta de usuario ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [nombreNuevo, setNombreNuevo] = useState('')
  const [emailNuevo, setEmailNuevo] = useState('')
  const [passwordNuevo, setPasswordNuevo] = useState('')
  const [rolesNuevo, setRolesNuevo] = useState<Set<number>>(new Set())
  const [enviandoNuevo, setEnviandoNuevo] = useState(false)
  const [errorNuevo, setErrorNuevo] = useState<string | null>(null)

  const botonNuevo = useRef<HTMLButtonElement>(null)
  const tituloNuevo = useRef<HTMLHeadingElement>(null)
  const errorNuevoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioAbierto) {
      tituloNuevo.current?.focus()
    }
  }, [formularioAbierto])

  useEffect(() => {
    if (errorNuevo) {
      errorNuevoRef.current?.focus()
    }
  }, [errorNuevo])

  function abrirFormularioNuevo() {
    setNombreNuevo('')
    setEmailNuevo('')
    setPasswordNuevo('')
    setRolesNuevo(new Set())
    setErrorNuevo(null)
    setFormularioAbierto(true)
  }

  function cerrarFormularioNuevo() {
    setFormularioAbierto(false)
    botonNuevo.current?.focus()
  }

  function alternarRolNuevo(id: number) {
    setRolesNuevo((actual) => {
      const copia = new Set(actual)
      if (copia.has(id)) {
        copia.delete(id)
      } else {
        copia.add(id)
      }
      return copia
    })
  }

  async function crearUsuario(evento: FormEvent) {
    evento.preventDefault()
    setErrorNuevo(null)
    setEnviandoNuevo(true)
    try {
      await enviar(
        '/api/usuarios',
        'POST',
        {
          nombre: nombreNuevo,
          email: emailNuevo,
          password: passwordNuevo,
          roles: Array.from(rolesNuevo),
        },
        'No se pudo crear el usuario.',
      )
      await cargarUsuarios()
      if (vigente.current) {
        cerrarFormularioNuevo()
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorNuevo(fallo instanceof Error ? fallo.message : 'No se pudo crear el usuario.')
      }
    } finally {
      if (vigente.current) {
        setEnviandoNuevo(false)
      }
    }
  }

  // --- Edición de usuario ---

  const [edicion, setEdicion] = useState<EdicionUsuario | null>(null)
  const botonesEditar = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLInputElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo cuando cambia de usuario: si solo cambió activo/roles/error no
    // hay que robarle el foco a lo que esté tocando en ese momento.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(usuario: UsuarioDelMunicipio) {
    setEdicion({
      id: usuario.id,
      activo: usuario.activo,
      roles: new Set(usuario.roles.map((rol) => rol.id)),
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion(idUsuario: number) {
    setEdicion(null)
    botonesEditar.current.get(idUsuario)?.focus()
  }

  function alternarRolEdicion(id: number) {
    setEdicion((actual) => {
      if (!actual) {
        return actual
      }
      const copia = new Set(actual.roles)
      if (copia.has(id)) {
        copia.delete(id)
      } else {
        copia.add(id)
      }
      return { ...actual, roles: copia }
    })
  }

  async function guardarEdicion(usuario: UsuarioDelMunicipio) {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/usuarios/${edicion.id}`,
        'PATCH',
        {
          nombre: usuario.nombre,
          activo: edicion.activo,
          roles: Array.from(edicion.roles),
        },
        'No se pudo actualizar el usuario.',
      )
      await cargarUsuarios()
      if (vigente.current) {
        cerrarEdicion(edicion.id)
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEdicion((actual) =>
          actual
            ? {
                ...actual,
                enviando: false,
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el usuario.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <section aria-labelledby="titulo-usuarios">
      <h2 id="titulo-usuarios">Usuarios del municipio</h2>

      {puedeAdministrar && (
        <div className="administracion__barra">
          {!formularioAbierto && (
            <button type="button" className="boton" ref={botonNuevo} onClick={abrirFormularioNuevo}>
              Nuevo usuario
            </button>
          )}

          {formularioAbierto && (
            <form className="formulario" onSubmit={(evento) => void crearUsuario(evento)}>
              <h3 ref={tituloNuevo} tabIndex={-1}>
                Nuevo usuario
              </h3>

              {errorNuevo && (
                <p
                  className="formulario__error"
                  role="alert"
                  tabIndex={-1}
                  ref={errorNuevoRef}
                >
                  {errorNuevo}
                </p>
              )}

              <div className="campo">
                <label htmlFor="nuevo-usuario-nombre">Nombre</label>
                <input
                  id="nuevo-usuario-nombre"
                  required
                  value={nombreNuevo}
                  onChange={(evento) => setNombreNuevo(evento.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="nuevo-usuario-email">Correo electrónico</label>
                <input
                  id="nuevo-usuario-email"
                  type="email"
                  autoComplete="username"
                  required
                  value={emailNuevo}
                  onChange={(evento) => setEmailNuevo(evento.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="nuevo-usuario-password">Contraseña</label>
                <input
                  id="nuevo-usuario-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  aria-describedby="nuevo-usuario-password-ayuda"
                  value={passwordNuevo}
                  onChange={(evento) => setPasswordNuevo(evento.target.value)}
                />
                <p className="campo__ayuda" id="nuevo-usuario-password-ayuda">
                  Tiene que tener al menos 12 caracteres.
                </p>
              </div>

              {rolesDisponibles.estado === 'cargando' && (
                <p role="status">Cargando los roles disponibles…</p>
              )}
              {rolesDisponibles.estado === 'error' && (
                <p role="alert">{rolesDisponibles.mensaje}</p>
              )}
              {rolesDisponibles.estado === 'listo' && (
                <fieldset className="grupo-checkboxes">
                  <legend>Roles</legend>
                  {rolesDisponibles.roles.length === 0 && (
                    <p className="campo__ayuda">Todavía no hay roles creados en este municipio.</p>
                  )}
                  {rolesDisponibles.roles.map((rol) => (
                    <label key={rol.id} className="grupo-checkboxes__opcion">
                      <input
                        type="checkbox"
                        checked={rolesNuevo.has(rol.id)}
                        onChange={() => alternarRolNuevo(rol.id)}
                      />
                      {rol.nombre}
                    </label>
                  ))}
                </fieldset>
              )}

              <div className="formulario__acciones">
                <button type="submit" className="boton" disabled={enviandoNuevo} aria-busy={enviandoNuevo}>
                  {enviandoNuevo ? 'Creando…' : 'Crear usuario'}
                </button>
                <button type="button" className="boton boton--secundario" onClick={cerrarFormularioNuevo}>
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {estado.estado === 'cargando' && <p role="status">Cargando los usuarios…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>
              Usuarios que pueden entrar al portal de este municipio, con los
              roles que tiene cada uno.
              {puedeAdministrar && ' Se pueden dar de alta usuarios nuevos y editar los existentes.'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">Correo electrónico</th>
                <th scope="col">Roles</th>
                <th scope="col">Estado</th>
                <th scope="col">Último acceso</th>
                {puedeAdministrar && <th scope="col">Acciones</th>}
              </tr>
            </thead>
            <tbody>
              {estado.usuarios.map((usuario) => {
                const usuarioEnEdicion = edicion && edicion.id === usuario.id ? edicion : null

                return (
                  <tr key={usuario.id}>
                    <th scope="row">{usuario.nombre}</th>
                    <td>{usuario.email}</td>
                    <td>
                      {usuarioEnEdicion && rolesDisponibles.estado === 'listo' ? (
                        <fieldset className="grupo-checkboxes grupo-checkboxes--compacto">
                          <legend>Roles de {usuario.nombre}</legend>
                          {rolesDisponibles.roles.map((rol, indice) => (
                            <label key={rol.id} className="grupo-checkboxes__opcion">
                              <input
                                ref={indice === 0 ? primerCampoEdicion : undefined}
                                type="checkbox"
                                checked={usuarioEnEdicion.roles.has(rol.id)}
                                onChange={() => alternarRolEdicion(rol.id)}
                              />
                              {rol.nombre}
                            </label>
                          ))}
                        </fieldset>
                      ) : (
                        usuario.roles.map((rol) => rol.nombre).join(', ') || 'Sin roles'
                      )}
                    </td>
                    <td>
                      {usuarioEnEdicion ? (
                        <label className="campo-en-linea">
                          <input
                            type="checkbox"
                            checked={usuarioEnEdicion.activo}
                            onChange={(evento) =>
                              setEdicion((actual) =>
                                actual ? { ...actual, activo: evento.target.checked } : actual,
                              )
                            }
                          />
                          Activo
                        </label>
                      ) : usuario.activo ? (
                        'Activo'
                      ) : (
                        'Desactivado'
                      )}
                    </td>
                    <td>
                      {usuario.ultimoAcceso
                        ? FECHA.format(new Date(usuario.ultimoAcceso))
                        : 'Nunca entró'}
                    </td>
                    {puedeAdministrar && (
                      <td>
                        {usuarioEnEdicion ? (
                          <div className="formulario__acciones formulario__acciones--compacto">
                            <button
                              type="button"
                              className="boton"
                              disabled={usuarioEnEdicion.enviando}
                              aria-busy={usuarioEnEdicion.enviando}
                              onClick={() => void guardarEdicion(usuario)}
                            >
                              {usuarioEnEdicion.enviando ? 'Guardando…' : 'Guardar'}
                            </button>
                            <button
                              type="button"
                              className="boton boton--secundario"
                              onClick={() => cerrarEdicion(usuario.id)}
                            >
                              Cancelar
                            </button>
                            {usuarioEnEdicion.error && (
                              <p
                                className="formulario__error"
                                role="alert"
                                tabIndex={-1}
                                ref={errorEdicionRef}
                              >
                                {usuarioEnEdicion.error}
                              </p>
                            )}
                          </div>
                        ) : (
                          <button
                            type="button"
                            className="boton boton--secundario"
                            ref={(elemento) => {
                              if (elemento) {
                                botonesEditar.current.set(usuario.id, elemento)
                              } else {
                                botonesEditar.current.delete(usuario.id)
                              }
                            }}
                            onClick={() => abrirEdicion(usuario)}
                          >
                            Editar
                          </button>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
