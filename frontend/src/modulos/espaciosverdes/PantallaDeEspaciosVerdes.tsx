import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type TipoDeEspacioVerde = 'PLAZA' | 'PARQUE' | 'PASEO' | 'OTRA'

type EstadoDeEspacioVerde = 'DISPONIBLE' | 'EN_MANTENIMIENTO' | 'CERRADO'

type EspacioVerde = {
  id: number
  nombre: string
  tipo: TipoDeEspacioVerde
  ubicacion: string
  descripcion: string | null
  superficie: number | null
  estado: EstadoDeEspacioVerde
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const TIPOS: { valor: TipoDeEspacioVerde; etiqueta: string }[] = [
  { valor: 'PLAZA', etiqueta: 'Plaza' },
  { valor: 'PARQUE', etiqueta: 'Parque' },
  { valor: 'PASEO', etiqueta: 'Paseo' },
  { valor: 'OTRA', etiqueta: 'Otra' },
]

const ETIQUETA_TIPO: Record<TipoDeEspacioVerde, string> = TIPOS.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeEspacioVerde, string>,
)

const ESTADOS: { valor: EstadoDeEspacioVerde; etiqueta: string }[] = [
  { valor: 'DISPONIBLE', etiqueta: 'Disponible' },
  { valor: 'EN_MANTENIMIENTO', etiqueta: 'En mantenimiento' },
  { valor: 'CERRADO', etiqueta: 'Cerrado' },
]

const ETIQUETA_ESTADO: Record<EstadoDeEspacioVerde, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeEspacioVerde, string>,
)

// Mismo mapa de transiciones válidas que valida el backend
// (`GestionDeEspaciosVerdes`, ADR 0029 §5): acá solo decide qué opciones
// ofrecer en el `<select>` de cada fila, el enforcement real sigue siendo
// del backend (ADR 0011), mismo criterio que `TRANSICIONES_VALIDAS` en
// `PantallaDeObras`/`PantallaDeArbolado`/`PantallaDeEducacion`.
const TRANSICIONES_VALIDAS: Record<EstadoDeEspacioVerde, EstadoDeEspacioVerde[]> = {
  DISPONIBLE: ['EN_MANTENIMIENTO'],
  EN_MANTENIMIENTO: ['DISPONIBLE', 'CERRADO'],
  CERRADO: [],
}

const SUPERFICIE = new Intl.NumberFormat('es-AR', { maximumFractionDigits: 2 })

/** Mismo texto que en `PantallaDeObras`/`PantallaDeArbolado`/`PantallaDeEducacion` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea la superficie en m², mismo criterio de formateo con `Intl` que
 * las fechas en `PantallaDeObras`/`PantallaDeArbolado`. `null` (superficie
 * no cargada) se muestra como "— m²".
 */
function formatearSuperficie(superficie: number | null): string {
  if (superficie === null) {
    return '— m²'
  }
  return `${SUPERFICIE.format(superficie)} m²`
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; espaciosVerdes: EspacioVerde[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  nombre: string
  tipo: TipoDeEspacioVerde | ''
  ubicacion: string
  superficie: string
  descripcion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  nombre: '',
  tipo: '',
  ubicacion: '',
  superficie: '',
  descripcion: '',
  enviando: false,
  error: null,
}

type EdicionEstado = {
  id: number
  estadoNuevo: EstadoDeEspacioVerde
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `espaciosverdes`: búsqueda pública del padrón de
 * plazas, parques y paseos (sin sesión) y, dentro de la misma vista, la
 * acción de registrar un espacio verde nuevo y de cambiar su estado,
 * visibles solo para quien tiene `espaciosverdes.gestionar` (ADR 0011: se
 * esconde por comodidad, el backend vuelve a exigir el permiso). Mismo
 * patrón exacto que `PantallaDeObras`/`PantallaDeArbolado`/
 * `PantallaDeEducacion` — no el de `PantallaDeReclamos`, que muestra
 * vistas *alternativas* según permiso: acá el listado es el mismo para
 * todos, solo cambia qué acciones se ven (ADR 0029).
 */
export function PantallaDeEspaciosVerdes({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('espaciosverdes.gestionar') ?? false

  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeEspacioVerde | ''>('')
  const [tipoFiltro, setTipoFiltro] = useState<TipoDeEspacioVerde | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    estado: EstadoDeEspacioVerde | ''
    tipo: TipoDeEspacioVerde | ''
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

  const cargarEspaciosVerdes = useCallback(
    async (filtros: { estado: EstadoDeEspacioVerde | ''; tipo: TipoDeEspacioVerde | ''; q: string }) => {
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
        const espaciosVerdes = await pedir<EspacioVerde[]>(
          `/api/espaciosverdes${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de espacios verdes.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', espaciosVerdes })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'espaciosverdes' })
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
    // patrón que PantallaDeObras/PantallaDeArbolado): el setState está
    // protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarEspaciosVerdes(filtrosAplicados)
  }, [cargarEspaciosVerdes, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ estado: estadoFiltro, tipo: tipoFiltro, q: qFiltro })
  }

  // --- Registro de espacio verde ---

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

  async function registrarEspacioVerde(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/espaciosverdes',
        'POST',
        {
          nombre: registro.nombre,
          tipo: registro.tipo,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
          superficie: registro.superficie.trim() === '' ? null : Number(registro.superficie),
        },
        'No se pudo registrar el espacio verde.',
      )
      if (!vigente.current) {
        return
      }
      await cargarEspaciosVerdes(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'espaciosverdes'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar el espacio verde.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-espacio-verde'

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
    // PantallaDeObras/PantallaDeArbolado.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(espacioVerde: EspacioVerde) {
    const opciones = TRANSICIONES_VALIDAS[espacioVerde.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({ id: espacioVerde.id, estadoNuevo: opciones[0], enviando: false, error: null })
  }

  function cerrarEdicion(idEspacioVerde: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idEspacioVerde)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/espaciosverdes/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado del espacio verde.',
      )
      await cargarEspaciosVerdes(filtrosAplicados)
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
                    : 'No se pudo actualizar el estado del espacio verde.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Espacios Verdes'}
      </h1>
      <p className="contenido__bajada">
        Plazas, parques y paseos registrados por el municipio: tipo,
        ubicación, superficie y estado. No hace falta tener cuenta ni
        iniciar sesión para consultarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar espacios verdes</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="espaciosverdes-filtro-estado">Estado</label>
            <select
              id="espaciosverdes-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeEspacioVerde | '')}
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
            <label htmlFor="espaciosverdes-filtro-tipo">Tipo</label>
            <select
              id="espaciosverdes-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeEspacioVerde | '')}
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
            <label htmlFor="espaciosverdes-filtro-q">Buscar en nombre o ubicación</label>
            <input
              id="espaciosverdes-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando espacios verdes…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.espaciosVerdes.length === 0 && (
          <p>No se encontraron espacios verdes.</p>
        )}

        {estado.estado === 'listo' && estado.espaciosVerdes.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Espacios verdes registrados por el municipio. Se puede
                filtrar por estado, tipo y texto en el nombre o la
                ubicación.
                {puedeGestionar &&
                  ' Se puede cambiar el estado de los que todavía admiten una transición.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Superficie</th>
                  <th scope="col">Estado</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estado.espaciosVerdes.map((espacioVerde) => {
                  const enEdicion = edicion && edicion.id === espacioVerde.id ? edicion : null
                  const opcionesValidas = TRANSICIONES_VALIDAS[espacioVerde.estado]

                  return (
                    <tr key={espacioVerde.id}>
                      <th scope="row">{espacioVerde.nombre}</th>
                      <td>{ETIQUETA_TIPO[espacioVerde.tipo]}</td>
                      <td>{espacioVerde.ubicacion}</td>
                      <td>{formatearSuperficie(espacioVerde.superficie)}</td>
                      <td>{ETIQUETA_ESTADO[espacioVerde.estado]}</td>
                      {puedeGestionar && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`espacio-verde-${espacioVerde.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`espacio-verde-${espacioVerde.id}-estado`}
                                  ref={primerCampoEdicion}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicion((actual) =>
                                      actual
                                        ? {
                                            ...actual,
                                            estadoNuevo: evento.target.value as EstadoDeEspacioVerde,
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
                                onClick={() => cerrarEdicion(espacioVerde.id)}
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
                                  botonesCambiarEstado.current.set(espacioVerde.id, elemento)
                                } else {
                                  botonesCambiarEstado.current.delete(espacioVerde.id)
                                }
                              }}
                              onClick={() => abrirEdicion(espacioVerde)}
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
          <h2 id="titulo-registrar">Registrar espacio verde</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormulario}>
                Registrar espacio verde
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void registrarEspacioVerde(evento)}>
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
                <label htmlFor="espaciosverdes-nombre">Nombre</label>
                <input
                  id="espaciosverdes-nombre"
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
                <label htmlFor="espaciosverdes-tipo">Tipo</label>
                <select
                  id="espaciosverdes-tipo"
                  required
                  value={registro.tipo}
                  onChange={(evento) =>
                    setRegistro((actual) => ({
                      ...actual,
                      tipo: evento.target.value as TipoDeEspacioVerde,
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
                <label htmlFor="espaciosverdes-ubicacion">Ubicación</label>
                <input
                  id="espaciosverdes-ubicacion"
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
                <label htmlFor="espaciosverdes-superficie">Superficie en m² (opcional)</label>
                <input
                  id="espaciosverdes-superficie"
                  type="number"
                  min="0"
                  step="0.01"
                  value={registro.superficie}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, superficie: evento.target.value }))
                  }
                />
              </div>

              <div className="campo">
                <label htmlFor="espaciosverdes-descripcion">Descripción (opcional)</label>
                <textarea
                  id="espaciosverdes-descripcion"
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
