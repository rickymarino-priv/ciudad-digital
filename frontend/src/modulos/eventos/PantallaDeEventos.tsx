import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type CategoriaDeEvento = 'CULTURA' | 'TURISMO' | 'DEPORTE' | 'OTRA'

type EstadoDeEvento = 'PROGRAMADO' | 'CANCELADO'

type Evento = {
  id: number
  nombre: string
  categoria: CategoriaDeEvento
  ubicacion: string
  descripcion: string | null
  fechaInicio: string
  fechaFin: string | null
  horaInicio: string | null
  estado: EstadoDeEvento
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const CATEGORIAS: { valor: CategoriaDeEvento; etiqueta: string }[] = [
  { valor: 'CULTURA', etiqueta: 'Cultura' },
  { valor: 'TURISMO', etiqueta: 'Turismo' },
  { valor: 'DEPORTE', etiqueta: 'Deporte' },
  { valor: 'OTRA', etiqueta: 'Otra' },
]

const ETIQUETA_CATEGORIA: Record<CategoriaDeEvento, string> = CATEGORIAS.reduce(
  (mapa, categoria) => ({ ...mapa, [categoria.valor]: categoria.etiqueta }),
  {} as Record<CategoriaDeEvento, string>,
)

const ESTADOS: { valor: EstadoDeEvento; etiqueta: string }[] = [
  { valor: 'PROGRAMADO', etiqueta: 'Programado' },
  { valor: 'CANCELADO', etiqueta: 'Cancelado' },
]

const ETIQUETA_ESTADO: Record<EstadoDeEvento, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeEvento, string>,
)

const FECHA = new Intl.DateTimeFormat('es-AR', { day: 'numeric', month: 'short' })

/** Mismo texto que en `PantallaDeObras`/`PantallaDeEspaciosVerdes` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario, mismo
 * criterio que `PantallaDeObras#formatearFecha`: pasarle ese string directo
 * a `new Date(...)` lo interpreta en UTC, y en un huso negativo puede
 * mostrar el día anterior. Se arma la fecha a partir de los componentes, en
 * la zona local.
 */
function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

/** `fechaInicio` sola, o el rango "15 oct al 17 oct" si `fechaFin` está cargada y difiere de `fechaInicio`. */
function formatearRangoDeFechas(fechaInicio: string, fechaFin: string | null): string {
  if (fechaFin !== null && fechaFin !== fechaInicio) {
    return `${formatearFecha(fechaInicio)} al ${formatearFecha(fechaFin)}`
  }
  return formatearFecha(fechaInicio)
}

/** `"09:00:00"` (o `"09:00"`) → `"09:00hs"`: el back manda `LocalTime`, que
 * puede llegar con o sin segundos; a la vista solo le importan hora y
 * minuto, mismo criterio que `PantallaDeTurnos#formatearHora`. */
function formatearHora(horaIso: string): string {
  return `${horaIso.slice(0, 5)}hs`
}

/** Fecha (o rango) del evento, con la hora agregada si `horaInicio` no es `null`, ej. "15 oct, 9:00hs". */
function formatearFechaDeEvento(evento: Evento): string {
  const rango = formatearRangoDeFechas(evento.fechaInicio, evento.fechaFin)
  return evento.horaInicio !== null ? `${rango}, ${formatearHora(evento.horaInicio)}` : rango
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; eventos: Evento[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  nombre: string
  categoria: CategoriaDeEvento | ''
  ubicacion: string
  fechaInicio: string
  fechaFin: string
  horaInicio: string
  descripcion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  nombre: '',
  categoria: '',
  ubicacion: '',
  fechaInicio: '',
  fechaFin: '',
  horaInicio: '',
  descripcion: '',
  enviando: false,
  error: null,
}

type EstadoCancelacion = {
  id: number
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `eventos`: agenda pública de eventos culturales,
 * turísticos y deportivos (sin sesión) y, dentro de la misma vista, la
 * acción de publicar un evento nuevo y de cancelarlo, visibles solo para
 * quien tiene `eventos.gestionar` (ADR 0011: se esconde por comodidad, el
 * backend vuelve a exigir el permiso). Mismo patrón exacto que
 * `PantallaDeEspaciosVerdes`/`PantallaDeObras` — no el de
 * `PantallaDeReclamos`, que muestra vistas *alternativas* según permiso:
 * acá el listado es el mismo para todos, solo cambia qué acciones se ven
 * (ADR 0030).
 *
 * A diferencia de `PantallaDeEspaciosVerdes`/`PantallaDeObras`, la
 * cancelación no usa un `<select>` de estado destino: solo hay una
 * transición posible (`PROGRAMADO → CANCELADO`, ADR 0030 §3), así que el
 * botón por fila dispara el `PATCH` directo, con confirmación previa.
 */
export function PantallaDeEventos({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('eventos.gestionar') ?? false

  const [categoriaFiltro, setCategoriaFiltro] = useState<CategoriaDeEvento | ''>('')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeEvento | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    categoria: CategoriaDeEvento | ''
    estado: EstadoDeEvento | ''
    q: string
  }>({ categoria: '', estado: '', q: '' })

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

  const cargarEventos = useCallback(
    async (filtros: { categoria: CategoriaDeEvento | ''; estado: EstadoDeEvento | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.categoria !== '') {
        parametros.set('categoria', filtros.categoria)
      }
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        // El listado ya viene ordenado por fechaInicio ascendente y luego
        // nombre desde el backend (ADR 0030 §4): no se reordena acá.
        const eventos = await pedir<Evento[]>(
          `/api/eventos${query ? `?${query}` : ''}`,
          'No se pudo cargar la agenda de eventos.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', eventos })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'eventos' })
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
    // patrón que PantallaDeObras/PantallaDeEspaciosVerdes): el setState
    // está protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarEventos(filtrosAplicados)
  }, [cargarEventos, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ categoria: categoriaFiltro, estado: estadoFiltro, q: qFiltro })
  }

  // --- Publicación de evento ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [registro, setRegistro] = useState<EstadoRegistro>(REGISTRO_INICIAL)

  const botonPublicar = useRef<HTMLButtonElement>(null)
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
    botonPublicar.current?.focus()
  }

  async function publicarEvento(evento: FormEvent) {
    evento.preventDefault()

    // Misma validación que `GestionDeEventos#publicar` en el backend
    // (ADR 0030 §1): se duplica acá para dar el error sin ida y vuelta al
    // servidor, pero el backend la vuelve a hacer igual, es la que cuenta.
    if (
      registro.fechaFin.trim() !== '' &&
      registro.fechaInicio.trim() !== '' &&
      registro.fechaFin < registro.fechaInicio
    ) {
      setRegistro((actual) => ({
        ...actual,
        error: 'La fecha de fin no puede ser anterior a la fecha de inicio.',
      }))
      return
    }

    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/eventos',
        'POST',
        {
          nombre: registro.nombre,
          categoria: registro.categoria,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
          fechaInicio: registro.fechaInicio,
          fechaFin: registro.fechaFin.trim() === '' ? null : registro.fechaFin,
          horaInicio: registro.horaInicio.trim() === '' ? null : registro.horaInicio,
        },
        'No se pudo publicar el evento.',
      )
      if (!vigente.current) {
        return
      }
      await cargarEventos(filtrosAplicados)
      if (vigente.current) {
        setFormularioAbierto(false)
        botonPublicar.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'eventos'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar el evento.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-evento'

  // --- Cancelación por fila ---
  //
  // A diferencia de PantallaDeEspaciosVerdes/PantallaDeObras no hay un
  // `<select>` de estado destino: la única transición posible es
  // PROGRAMADO → CANCELADO (ADR 0030 §3), así que el botón dispara
  // directo el PATCH, con `window.confirm` como confirmación previa (no
  // hay todavía un diálogo de confirmación accesible propio en el
  // proyecto).

  const [cancelacion, setCancelacion] = useState<EstadoCancelacion | null>(null)
  const [eventoCancelado, setEventoCancelado] = useState<string | null>(null)
  const errorCancelacionRef = useRef<HTMLParagraphElement>(null)
  const confirmacionCancelacionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (cancelacion?.error) {
      errorCancelacionRef.current?.focus()
    }
  }, [cancelacion?.error])

  useEffect(() => {
    if (eventoCancelado) {
      confirmacionCancelacionRef.current?.focus()
    }
  }, [eventoCancelado])

  async function cancelarEvento(evento: Evento) {
    if (!window.confirm(`¿Cancelar el evento "${evento.nombre}"? Esta acción no se puede deshacer.`)) {
      return
    }
    setEventoCancelado(null)
    setCancelacion({ id: evento.id, enviando: true, error: null })
    try {
      await enviar(
        `/api/eventos/${evento.id}/estado`,
        'PATCH',
        { estadoNuevo: 'CANCELADO' },
        'No se pudo cancelar el evento.',
      )
      if (!vigente.current) {
        return
      }
      await cargarEventos(filtrosAplicados)
      if (vigente.current) {
        setCancelacion(null)
        setEventoCancelado(evento.nombre)
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setCancelacion({
          id: evento.id,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo cancelar el evento.',
        })
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Cultura, Turismo y Deportes'}
      </h1>
      <p className="contenido__bajada">
        Agenda de eventos culturales, turísticos y deportivos organizados
        por el municipio: fecha, ubicación y estado. No hace falta tener
        cuenta ni iniciar sesión para consultarla.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar eventos</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="eventos-filtro-categoria">Categoría</label>
            <select
              id="eventos-filtro-categoria"
              value={categoriaFiltro}
              onChange={(evento) => setCategoriaFiltro(evento.target.value as CategoriaDeEvento | '')}
            >
              <option value="">Todas</option>
              {CATEGORIAS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="eventos-filtro-estado">Estado</label>
            <select
              id="eventos-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeEvento | '')}
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
            <label htmlFor="eventos-filtro-q">Buscar en nombre o ubicación</label>
            <input id="eventos-filtro-q" value={qFiltro} onChange={(evento) => setQFiltro(evento.target.value)} />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estado.estado === 'cargando' && <p role="status">Buscando eventos…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.eventos.length === 0 && <p>No se encontraron eventos.</p>}

        {estado.estado === 'listo' && estado.eventos.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Agenda de eventos culturales, turísticos y deportivos
                publicados por el municipio, ordenada por fecha. Se puede
                filtrar por categoría, estado y texto en el nombre o la
                ubicación.
                {puedeGestionar && ' Se puede cancelar un evento que todavía esté programado.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Categoría</th>
                  <th scope="col">Fecha</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Estado</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estado.eventos.map((evento) => (
                  <tr key={evento.id}>
                    <th scope="row">{evento.nombre}</th>
                    <td>{ETIQUETA_CATEGORIA[evento.categoria]}</td>
                    <td>{formatearFechaDeEvento(evento)}</td>
                    <td>{evento.ubicacion}</td>
                    <td>{ETIQUETA_ESTADO[evento.estado]}</td>
                    {puedeGestionar && (
                      <td>
                        {evento.estado === 'PROGRAMADO' ? (
                          <button
                            type="button"
                            className="boton boton--secundario"
                            disabled={cancelacion?.id === evento.id && cancelacion.enviando}
                            aria-busy={cancelacion?.id === evento.id && cancelacion.enviando}
                            onClick={() => void cancelarEvento(evento)}
                          >
                            {cancelacion?.id === evento.id && cancelacion.enviando
                              ? 'Cancelando…'
                              : 'Cancelar evento'}
                          </button>
                        ) : (
                          'Sin acciones disponibles'
                        )}
                      </td>
                    )}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}

        {cancelacion?.error && (
          <p className="formulario__error" role="alert" tabIndex={-1} ref={errorCancelacionRef}>
            {cancelacion.error}
          </p>
        )}

        {eventoCancelado && (
          <p role="status" tabIndex={-1} ref={confirmacionCancelacionRef}>
            Se canceló el evento «{eventoCancelado}».
          </p>
        )}
      </section>

      {puedeGestionar && (
        <section aria-labelledby="titulo-publicar">
          <h2 id="titulo-publicar">Publicar evento</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonPublicar} onClick={abrirFormulario}>
                Publicar evento
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarEvento(evento)}>
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
                <label htmlFor="eventos-nombre">Nombre</label>
                <input
                  id="eventos-nombre"
                  ref={primerCampoRegistro}
                  required
                  value={registro.nombre}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, nombre: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="eventos-categoria">Categoría</label>
                <select
                  id="eventos-categoria"
                  required
                  value={registro.categoria}
                  onChange={(evento) =>
                    setRegistro((actual) => ({
                      ...actual,
                      categoria: evento.target.value as CategoriaDeEvento,
                    }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                >
                  <option value="" disabled>
                    Elegí una categoría
                  </option>
                  {CATEGORIAS.map((opcion) => (
                    <option key={opcion.valor} value={opcion.valor}>
                      {opcion.etiqueta}
                    </option>
                  ))}
                </select>
              </div>

              <div className="campo">
                <label htmlFor="eventos-ubicacion">Ubicación</label>
                <input
                  id="eventos-ubicacion"
                  required
                  value={registro.ubicacion}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, ubicacion: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="eventos-fecha-inicio">Fecha de inicio</label>
                <input
                  id="eventos-fecha-inicio"
                  type="date"
                  required
                  value={registro.fechaInicio}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, fechaInicio: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="eventos-fecha-fin">Fecha de fin (opcional)</label>
                <input
                  id="eventos-fecha-fin"
                  type="date"
                  value={registro.fechaFin}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, fechaFin: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="eventos-hora-inicio">Hora de inicio (opcional)</label>
                <input
                  id="eventos-hora-inicio"
                  type="time"
                  value={registro.horaInicio}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, horaInicio: evento.target.value }))}
                />
              </div>

              <div className="campo">
                <label htmlFor="eventos-descripcion">Descripción (opcional)</label>
                <textarea
                  id="eventos-descripcion"
                  value={registro.descripcion}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, descripcion: evento.target.value }))}
                />
              </div>

              <div className="formulario__acciones">
                <button type="submit" className="boton" disabled={registro.enviando} aria-busy={registro.enviando}>
                  {registro.enviando ? 'Publicando…' : 'Publicar'}
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
