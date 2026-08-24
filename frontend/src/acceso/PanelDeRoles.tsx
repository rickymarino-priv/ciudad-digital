import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type FormEvent,
  type RefObject,
} from 'react'

import { enviar, pedir } from './api'

type Permiso = {
  codigo: string
  modulo: string
  accion: string
  descripcion: string | null
}

type AreaDePermisos = {
  area: string
  permisos: Permiso[]
}

type Rol = {
  id: number
  codigo: string
  nombre: string
  descripcion: string | null
  delSistema: boolean
  permisos: Permiso[]
}

type EstadoRoles =
  | { estado: 'cargando' }
  | { estado: 'listo'; roles: Rol[] }
  | { estado: 'error'; mensaje: string }

type EstadoCatalogo =
  | { estado: 'cargando' }
  | { estado: 'listo'; areas: AreaDePermisos[] }
  | { estado: 'error'; mensaje: string }

type EdicionRol = {
  id: number
  nombre: string
  descripcion: string
  permisos: Set<string>
  enviando: boolean
  error: string | null
}

type Props = {
  /** Si puede además crear, editar y borrar roles, no solo verlos. */
  puedeAdministrar: boolean
}

/** Checkboxes de permisos agrupados por área, reutilizados en alta y edición. */
function CamposDePermisos({
  areas,
  seleccionados,
  onAlternar,
  primerCampoRef,
}: {
  areas: AreaDePermisos[]
  seleccionados: Set<string>
  onAlternar: (codigo: string) => void
  primerCampoRef?: RefObject<HTMLInputElement | null>
}) {
  // El primer checkbox de todos (el del primer permiso de la primera área
  // que tenga alguno) es el que recibe el foco al abrir el formulario.
  const primerCodigo = areas.find((area) => area.permisos.length > 0)?.permisos[0]?.codigo

  return (
    <>
      {areas.map((area) => (
        <fieldset key={area.area} className="grupo-checkboxes">
          <legend>{area.area}</legend>
          {area.permisos.map((permiso) => {
            return (
              <label key={permiso.codigo} className="grupo-checkboxes__opcion">
                <input
                  ref={permiso.codigo === primerCodigo ? primerCampoRef : undefined}
                  type="checkbox"
                  checked={seleccionados.has(permiso.codigo)}
                  onChange={() => onAlternar(permiso.codigo)}
                />
                <span>
                  <strong>{permiso.codigo}</strong>
                  {permiso.descripcion ? ` — ${permiso.descripcion}` : ''}
                </span>
              </label>
            )
          })}
        </fieldset>
      ))}
    </>
  )
}

/**
 * Roles del municipio y catálogo de permisos con los que se arman.
 *
 * Se muestra a quien tiene {@code roles.ver} o {@code usuarios.administrar}
 * (el mismo criterio que exige el backend para listar), pero eso es
 * comodidad: cada ruta vuelve a verificar el permiso (ADR 0011).
 */
export function PanelDeRoles({ puedeAdministrar }: Props) {
  const [estado, setEstado] = useState<EstadoRoles>({ estado: 'cargando' })
  const [catalogo, setCatalogo] = useState<EstadoCatalogo>({ estado: 'cargando' })

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

  const cargarRoles = useCallback(async () => {
    try {
      const roles = await pedir<Rol[]>('/api/roles', 'No se pudo cargar la lista de roles.')
      if (vigente.current) {
        setEstado({ estado: 'listo', roles })
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
    void cargarRoles()
  }, [cargarRoles])

  // El catálogo de permisos solo hace falta para armar los checkboxes de
  // alta/edición de roles.
  useEffect(() => {
    if (!puedeAdministrar) {
      return
    }
    pedir<AreaDePermisos[]>('/api/permisos', 'No se pudo cargar el catálogo de permisos.')
      .then((areas) => {
        if (vigente.current) {
          setCatalogo({ estado: 'listo', areas })
        }
      })
      .catch((fallo: unknown) => {
        if (vigente.current) {
          setCatalogo({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })
  }, [puedeAdministrar])

  // --- Alta de rol ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [codigoNuevo, setCodigoNuevo] = useState('')
  const [nombreNuevo, setNombreNuevo] = useState('')
  const [descripcionNueva, setDescripcionNueva] = useState('')
  const [permisosNuevo, setPermisosNuevo] = useState<Set<string>>(new Set())
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
    setCodigoNuevo('')
    setNombreNuevo('')
    setDescripcionNueva('')
    setPermisosNuevo(new Set())
    setErrorNuevo(null)
    setFormularioAbierto(true)
  }

  function cerrarFormularioNuevo() {
    setFormularioAbierto(false)
    botonNuevo.current?.focus()
  }

  function alternarPermisoNuevo(codigo: string) {
    setPermisosNuevo((actual) => {
      const copia = new Set(actual)
      if (copia.has(codigo)) {
        copia.delete(codigo)
      } else {
        copia.add(codigo)
      }
      return copia
    })
  }

  async function crearRol(evento: FormEvent) {
    evento.preventDefault()
    setErrorNuevo(null)
    setEnviandoNuevo(true)
    try {
      await enviar(
        '/api/roles',
        'POST',
        {
          codigo: codigoNuevo,
          nombre: nombreNuevo,
          descripcion: descripcionNueva.trim() === '' ? null : descripcionNueva,
          permisos: Array.from(permisosNuevo),
        },
        'No se pudo crear el rol.',
      )
      await cargarRoles()
      if (vigente.current) {
        cerrarFormularioNuevo()
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorNuevo(fallo instanceof Error ? fallo.message : 'No se pudo crear el rol.')
      }
    } finally {
      if (vigente.current) {
        setEnviandoNuevo(false)
      }
    }
  }

  // --- Edición de rol ---

  const [edicion, setEdicion] = useState<EdicionRol | null>(null)
  const botonesEditar = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLInputElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(rol: Rol) {
    setEdicion({
      id: rol.id,
      nombre: rol.nombre,
      descripcion: rol.descripcion ?? '',
      permisos: new Set(rol.permisos.map((permiso) => permiso.codigo)),
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion(idRol: number) {
    setEdicion(null)
    botonesEditar.current.get(idRol)?.focus()
  }

  function alternarPermisoEdicion(codigo: string) {
    setEdicion((actual) => {
      if (!actual) {
        return actual
      }
      const copia = new Set(actual.permisos)
      if (copia.has(codigo)) {
        copia.delete(codigo)
      } else {
        copia.add(codigo)
      }
      return { ...actual, permisos: copia }
    })
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/roles/${edicion.id}`,
        'PATCH',
        {
          nombre: edicion.nombre,
          descripcion: edicion.descripcion.trim() === '' ? null : edicion.descripcion,
          permisos: Array.from(edicion.permisos),
        },
        'No se pudo actualizar el rol.',
      )
      await cargarRoles()
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
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el rol.',
              }
            : actual,
        )
      }
    }
  }

  // --- Borrado de rol ---

  const [confirmarBorrado, setConfirmarBorrado] = useState<number | null>(null)
  const [borrando, setBorrando] = useState(false)
  const [errorBorrado, setErrorBorrado] = useState<string | null>(null)
  const botonesBorrar = useRef<Map<number, HTMLButtonElement>>(new Map())
  const botonConfirmarBorrado = useRef<HTMLButtonElement>(null)
  const errorBorradoRef = useRef<HTMLParagraphElement>(null)
  const tituloSeccion = useRef<HTMLHeadingElement>(null)

  useEffect(() => {
    if (confirmarBorrado !== null) {
      botonConfirmarBorrado.current?.focus()
    }
  }, [confirmarBorrado])

  useEffect(() => {
    if (errorBorrado) {
      errorBorradoRef.current?.focus()
    }
  }, [errorBorrado])

  function pedirConfirmacionDeBorrado(id: number) {
    setErrorBorrado(null)
    setConfirmarBorrado(id)
  }

  function cancelarBorrado(id: number) {
    setConfirmarBorrado(null)
    botonesBorrar.current.get(id)?.focus()
  }

  async function confirmarYBorrar(id: number) {
    setBorrando(true)
    setErrorBorrado(null)
    try {
      await enviar(`/api/roles/${id}`, 'DELETE', undefined, 'No se pudo borrar el rol.')
      setConfirmarBorrado(null)
      await cargarRoles()
      // La tarjeta del rol borrado ya no existe: el foco vuelve al título
      // de la sección en lugar de a un botón que desapareció.
      if (vigente.current) {
        tituloSeccion.current?.focus()
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorBorrado(fallo instanceof Error ? fallo.message : 'No se pudo borrar el rol.')
      }
    } finally {
      if (vigente.current) {
        setBorrando(false)
      }
    }
  }

  return (
    <section aria-labelledby="titulo-roles">
      <h2 id="titulo-roles" tabIndex={-1} ref={tituloSeccion}>
        Roles del municipio
      </h2>

      {puedeAdministrar && (
        <div className="administracion__barra">
          {!formularioAbierto && (
            <button type="button" className="boton" ref={botonNuevo} onClick={abrirFormularioNuevo}>
              Nuevo rol
            </button>
          )}

          {formularioAbierto && (
            <form className="formulario" onSubmit={(evento) => void crearRol(evento)}>
              <h3 ref={tituloNuevo} tabIndex={-1}>
                Nuevo rol
              </h3>

              {errorNuevo && (
                <p className="formulario__error" role="alert" tabIndex={-1} ref={errorNuevoRef}>
                  {errorNuevo}
                </p>
              )}

              <div className="campo">
                <label htmlFor="nuevo-rol-codigo">Código</label>
                <input
                  id="nuevo-rol-codigo"
                  required
                  aria-describedby="nuevo-rol-codigo-ayuda"
                  value={codigoNuevo}
                  onChange={(evento) => setCodigoNuevo(evento.target.value)}
                />
                <p className="campo__ayuda" id="nuevo-rol-codigo-ayuda">
                  Minúsculas, números y guiones, empezando con una letra (por
                  ejemplo: atencion-vecinal).
                </p>
              </div>

              <div className="campo">
                <label htmlFor="nuevo-rol-nombre">Nombre</label>
                <input
                  id="nuevo-rol-nombre"
                  required
                  value={nombreNuevo}
                  onChange={(evento) => setNombreNuevo(evento.target.value)}
                />
              </div>

              <div className="campo">
                <label htmlFor="nuevo-rol-descripcion">Descripción</label>
                <textarea
                  id="nuevo-rol-descripcion"
                  value={descripcionNueva}
                  onChange={(evento) => setDescripcionNueva(evento.target.value)}
                />
              </div>

              {catalogo.estado === 'cargando' && (
                <p role="status">Cargando el catálogo de permisos…</p>
              )}
              {catalogo.estado === 'error' && <p role="alert">{catalogo.mensaje}</p>}
              {catalogo.estado === 'listo' && (
                <CamposDePermisos
                  areas={catalogo.areas}
                  seleccionados={permisosNuevo}
                  onAlternar={alternarPermisoNuevo}
                />
              )}

              <div className="formulario__acciones">
                <button type="submit" className="boton" disabled={enviandoNuevo} aria-busy={enviandoNuevo}>
                  {enviandoNuevo ? 'Creando…' : 'Crear rol'}
                </button>
                <button type="button" className="boton boton--secundario" onClick={cerrarFormularioNuevo}>
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {estado.estado === 'cargando' && <p role="status">Cargando los roles…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {errorBorrado && (
        <p className="formulario__error" role="alert" tabIndex={-1} ref={errorBorradoRef}>
          {errorBorrado}
        </p>
      )}

      {estado.estado === 'listo' && (
        <ul className="lista-roles">
          {estado.roles.map((rol) => {
            const rolEnEdicion = edicion && edicion.id === rol.id ? edicion : null

            return (
              <li key={rol.id} className="lista-roles__item">
                <div className="tarjeta-rol">
                  <div className="tarjeta-rol__encabezado">
                    <h3>{rol.nombre}</h3>
                    {rol.delSistema && <span className="badge">Rol de sistema</span>}
                  </div>

                  {rolEnEdicion ? (
                    <div className="formulario">
                      <div className="campo">
                        <label htmlFor={`rol-${rol.id}-nombre`}>Nombre</label>
                        <input
                          id={`rol-${rol.id}-nombre`}
                          ref={primerCampoEdicion}
                          required
                          value={rolEnEdicion.nombre}
                          onChange={(evento) =>
                            setEdicion((actual) =>
                              actual ? { ...actual, nombre: evento.target.value } : actual,
                            )
                          }
                        />
                      </div>

                      <div className="campo">
                        <label htmlFor={`rol-${rol.id}-descripcion`}>Descripción</label>
                        <textarea
                          id={`rol-${rol.id}-descripcion`}
                          value={rolEnEdicion.descripcion}
                          onChange={(evento) =>
                            setEdicion((actual) =>
                              actual ? { ...actual, descripcion: evento.target.value } : actual,
                            )
                          }
                        />
                      </div>

                      {catalogo.estado === 'listo' && (
                        <CamposDePermisos
                          areas={catalogo.areas}
                          seleccionados={rolEnEdicion.permisos}
                          onAlternar={alternarPermisoEdicion}
                        />
                      )}

                      <div className="formulario__acciones">
                        <button
                          type="button"
                          className="boton"
                          disabled={rolEnEdicion.enviando}
                          aria-busy={rolEnEdicion.enviando}
                          onClick={() => void guardarEdicion()}
                        >
                          {rolEnEdicion.enviando ? 'Guardando…' : 'Guardar'}
                        </button>
                        <button
                          type="button"
                          className="boton boton--secundario"
                          onClick={() => cerrarEdicion(rol.id)}
                        >
                          Cancelar
                        </button>
                      </div>

                      {rolEnEdicion.error && (
                        <p
                          className="formulario__error"
                          role="alert"
                          tabIndex={-1}
                          ref={errorEdicionRef}
                        >
                          {rolEnEdicion.error}
                        </p>
                      )}
                    </div>
                  ) : (
                    <>
                      {rol.descripcion && <p className="contenido__nota">{rol.descripcion}</p>}
                      <p>
                        <strong>Permisos:</strong>{' '}
                        {rol.permisos.length > 0
                          ? rol.permisos.map((permiso) => permiso.codigo).join(', ')
                          : 'Sin permisos asignados'}
                      </p>

                      {puedeAdministrar && (
                        <div className="formulario__acciones formulario__acciones--compacto">
                          <button
                            type="button"
                            className="boton boton--secundario"
                            ref={(elemento) => {
                              if (elemento) {
                                botonesEditar.current.set(rol.id, elemento)
                              } else {
                                botonesEditar.current.delete(rol.id)
                              }
                            }}
                            onClick={() => abrirEdicion(rol)}
                          >
                            Editar
                          </button>

                          {!rol.delSistema && confirmarBorrado !== rol.id && (
                            <button
                              type="button"
                              className="boton boton--secundario"
                              ref={(elemento) => {
                                if (elemento) {
                                  botonesBorrar.current.set(rol.id, elemento)
                                } else {
                                  botonesBorrar.current.delete(rol.id)
                                }
                              }}
                              onClick={() => pedirConfirmacionDeBorrado(rol.id)}
                            >
                              Borrar
                            </button>
                          )}
                        </div>
                      )}

                      {confirmarBorrado === rol.id && (
                        <p className="confirmacion">
                          ¿Confirmás borrar el rol {rol.nombre}?
                          <button
                            type="button"
                            className="boton boton--peligro"
                            disabled={borrando}
                            aria-busy={borrando}
                            ref={botonConfirmarBorrado}
                            onClick={() => void confirmarYBorrar(rol.id)}
                          >
                            {borrando ? 'Borrando…' : 'Sí, borrar'}
                          </button>
                          <button
                            type="button"
                            className="boton boton--secundario"
                            disabled={borrando}
                            onClick={() => cancelarBorrado(rol.id)}
                          >
                            No
                          </button>
                        </p>
                      )}
                    </>
                  )}
                </div>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
