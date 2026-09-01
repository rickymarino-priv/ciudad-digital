import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type RubroBromatologico = 'VERDULERIA' | 'CARNICERIA' | 'PANADERIA' | 'RESTAURANTE' | 'ALMACEN' | 'OTRO'

/** Mismo enum que usa `ComercioBromatologicoEntity.estado` e
 * `InspeccionBromatologicaEntity.resultado` (ADR 0032 §3): no hay un
 * segundo tipo con nombres distintos para el mismo conjunto de valores. */
type EstadoBromatologico = 'HABILITADO' | 'OBSERVADO' | 'CLAUSURADO'

type Comercio = {
  id: number
  rubro: RubroBromatologico
  nombre: string
  direccion: string
  estado: EstadoBromatologico
  fechaHabilitacion: string
  fechaVencimientoHabilitacion: string
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

/** Historial protegido de un comercio (ADR 0032 §4): a diferencia del
 * comercio, `observaciones` no es pública. */
type Inspeccion = {
  id: number
  comercioId: number
  fecha: string
  resultado: EstadoBromatologico
  observaciones: string | null
  inspeccionadoPorNombre: string
  inspeccionadoPorEmail: string
  creadoEn: string
}

const RUBROS: { valor: RubroBromatologico; etiqueta: string }[] = [
  { valor: 'VERDULERIA', etiqueta: 'Verdulería' },
  { valor: 'CARNICERIA', etiqueta: 'Carnicería' },
  { valor: 'PANADERIA', etiqueta: 'Panadería' },
  { valor: 'RESTAURANTE', etiqueta: 'Restaurante' },
  { valor: 'ALMACEN', etiqueta: 'Almacén' },
  { valor: 'OTRO', etiqueta: 'Otro' },
]

const ETIQUETA_RUBRO: Record<RubroBromatologico, string> = RUBROS.reduce(
  (mapa, rubro) => ({ ...mapa, [rubro.valor]: rubro.etiqueta }),
  {} as Record<RubroBromatologico, string>,
)

const ESTADOS: { valor: EstadoBromatologico; etiqueta: string }[] = [
  { valor: 'HABILITADO', etiqueta: 'Habilitado' },
  { valor: 'OBSERVADO', etiqueta: 'Observado' },
  { valor: 'CLAUSURADO', etiqueta: 'Clausurado' },
]

const ETIQUETA_ESTADO: Record<EstadoBromatologico, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoBromatologico, string>,
)

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })

/** Mismo texto que en `PantallaDeDefensaCivil`/`PantallaDeTurnos` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

function textoOVacio(valor: string | null): string {
  return valor && valor.trim() !== '' ? valor : '—'
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario, mismo
 * criterio que `PantallaDeTurnos#formatearFecha`: pasarle ese string
 * directo a `new Date(...)` lo interpreta en UTC, y en un huso negativo
 * puede mostrar el día anterior. Se arma la fecha a partir de los
 * componentes, en la zona local.
 */
function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

type EstadoListadoComercios =
  | { estado: 'cargando' }
  | { estado: 'listo'; comercios: Comercio[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistroComercio = {
  rubro: RubroBromatologico | ''
  nombre: string
  direccion: string
  fechaHabilitacion: string
  fechaVencimientoHabilitacion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_COMERCIO_INICIAL: EstadoRegistroComercio = {
  rubro: '',
  nombre: '',
  direccion: '',
  fechaHabilitacion: '',
  fechaVencimientoHabilitacion: '',
  enviando: false,
  error: null,
}

type EstadoInspeccionesDeFila =
  | { estado: 'cargando' }
  | { estado: 'listo'; inspecciones: Inspeccion[] }
  | { estado: 'error'; mensaje: string }

type EstadoFormularioInspeccion = {
  fecha: string
  resultado: EstadoBromatologico | ''
  observaciones: string
  enviando: boolean
  error: string | null
}

const FORMULARIO_INSPECCION_INICIAL: EstadoFormularioInspeccion = {
  fecha: '',
  resultado: '',
  observaciones: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `bromatologia` (ADR 0032): a diferencia de
 * `PantallaDeDefensaCivil`, una sola sección — el padrón de comercios y su
 * historial de inspecciones son entidades relacionadas, no independientes.
 *
 * - **Padrón de comercios**: listado público con filtros por rubro,
 *   estado y texto libre. El alta ("Registrar comercio") requiere el
 *   permiso `bromatologia.gestionar`.
 * - **Historial de inspecciones**: por fila, solo con el permiso, un
 *   botón "Ver inspecciones" expande (una fila a la vez, mismo patrón que
 *   las franjas horarias de `PantallaDeTurnos`) un panel que carga el
 *   historial protegido de ese comercio con un `GET` recién al abrirse, y
 *   ofrece un formulario para registrar una inspección nueva. Al
 *   confirmar, se recarga tanto ese historial como el padrón completo,
 *   porque el `estado` del comercio cambia como efecto de la inspección
 *   (ADR 0032 §3), no con un `PATCH` directo.
 */
export function PantallaDeBromatologia({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('bromatologia.gestionar') ?? false

  // Mismo patrón que PantallaDeDefensaCivil/PantallaDeTurnos: evita pisar
  // estado de un componente que ya no está montado cuando un pedido en
  // vuelo termina después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // ======================= Padrón de comercios =======================

  const [rubroFiltro, setRubroFiltro] = useState<RubroBromatologico | ''>('')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoBromatologico | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    rubro: RubroBromatologico | ''
    estado: EstadoBromatologico | ''
    q: string
  }>({ rubro: '', estado: '', q: '' })

  const [comercios, setComercios] = useState<EstadoListadoComercios>({ estado: 'cargando' })

  const cargarComercios = useCallback(
    async (filtros: { rubro: RubroBromatologico | ''; estado: EstadoBromatologico | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.rubro !== '') {
        parametros.set('rubro', filtros.rubro)
      }
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const listado = await pedir<Comercio[]>(
          `/api/bromatologia/comercios${query ? `?${query}` : ''}`,
          'No se pudo cargar el padrón de comercios.',
        )
        if (vigente.current) {
          setComercios({ estado: 'listo', comercios: listado })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setComercios({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'bromatologia' })
        } else {
          setComercios({
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
    // patrón que PantallaDeDefensaCivil): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarComercios(filtrosAplicados)
  }, [cargarComercios, filtrosAplicados])

  function buscarComercios(evento: FormEvent) {
    evento.preventDefault()
    setComercios({ estado: 'cargando' })
    setFiltrosAplicados({ rubro: rubroFiltro, estado: estadoFiltro, q: qFiltro })
  }

  // --- Alta de comercio ---

  const [formularioComercioAbierto, setFormularioComercioAbierto] = useState(false)
  const [registroComercio, setRegistroComercio] = useState<EstadoRegistroComercio>(REGISTRO_COMERCIO_INICIAL)

  const botonRegistrarComercio = useRef<HTMLButtonElement>(null)
  const primerCampoRegistroComercio = useRef<HTMLSelectElement>(null)
  const errorRegistroComercioRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioComercioAbierto) {
      primerCampoRegistroComercio.current?.focus()
    }
  }, [formularioComercioAbierto])

  useEffect(() => {
    if (registroComercio.error) {
      errorRegistroComercioRef.current?.focus()
    }
  }, [registroComercio.error])

  function abrirFormularioComercio() {
    setRegistroComercio(REGISTRO_COMERCIO_INICIAL)
    setFormularioComercioAbierto(true)
  }

  function cerrarFormularioComercio() {
    setFormularioComercioAbierto(false)
    botonRegistrarComercio.current?.focus()
  }

  async function registrarComercio(evento: FormEvent) {
    evento.preventDefault()
    setRegistroComercio((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/bromatologia/comercios',
        'POST',
        {
          rubro: registroComercio.rubro,
          nombre: registroComercio.nombre,
          direccion: registroComercio.direccion,
          fechaHabilitacion: registroComercio.fechaHabilitacion,
          fechaVencimientoHabilitacion: registroComercio.fechaVencimientoHabilitacion,
        },
        'No se pudo registrar el comercio.',
      )
      if (!vigente.current) {
        return
      }
      await cargarComercios(filtrosAplicados)
      if (vigente.current) {
        setFormularioComercioAbierto(false)
        botonRegistrarComercio.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistroComercio((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'bromatologia'),
        }))
      } else {
        setRegistroComercio((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar el comercio.',
        }))
      }
    }
  }

  const idDelErrorRegistroComercio = 'error-de-registro-comercio'

  // --- Historial de inspecciones, por fila (una a la vez) ---

  const [filaAbiertaId, setFilaAbiertaId] = useState<number | null>(null)
  const [inspeccionesDeFila, setInspeccionesDeFila] = useState<EstadoInspeccionesDeFila | null>(null)
  const [formularioInspeccion, setFormularioInspeccion] = useState<EstadoFormularioInspeccion>(
    FORMULARIO_INSPECCION_INICIAL,
  )
  const [confirmacionInspeccion, setConfirmacionInspeccion] = useState<string | null>(null)

  const botonesVerInspecciones = useRef<Map<number, HTMLButtonElement>>(new Map())
  const tituloInspecciones = useRef<HTMLHeadingElement>(null)
  const errorInspeccionRef = useRef<HTMLParagraphElement>(null)
  const confirmacionInspeccionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (filaAbiertaId !== null) {
      tituloInspecciones.current?.focus()
    }
  }, [filaAbiertaId])

  useEffect(() => {
    if (formularioInspeccion.error) {
      errorInspeccionRef.current?.focus()
    }
  }, [formularioInspeccion.error])

  useEffect(() => {
    if (confirmacionInspeccion) {
      confirmacionInspeccionRef.current?.focus()
    }
  }, [confirmacionInspeccion])

  const cargarInspecciones = useCallback(async (comercioId: number) => {
    try {
      const inspecciones = await pedir<Inspeccion[]>(
        `/api/bromatologia/comercios/${comercioId}/inspecciones`,
        'No se pudo cargar el historial de inspecciones de este comercio.',
      )
      if (vigente.current) {
        setInspeccionesDeFila({ estado: 'listo', inspecciones })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setInspeccionesDeFila({
        estado: 'error',
        mensaje:
          fallo instanceof Error ? fallo.message : 'No se pudo cargar el historial de inspecciones de este comercio.',
      })
    }
  }, [])

  function alternarInspecciones(comercio: Comercio) {
    setConfirmacionInspeccion(null)
    if (filaAbiertaId === comercio.id) {
      setFilaAbiertaId(null)
      setInspeccionesDeFila(null)
      setFormularioInspeccion(FORMULARIO_INSPECCION_INICIAL)
      botonesVerInspecciones.current.get(comercio.id)?.focus()
      return
    }
    setFilaAbiertaId(comercio.id)
    setInspeccionesDeFila({ estado: 'cargando' })
    setFormularioInspeccion(FORMULARIO_INSPECCION_INICIAL)
    void cargarInspecciones(comercio.id)
  }

  async function registrarInspeccion(evento: FormEvent, comercioId: number) {
    evento.preventDefault()
    setConfirmacionInspeccion(null)
    setFormularioInspeccion((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        `/api/bromatologia/comercios/${comercioId}/inspecciones`,
        'POST',
        {
          fecha: formularioInspeccion.fecha,
          resultado: formularioInspeccion.resultado,
          observaciones: formularioInspeccion.observaciones.trim() === '' ? null : formularioInspeccion.observaciones,
        },
        'No se pudo registrar la inspección.',
      )
      if (!vigente.current) {
        return
      }
      const resultadoRegistrado = formularioInspeccion.resultado
      await Promise.all([cargarInspecciones(comercioId), cargarComercios(filtrosAplicados)])
      if (vigente.current) {
        setFormularioInspeccion(FORMULARIO_INSPECCION_INICIAL)
        setConfirmacionInspeccion(
          `Se registró la inspección. Resultado: ${resultadoRegistrado !== '' ? ETIQUETA_ESTADO[resultadoRegistrado] : ''}.`,
        )
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setFormularioInspeccion((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'bromatologia'),
        }))
      } else {
        setFormularioInspeccion((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar la inspección.',
        }))
      }
    }
  }

  const columnas = puedeGestionar ? 6 : 5

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Bromatología'}
      </h1>
      <p className="contenido__bajada">
        Padrón de comercios habilitados por Bromatología, con su estado
        sanitario vigente. No hace falta tener cuenta ni iniciar sesión
        para consultarlo; el historial de inspecciones de cada comercio
        solo lo ve quien tiene sesión y permiso para gestionarlo.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="bromatologia-comercios-titulo">
        <h2 id="bromatologia-comercios-titulo">Padrón de comercios</h2>

        <form className="formulario" onSubmit={buscarComercios}>
          <div className="campo">
            <label htmlFor="bromatologia-filtro-rubro">Rubro</label>
            <select
              id="bromatologia-filtro-rubro"
              value={rubroFiltro}
              onChange={(evento) => setRubroFiltro(evento.target.value as RubroBromatologico | '')}
            >
              <option value="">Todos</option>
              {RUBROS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="bromatologia-filtro-estado">Estado</label>
            <select
              id="bromatologia-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoBromatologico | '')}
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
            <label htmlFor="bromatologia-filtro-q">Buscar en nombre o dirección</label>
            <input id="bromatologia-filtro-q" value={qFiltro} onChange={(evento) => setQFiltro(evento.target.value)} />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {comercios.estado === 'cargando' && <p role="status">Buscando comercios…</p>}

        {comercios.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? comercios.moduloDelError)}
          </p>
        )}

        {comercios.estado === 'error' && <p role="alert">{comercios.mensaje}</p>}

        {comercios.estado === 'listo' && comercios.comercios.length === 0 && <p>No se encontraron comercios.</p>}

        {comercios.estado === 'listo' && comercios.comercios.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Comercios habilitados por Bromatología, con su estado
                sanitario vigente. Se puede filtrar por rubro, estado y
                texto en el nombre o la dirección.
                {puedeGestionar &&
                  ' Se puede ver el historial de inspecciones de cada comercio y registrar una inspección nueva.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Rubro</th>
                  <th scope="col">Nombre</th>
                  <th scope="col">Dirección</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Vencimiento de habilitación</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {comercios.comercios.map((comercio) => {
                  const filaAbierta = filaAbiertaId === comercio.id

                  return (
                    <Fragment key={comercio.id}>
                      <tr>
                        <th scope="row">{ETIQUETA_RUBRO[comercio.rubro]}</th>
                        <td>{comercio.nombre}</td>
                        <td>{comercio.direccion}</td>
                        <td>{ETIQUETA_ESTADO[comercio.estado]}</td>
                        <td>{formatearFecha(comercio.fechaVencimientoHabilitacion)}</td>
                        {puedeGestionar && (
                          <td>
                            <button
                              type="button"
                              className="boton boton--secundario"
                              aria-expanded={filaAbierta}
                              ref={(elemento) => {
                                if (elemento) {
                                  botonesVerInspecciones.current.set(comercio.id, elemento)
                                } else {
                                  botonesVerInspecciones.current.delete(comercio.id)
                                }
                              }}
                              onClick={() => alternarInspecciones(comercio)}
                            >
                              {filaAbierta ? 'Ocultar inspecciones' : 'Ver inspecciones'}
                            </button>
                          </td>
                        )}
                      </tr>

                      {filaAbierta && (
                        <tr>
                          <td colSpan={columnas}>
                            <h3 ref={tituloInspecciones} tabIndex={-1}>
                              Inspecciones — {comercio.nombre}
                            </h3>

                            {inspeccionesDeFila?.estado === 'cargando' && (
                              <p role="status">Cargando historial de inspecciones…</p>
                            )}

                            {inspeccionesDeFila?.estado === 'error' && (
                              <p role="alert">{inspeccionesDeFila.mensaje}</p>
                            )}

                            {inspeccionesDeFila?.estado === 'listo' && inspeccionesDeFila.inspecciones.length === 0 && (
                              <p>Todavía no hay inspecciones registradas para este comercio.</p>
                            )}

                            {inspeccionesDeFila?.estado === 'listo' && inspeccionesDeFila.inspecciones.length > 0 && (
                              <div className="tabla-contenedor">
                                <table className="tabla">
                                  <caption>Historial de inspecciones registradas para {comercio.nombre}.</caption>
                                  <thead>
                                    <tr>
                                      <th scope="col">Fecha</th>
                                      <th scope="col">Resultado</th>
                                      <th scope="col">Observaciones</th>
                                      <th scope="col">Inspeccionado por</th>
                                    </tr>
                                  </thead>
                                  <tbody>
                                    {inspeccionesDeFila.inspecciones.map((inspeccion) => (
                                      <tr key={inspeccion.id}>
                                        <th scope="row">{formatearFecha(inspeccion.fecha)}</th>
                                        <td>{ETIQUETA_ESTADO[inspeccion.resultado]}</td>
                                        <td>{textoOVacio(inspeccion.observaciones)}</td>
                                        <td>{inspeccion.inspeccionadoPorNombre}</td>
                                      </tr>
                                    ))}
                                  </tbody>
                                </table>
                              </div>
                            )}

                            {confirmacionInspeccion && (
                              <p role="status" tabIndex={-1} ref={confirmacionInspeccionRef}>
                                {confirmacionInspeccion}
                              </p>
                            )}

                            <form
                              className="formulario"
                              aria-labelledby={`bromatologia-comercio-${comercio.id}-registrar-inspeccion-titulo`}
                              onSubmit={(evento) => void registrarInspeccion(evento, comercio.id)}
                            >
                              <h4 id={`bromatologia-comercio-${comercio.id}-registrar-inspeccion-titulo`}>
                                Registrar inspección
                              </h4>

                              {formularioInspeccion.error && (
                                <p
                                  className="formulario__error"
                                  id={`bromatologia-comercio-${comercio.id}-error-inspeccion`}
                                  role="alert"
                                  tabIndex={-1}
                                  ref={errorInspeccionRef}
                                >
                                  {formularioInspeccion.error}
                                </p>
                              )}

                              <div className="campo">
                                <label htmlFor={`bromatologia-comercio-${comercio.id}-inspeccion-fecha`}>Fecha</label>
                                <input
                                  id={`bromatologia-comercio-${comercio.id}-inspeccion-fecha`}
                                  type="date"
                                  required
                                  value={formularioInspeccion.fecha}
                                  onChange={(evento) =>
                                    setFormularioInspeccion((actual) => ({ ...actual, fecha: evento.target.value }))
                                  }
                                  aria-invalid={formularioInspeccion.error ? true : undefined}
                                  aria-describedby={
                                    formularioInspeccion.error
                                      ? `bromatologia-comercio-${comercio.id}-error-inspeccion`
                                      : undefined
                                  }
                                />
                              </div>

                              <div className="campo">
                                <label htmlFor={`bromatologia-comercio-${comercio.id}-inspeccion-resultado`}>
                                  Resultado
                                </label>
                                <select
                                  id={`bromatologia-comercio-${comercio.id}-inspeccion-resultado`}
                                  required
                                  value={formularioInspeccion.resultado}
                                  onChange={(evento) =>
                                    setFormularioInspeccion((actual) => ({
                                      ...actual,
                                      resultado: evento.target.value as EstadoBromatologico,
                                    }))
                                  }
                                  aria-invalid={formularioInspeccion.error ? true : undefined}
                                  aria-describedby={
                                    formularioInspeccion.error
                                      ? `bromatologia-comercio-${comercio.id}-error-inspeccion`
                                      : undefined
                                  }
                                >
                                  <option value="" disabled>
                                    Elegí un resultado
                                  </option>
                                  {ESTADOS.map((opcion) => (
                                    <option key={opcion.valor} value={opcion.valor}>
                                      {opcion.etiqueta}
                                    </option>
                                  ))}
                                </select>
                              </div>

                              <div className="campo">
                                <label htmlFor={`bromatologia-comercio-${comercio.id}-inspeccion-observaciones`}>
                                  Observaciones (opcional)
                                </label>
                                <textarea
                                  id={`bromatologia-comercio-${comercio.id}-inspeccion-observaciones`}
                                  value={formularioInspeccion.observaciones}
                                  onChange={(evento) =>
                                    setFormularioInspeccion((actual) => ({
                                      ...actual,
                                      observaciones: evento.target.value,
                                    }))
                                  }
                                />
                              </div>

                              <div className="formulario__acciones">
                                <button
                                  type="submit"
                                  className="boton"
                                  disabled={formularioInspeccion.enviando}
                                  aria-busy={formularioInspeccion.enviando}
                                >
                                  {formularioInspeccion.enviando ? 'Registrando…' : 'Registrar inspección'}
                                </button>
                              </div>
                            </form>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}

        {puedeGestionar && (
          <div>
            <h3 id="bromatologia-registrar-comercio-titulo">Registrar comercio</h3>

            {!formularioComercioAbierto ? (
              <div className="administracion__barra">
                <button
                  type="button"
                  className="boton"
                  ref={botonRegistrarComercio}
                  onClick={abrirFormularioComercio}
                >
                  Registrar comercio
                </button>
              </div>
            ) : (
              <form
                className="formulario"
                aria-labelledby="bromatologia-registrar-comercio-titulo"
                onSubmit={(evento) => void registrarComercio(evento)}
              >
                {registroComercio.error && (
                  <p
                    className="formulario__error"
                    id={idDelErrorRegistroComercio}
                    role="alert"
                    tabIndex={-1}
                    ref={errorRegistroComercioRef}
                  >
                    {registroComercio.error}
                  </p>
                )}

                <div className="campo">
                  <label htmlFor="bromatologia-comercio-rubro">Rubro</label>
                  <select
                    id="bromatologia-comercio-rubro"
                    ref={primerCampoRegistroComercio}
                    required
                    value={registroComercio.rubro}
                    onChange={(evento) =>
                      setRegistroComercio((actual) => ({
                        ...actual,
                        rubro: evento.target.value as RubroBromatologico,
                      }))
                    }
                    aria-invalid={registroComercio.error ? true : undefined}
                    aria-describedby={registroComercio.error ? idDelErrorRegistroComercio : undefined}
                  >
                    <option value="" disabled>
                      Elegí un rubro
                    </option>
                    {RUBROS.map((opcion) => (
                      <option key={opcion.valor} value={opcion.valor}>
                        {opcion.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="campo">
                  <label htmlFor="bromatologia-comercio-nombre">Nombre</label>
                  <input
                    id="bromatologia-comercio-nombre"
                    required
                    value={registroComercio.nombre}
                    onChange={(evento) =>
                      setRegistroComercio((actual) => ({ ...actual, nombre: evento.target.value }))
                    }
                    aria-invalid={registroComercio.error ? true : undefined}
                    aria-describedby={registroComercio.error ? idDelErrorRegistroComercio : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="bromatologia-comercio-direccion">Dirección</label>
                  <input
                    id="bromatologia-comercio-direccion"
                    required
                    value={registroComercio.direccion}
                    onChange={(evento) =>
                      setRegistroComercio((actual) => ({ ...actual, direccion: evento.target.value }))
                    }
                    aria-invalid={registroComercio.error ? true : undefined}
                    aria-describedby={registroComercio.error ? idDelErrorRegistroComercio : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="bromatologia-comercio-fecha-habilitacion">Fecha de habilitación</label>
                  <input
                    id="bromatologia-comercio-fecha-habilitacion"
                    type="date"
                    required
                    value={registroComercio.fechaHabilitacion}
                    onChange={(evento) =>
                      setRegistroComercio((actual) => ({ ...actual, fechaHabilitacion: evento.target.value }))
                    }
                    aria-invalid={registroComercio.error ? true : undefined}
                    aria-describedby={registroComercio.error ? idDelErrorRegistroComercio : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="bromatologia-comercio-fecha-vencimiento">Fecha de vencimiento de la habilitación</label>
                  <input
                    id="bromatologia-comercio-fecha-vencimiento"
                    type="date"
                    required
                    value={registroComercio.fechaVencimientoHabilitacion}
                    onChange={(evento) =>
                      setRegistroComercio((actual) => ({
                        ...actual,
                        fechaVencimientoHabilitacion: evento.target.value,
                      }))
                    }
                    aria-invalid={registroComercio.error ? true : undefined}
                    aria-describedby={registroComercio.error ? idDelErrorRegistroComercio : undefined}
                  />
                </div>

                <div className="formulario__acciones">
                  <button
                    type="submit"
                    className="boton"
                    disabled={registroComercio.enviando}
                    aria-busy={registroComercio.enviando}
                  >
                    {registroComercio.enviando ? 'Registrando…' : 'Registrar'}
                  </button>
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={cerrarFormularioComercio}
                    disabled={registroComercio.enviando}
                  >
                    Cancelar
                  </button>
                </div>
              </form>
            )}
          </div>
        )}
      </section>
    </main>
  )
}
