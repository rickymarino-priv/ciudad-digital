import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type TipoDeInstitucion =
  | 'JARDIN_MATERNAL'
  | 'JARDIN_DE_INFANTES'
  | 'CENTRO_DE_FORMACION_PROFESIONAL'
  | 'OTRA'

type EstadoDeInstitucion = 'ACTIVA' | 'CERRADA_TEMPORALMENTE' | 'CERRADA_DEFINITIVAMENTE'

type Institucion = {
  id: number
  nombre: string
  tipo: TipoDeInstitucion
  ubicacion: string
  descripcion: string | null
  estado: EstadoDeInstitucion
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const TIPOS: { valor: TipoDeInstitucion; etiqueta: string }[] = [
  { valor: 'JARDIN_MATERNAL', etiqueta: 'Jardín maternal' },
  { valor: 'JARDIN_DE_INFANTES', etiqueta: 'Jardín de infantes' },
  { valor: 'CENTRO_DE_FORMACION_PROFESIONAL', etiqueta: 'Centro de formación profesional' },
  { valor: 'OTRA', etiqueta: 'Otra' },
]

const ETIQUETA_TIPO: Record<TipoDeInstitucion, string> = TIPOS.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeInstitucion, string>,
)

const ESTADOS: { valor: EstadoDeInstitucion; etiqueta: string }[] = [
  { valor: 'ACTIVA', etiqueta: 'Activa' },
  { valor: 'CERRADA_TEMPORALMENTE', etiqueta: 'Cerrada temporalmente' },
  { valor: 'CERRADA_DEFINITIVAMENTE', etiqueta: 'Cerrada definitivamente' },
]

const ETIQUETA_ESTADO: Record<EstadoDeInstitucion, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeInstitucion, string>,
)

// Mismo mapa de transiciones válidas que valida el backend
// (`GestionDeEducacion`, ADR 0028 §4): acá solo decide qué opciones ofrecer
// en el `<select>` de cada fila, el enforcement real sigue siendo del
// backend (ADR 0011), mismo criterio que `TRANSICIONES_VALIDAS` en
// `PantallaDeObras`/`PantallaDeArbolado`.
const TRANSICIONES_VALIDAS: Record<EstadoDeInstitucion, EstadoDeInstitucion[]> = {
  ACTIVA: ['CERRADA_TEMPORALMENTE'],
  CERRADA_TEMPORALMENTE: ['ACTIVA', 'CERRADA_DEFINITIVAMENTE'],
  CERRADA_DEFINITIVAMENTE: [],
}

/** Mismo texto que en `PantallaDeBoletin`/`PantallaDeObras` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; instituciones: Institucion[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  nombre: string
  tipo: TipoDeInstitucion | ''
  ubicacion: string
  descripcion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  nombre: '',
  tipo: '',
  ubicacion: '',
  descripcion: '',
  enviando: false,
  error: null,
}

type EdicionEstado = {
  id: number
  estadoNuevo: EstadoDeInstitucion
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `educacion`: búsqueda pública de instituciones
 * educativas municipales (sin sesión) y, dentro de la misma vista, la
 * acción de registrar una institución nueva y de cambiar el estado de cada
 * una, visibles solo para quien tiene `educacion.gestionar` (ADR 0011: se
 * esconde por comodidad, el backend vuelve a exigir el permiso). Mismo
 * patrón exacto que `PantallaDeObras`/`PantallaDeArbolado` — no el de
 * `PantallaDeReclamos`, que muestra vistas *alternativas* según permiso:
 * acá el listado es el mismo para todos, solo cambia qué acciones se ven
 * (ADR 0028).
 */
export function PantallaDeEducacion({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('educacion.gestionar') ?? false

  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeInstitucion | ''>('')
  const [tipoFiltro, setTipoFiltro] = useState<TipoDeInstitucion | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    estado: EstadoDeInstitucion | ''
    tipo: TipoDeInstitucion | ''
    q: string
  }>({ estado: '', tipo: '', q: '' })

  const [estado, setEstado] = useState<EstadoListado>({ estado: 'cargando' })

  // Mismo patrón que PanelDeGestion/PanelDeUsuarios: evita pisar estado de
  // un componente que ya no está montado cuando un pedido en vuelo termina
  // después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarInstituciones = useCallback(
    async (filtros: { estado: EstadoDeInstitucion | ''; tipo: TipoDeInstitucion | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.tipo !== '') {
        parametros.set('tipo', filtros.tipo)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const instituciones = await pedir<Institucion[]>(
          `/api/educacion${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de instituciones educativas.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', instituciones })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'educacion' })
        } else {
          setEstado({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      }
    },
    [],
  )

  useEffect(() => {
    // Carga inicial y recarga al cambiar los filtros aplicados (mismo
    // patrón que PantallaDeObras): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarInstituciones(filtrosAplicados)
  }, [cargarInstituciones, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ estado: estadoFiltro, tipo: tipoFiltro, q: qFiltro })
  }

  // --- Registro de institución ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [registro, setRegistro] = useState<EstadoRegistro>(REGISTRO_INICIAL)

  const botonRegistrar = useRef<HTMLButtonElement>(null)
  const primerCampoRegistro = useRef<HTMLInputElement>(null)
  const errorRegistroRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioAbierto) {
      primerCampoRegistro.current?.focus()
    }
  }, [formularioAbierto])

  useEffect(() => {
    if (registro.error) {
      errorRegistroRef.current?.focus()
    }
  }, [registro.error])

  function abrirFormulario() {
    setRegistro(REGISTRO_INICIAL)
    setFormularioAbierto(true)
  }

  function cerrarFormulario() {
    setFormularioAbierto(false)
    botonRegistrar.current?.focus()
  }

  async function registrarInstitucion(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/educacion',
        'POST',
        {
          nombre: registro.nombre,
          tipo: registro.tipo,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
        },
        'No se pudo registrar la institución.',
      )
      if (!vigente.current) {
        return
      }
      await cargarInstituciones(filtrosAplicados)
      if (vigente.current) {
        setFormularioAbierto(false)
        botonRegistrar.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'educacion'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar la institución.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-institucion'

  // --- Cambio de estado por fila ---

  const [edicion, setEdicion] = useState<EdicionEstado | null>(null)
  const botonesCambiarEstado = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLSelectElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el select o el error no hay
    // que robarle el foco a lo que esté tocando, mismo criterio que
    // PantallaDeObras/PantallaDeReclamos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(institucion: Institucion) {
    const opciones = TRANSICIONES_VALIDAS[institucion.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({ id: institucion.id, estadoNuevo: opciones[0], enviando: false, error: null })
  }

  function cerrarEdicion(idInstitucion: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idInstitucion)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/educacion/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado de la institución.',
      )
      await cargarInstituciones(filtrosAplicados)
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
                error:
                  fallo instanceof Error
                    ? fallo.message
                    : 'No se pudo actualizar el estado de la institución.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Educación municipal'}
      </h1>
      <p className="contenido__bajada">
        Instituciones educativas de gestión municipal (jardines maternales,
        jardines de infantes y centros de formación profesional): nombre,
        tipo, ubicación y estado. No hace falta tener cuenta ni iniciar
        sesión para consultarlas.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar instituciones</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="educacion-filtro-estado">Estado</label>
            <select
              id="educacion-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeInstitucion | '')}
            >
              <option value="">Todos</option>
              {ESTADOS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="educacion-filtro-tipo">Tipo</label>
            <select
              id="educacion-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeInstitucion | '')}
            >
              <option value="">Todos</option>
              {TIPOS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="educacion-filtro-q">Buscar en nombre o ubicación</label>
            <input
              id="educacion-filtro-q"
              value={qFiltro}
              onChange={(evento) => setQFiltro(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estado.estado === 'cargando' && <p role="status">Buscando instituciones…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.instituciones.length === 0 && (
          <p>No se encontraron instituciones.</p>
        )}

        {estado.estado === 'listo' && estado.instituciones.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Instituciones educativas de gestión municipal registradas por
                el municipio. Se puede filtrar por estado, tipo y texto en el
                nombre o la ubicación.
                {puedeGestionar &&
                  ' Se puede cambiar el estado de las que todavía admiten una transición.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Estado</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estado.instituciones.map((institucion) => {
                  const enEdicion = edicion && edicion.id === institucion.id ? edicion : null
                  const opcionesValidas = TRANSICIONES_VALIDAS[institucion.estado]

                  return (
                    <tr key={institucion.id}>
                      <th scope="row">{institucion.nombre}</th>
                      <td>{ETIQUETA_TIPO[institucion.tipo]}</td>
                      <td>{institucion.ubicacion}</td>
                      <td>{ETIQUETA_ESTADO[institucion.estado]}</td>
                      {puedeGestionar && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`institucion-${institucion.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`institucion-${institucion.id}-estado`}
                                  ref={primerCampoEdicion}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicion((actual) =>
                                      actual
                                        ? {
                                            ...actual,
                                            estadoNuevo: evento.target.value as EstadoDeInstitucion,
                                          }
                                        : actual,
                                    )
                                  }
                                >
                                  {opcionesValidas.map((opcion) => (
                                    <option key={opcion} value={opcion}>
                                      {ETIQUETA_ESTADO[opcion]}
                                    </option>
                                  ))}
                                </select>
                              </div>

                              <button
                                type="button"
                                className="boton"
                                disabled={enEdicion.enviando}
                                aria-busy={enEdicion.enviando}
                                onClick={() => void guardarEdicion()}
                              >
                                {enEdicion.enviando ? 'Actualizando…' : 'Actualizar estado'}
                              </button>
                              <button
                                type="button"
                                className="boton boton--secundario"
                                onClick={() => cerrarEdicion(institucion.id)}
                              >
                                Cancelar
                              </button>
                              {enEdicion.error && (
                                <p
                                  className="formulario__error"
                                  role="alert"
                                  tabIndex={-1}
                                  ref={errorEdicionRef}
                                >
                                  {enEdicion.error}
                                </p>
                              )}
                            </div>
                          ) : opcionesValidas.length > 0 ? (
                            <button
                              type="button"
                              className="boton boton--secundario"
                              ref={(elemento) => {
                                if (elemento) {
                                  botonesCambiarEstado.current.set(institucion.id, elemento)
                                } else {
                                  botonesCambiarEstado.current.delete(institucion.id)
                                }
                              }}
                              onClick={() => abrirEdicion(institucion)}
                            >
                              Cambiar estado
                            </button>
                          ) : (
                            'Sin cambios de estado disponibles'
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

      {puedeGestionar && (
        <section aria-labelledby="titulo-registrar">
          <h2 id="titulo-registrar">Registrar institución</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormulario}>
                Registrar institución
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void registrarInstitucion(evento)}>
              {registro.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorRegistro}
                  role="alert"
                  tabIndex={-1}
                  ref={errorRegistroRef}
                >
                  {registro.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="educacion-nombre">Nombre</label>
                <input
                  id="educacion-nombre"
                  ref={primerCampoRegistro}
                  required
                  value={registro.nombre}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, nombre: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="educacion-tipo">Tipo</label>
                <select
                  id="educacion-tipo"
                  required
                  value={registro.tipo}
                  onChange={(evento) =>
                    setRegistro((actual) => ({
                      ...actual,
                      tipo: evento.target.value as TipoDeInstitucion,
                    }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                >
                  <option value="" disabled>
                    Elegí un tipo
                  </option>
                  {TIPOS.map((opcion) => (
                    <option key={opcion.valor} value={opcion.valor}>
                      {opcion.etiqueta}
                    </option>
                  ))}
                </select>
              </div>

              <div className="campo">
                <label htmlFor="educacion-ubicacion">Ubicación</label>
                <input
                  id="educacion-ubicacion"
                  required
                  value={registro.ubicacion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, ubicacion: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="educacion-descripcion">Descripción (opcional)</label>
                <textarea
                  id="educacion-descripcion"
                  value={registro.descripcion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, descripcion: evento.target.value }))
                  }
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={registro.enviando}
                  aria-busy={registro.enviando}
                >
                  {registro.enviando ? 'Registrando…' : 'Registrar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormulario}
                  disabled={registro.enviando}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </section>
      )}
    </main>
  )
}
