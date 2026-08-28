import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type TipoDeObra = 'VIALIDAD' | 'ESPACIO_PUBLICO' | 'EDIFICIO_PUBLICO' | 'SERVICIOS' | 'OTRA'

type EstadoDeObra = 'PLANIFICADA' | 'EN_EJECUCION' | 'PARALIZADA' | 'FINALIZADA'

type Obra = {
  id: number
  nombre: string
  tipo: TipoDeObra
  ubicacion: string
  descripcion: string | null
  estado: EstadoDeObra
  fechaInicioEstimada: string | null
  fechaFinEstimada: string | null
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const TIPOS: { valor: TipoDeObra; etiqueta: string }[] = [
  { valor: 'VIALIDAD', etiqueta: 'Vialidad' },
  { valor: 'ESPACIO_PUBLICO', etiqueta: 'Espacio público' },
  { valor: 'EDIFICIO_PUBLICO', etiqueta: 'Edificio público' },
  { valor: 'SERVICIOS', etiqueta: 'Servicios' },
  { valor: 'OTRA', etiqueta: 'Otra' },
]

const ETIQUETA_TIPO: Record<TipoDeObra, string> = TIPOS.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeObra, string>,
)

const ESTADOS: { valor: EstadoDeObra; etiqueta: string }[] = [
  { valor: 'PLANIFICADA', etiqueta: 'Planificada' },
  { valor: 'EN_EJECUCION', etiqueta: 'En ejecución' },
  { valor: 'PARALIZADA', etiqueta: 'Paralizada' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizada' },
]

const ETIQUETA_ESTADO: Record<EstadoDeObra, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeObra, string>,
)

// Mismo mapa de transiciones válidas que valida el backend
// (`GestionDeObras`, ADR 0023 §3): acá solo decide qué opciones ofrecer en
// el `<select>` de cada fila, el enforcement real sigue siendo del backend
// (ADR 0011), mismo criterio que `TRANSICIONES_VALIDAS` en
// `PantallaDeReclamos`.
const TRANSICIONES_VALIDAS: Record<EstadoDeObra, EstadoDeObra[]> = {
  PLANIFICADA: ['EN_EJECUCION'],
  EN_EJECUCION: ['PARALIZADA', 'FINALIZADA'],
  PARALIZADA: ['EN_EJECUCION'],
  FINALIZADA: [],
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })

/** Mismo texto que en `PantallaDeBoletin`/`PantallaDeReclamos` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario, mismo
 * criterio que `PantallaDeBoletin#formatearFecha`: pasarle ese string
 * directo a `new Date(...)` lo interpreta en UTC, y en un huso negativo
 * puede mostrar el día anterior. Se arma la fecha a partir de los
 * componentes, en la zona local. `null` (fecha no cargada) se muestra como
 * "—".
 */
function formatearFecha(fechaIso: string | null): string {
  if (fechaIso === null) {
    return '—'
  }
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; obras: Obra[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  nombre: string
  tipo: TipoDeObra | ''
  ubicacion: string
  descripcion: string
  fechaInicioEstimada: string
  fechaFinEstimada: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  nombre: '',
  tipo: '',
  ubicacion: '',
  descripcion: '',
  fechaInicioEstimada: '',
  fechaFinEstimada: '',
  enviando: false,
  error: null,
}

type EdicionEstado = {
  id: number
  estadoNuevo: EstadoDeObra
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `obras`: búsqueda pública de obras (sin sesión) y,
 * dentro de la misma vista, la acción de registrar una obra nueva y de
 * cambiar el estado de cada una, visibles solo para quien tiene
 * `obras.gestionar` (ADR 0011: se esconde por comodidad, el backend vuelve
 * a exigir el permiso). Mismo patrón exacto que `PantallaDeBoletin` — no
 * el de `PantallaDeReclamos`, que muestra vistas *alternativas* según
 * permiso: acá el listado es el mismo para todos, solo cambia qué
 * acciones se ven (ADR 0023).
 */
export function PantallaDeObras({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('obras.gestionar') ?? false

  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeObra | ''>('')
  const [tipoFiltro, setTipoFiltro] = useState<TipoDeObra | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    estado: EstadoDeObra | ''
    tipo: TipoDeObra | ''
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

  const cargarObras = useCallback(
    async (filtros: { estado: EstadoDeObra | ''; tipo: TipoDeObra | ''; q: string }) => {
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
        const obras = await pedir<Obra[]>(
          `/api/obras${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de obras públicas.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', obras })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'obras' })
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
    // patrón que PantallaDeBoletin): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarObras(filtrosAplicados)
  }, [cargarObras, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ estado: estadoFiltro, tipo: tipoFiltro, q: qFiltro })
  }

  // --- Registro de obra ---

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

  async function registrarObra(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/obras',
        'POST',
        {
          nombre: registro.nombre,
          tipo: registro.tipo,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
          fechaInicioEstimada: registro.fechaInicioEstimada.trim() === '' ? null : registro.fechaInicioEstimada,
          fechaFinEstimada: registro.fechaFinEstimada.trim() === '' ? null : registro.fechaFinEstimada,
        },
        'No se pudo registrar la obra.',
      )
      if (!vigente.current) {
        return
      }
      await cargarObras(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'obras'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar la obra.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-obra'

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
    // PantallaDeReclamos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(obra: Obra) {
    const opciones = TRANSICIONES_VALIDAS[obra.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({ id: obra.id, estadoNuevo: opciones[0], enviando: false, error: null })
  }

  function cerrarEdicion(idObra: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idObra)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/obras/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado de la obra.',
      )
      await cargarObras(filtrosAplicados)
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
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado de la obra.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Obras Públicas'}
      </h1>
      <p className="contenido__bajada">
        Obras públicas en curso en este municipio: nombre, tipo, ubicación y
        estado de avance. No hace falta tener cuenta ni iniciar sesión para
        consultarlas.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar obras</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="obras-filtro-estado">Estado</label>
            <select
              id="obras-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeObra | '')}
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
            <label htmlFor="obras-filtro-tipo">Tipo</label>
            <select
              id="obras-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeObra | '')}
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
            <label htmlFor="obras-filtro-q">Buscar en nombre o ubicación</label>
            <input
              id="obras-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando obras…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.obras.length === 0 && <p>No se encontraron obras.</p>}

        {estado.estado === 'listo' && estado.obras.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Obras públicas registradas por el municipio. Se puede
                filtrar por estado, tipo y texto en el nombre o la
                ubicación.
                {puedeGestionar &&
                  ' Se puede cambiar el estado de las que todavía admiten una transición.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha estimada de inicio</th>
                  <th scope="col">Fecha estimada de fin</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estado.obras.map((obra) => {
                  const enEdicion = edicion && edicion.id === obra.id ? edicion : null
                  const opcionesValidas = TRANSICIONES_VALIDAS[obra.estado]

                  return (
                    <tr key={obra.id}>
                      <th scope="row">{obra.nombre}</th>
                      <td>{ETIQUETA_TIPO[obra.tipo]}</td>
                      <td>{obra.ubicacion}</td>
                      <td>{ETIQUETA_ESTADO[obra.estado]}</td>
                      <td>{formatearFecha(obra.fechaInicioEstimada)}</td>
                      <td>{formatearFecha(obra.fechaFinEstimada)}</td>
                      {puedeGestionar && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`obra-${obra.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`obra-${obra.id}-estado`}
                                  ref={primerCampoEdicion}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicion((actual) =>
                                      actual
                                        ? { ...actual, estadoNuevo: evento.target.value as EstadoDeObra }
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
                                onClick={() => cerrarEdicion(obra.id)}
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
                                  botonesCambiarEstado.current.set(obra.id, elemento)
                                } else {
                                  botonesCambiarEstado.current.delete(obra.id)
                                }
                              }}
                              onClick={() => abrirEdicion(obra)}
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
          <h2 id="titulo-registrar">Registrar una obra</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormulario}>
                Registrar obra
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void registrarObra(evento)}>
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
                <label htmlFor="obras-nombre">Nombre</label>
                <input
                  id="obras-nombre"
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
                <label htmlFor="obras-tipo">Tipo</label>
                <select
                  id="obras-tipo"
                  required
                  value={registro.tipo}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, tipo: evento.target.value as TipoDeObra }))
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
                <label htmlFor="obras-ubicacion">Ubicación</label>
                <input
                  id="obras-ubicacion"
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
                <label htmlFor="obras-descripcion">Descripción (opcional)</label>
                <textarea
                  id="obras-descripcion"
                  value={registro.descripcion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, descripcion: evento.target.value }))
                  }
                />
              </div>

              <div className="campo">
                <label htmlFor="obras-fecha-inicio">Fecha estimada de inicio (opcional)</label>
                <input
                  id="obras-fecha-inicio"
                  type="date"
                  value={registro.fechaInicioEstimada}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fechaInicioEstimada: evento.target.value }))
                  }
                />
              </div>

              <div className="campo">
                <label htmlFor="obras-fecha-fin">Fecha estimada de fin (opcional)</label>
                <input
                  id="obras-fecha-fin"
                  type="date"
                  value={registro.fechaFinEstimada}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fechaFinEstimada: evento.target.value }))
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
