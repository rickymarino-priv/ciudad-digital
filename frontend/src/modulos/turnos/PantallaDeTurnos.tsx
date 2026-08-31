import { Fragment, useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type TipoDeActividad = 'DEPORTE' | 'CULTURA' | 'TURISMO'

type EstadoDeActividad = 'ACTIVA' | 'INACTIVA'

type Actividad = {
  id: number
  nombre: string
  tipo: TipoDeActividad
  descripcion: string | null
  ubicacion: string
  estado: EstadoDeActividad
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

type FranjaHoraria = {
  id: number
  actividadId: number
  fecha: string
  horaInicio: string
  horaFin: string
  cupoTotal: number
  cupoDisponible: number
  creadoEn: string
}

/** Respuesta de `POST /api/turnos/reservas`: deliberadamente sin
 * nombre/DNI/contacto del vecino (ADR 0026 §4) — él ya los tiene. */
type ReservaPublica = {
  id: number
  nombreActividad: string
  fecha: string
  horaInicio: string
  horaFin: string
  cupoDisponibleRestante: number
}

/** Respuesta de `GET /api/turnos/reservas?franjaId=...`: solo la ve quien
 * tiene `turnos.gestionar` (ADR 0026 §5), con los datos completos. */
type TurnoDeGestion = {
  id: number
  franjaId: number
  nombreSolicitante: string
  dniSolicitante: string
  contacto: string
  creadoEn: string
}

/** Lo mínimo que necesitan `FormularioDeReserva`/`AgendaDeFranja` de la
 * franja elegida en el catálogo: se guarda en estado local al navegar
 * (ADR 0008, sin router), sin volver a pedirlo al backend. */
type FranjaSeleccionada = {
  id: number
  nombreActividad: string
  fecha: string
  horaInicio: string
  horaFin: string
  cupoDisponible: number
}

const TIPOS_DE_ACTIVIDAD: { valor: TipoDeActividad; etiqueta: string }[] = [
  { valor: 'DEPORTE', etiqueta: 'Deporte' },
  { valor: 'CULTURA', etiqueta: 'Cultura' },
  { valor: 'TURISMO', etiqueta: 'Turismo' },
]

const ETIQUETA_TIPO_ACTIVIDAD: Record<TipoDeActividad, string> = TIPOS_DE_ACTIVIDAD.reduce(
  (mapa, opcion) => ({ ...mapa, [opcion.valor]: opcion.etiqueta }),
  {} as Record<TipoDeActividad, string>,
)

const ESTADOS_DE_ACTIVIDAD: { valor: EstadoDeActividad; etiqueta: string }[] = [
  { valor: 'ACTIVA', etiqueta: 'Activa' },
  { valor: 'INACTIVA', etiqueta: 'Inactiva' },
]

const ETIQUETA_ESTADO_ACTIVIDAD: Record<EstadoDeActividad, string> = {
  ACTIVA: 'Activa',
  INACTIVA: 'Inactiva',
}

/** Único destino posible desde cada estado (mismo criterio que
 * `OPUESTO_ESTADO_PROGRAMA` en `PantallaDeDesarrolloSocial`, ADR 0026 §3):
 * a diferencia de Obras/Arbolado no hace falta un mapa de varias opciones
 * por estado. */
const OPUESTO_ESTADO_ACTIVIDAD: Record<EstadoDeActividad, EstadoDeActividad> = {
  ACTIVA: 'INACTIVA',
  INACTIVA: 'ACTIVA',
}

/** Mismo texto exacto que devuelve `TurnosController` cuando el cupo se
 * agotó justo al confirmar (409 `CupoAgotado`, ADR 0026 §4): el backend no
 * manda un `codigo` machine-readable para este caso (solo lo hace para
 * `MODULO_NO_CONTRATADO`), así que la única forma de distinguirlo de
 * `ReservaDuplicada` — también 409 — es este texto fijo. */
const MENSAJE_CUPO_AGOTADO = 'No queda cupo disponible para esta franja.'

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })
const FECHA_HORA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en `PantallaDeObras`/`PantallaDeArbolado` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario, mismo
 * criterio que `PantallaDeArbolado#formatearFecha`: pasarle ese string
 * directo a `new Date(...)` lo interpreta en UTC, y en un huso negativo
 * puede mostrar el día anterior. Se arma la fecha a partir de los
 * componentes, en la zona local.
 */
function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

/** `"10:00:00"` (o `"10:00"`) → `"10:00"`: el back manda `LocalTime`, que
 * puede llegar con o sin segundos; a la vista solo le importan hora y
 * minuto. */
function formatearHora(hora: string): string {
  return hora.slice(0, 5)
}

type EstadoCatalogo =
  | { estado: 'cargando' }
  | { estado: 'listo'; actividades: Actividad[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistroActividad = {
  nombre: string
  tipo: TipoDeActividad | ''
  ubicacion: string
  descripcion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_ACTIVIDAD_INICIAL: EstadoRegistroActividad = {
  nombre: '',
  tipo: '',
  ubicacion: '',
  descripcion: '',
  enviando: false,
  error: null,
}

type EdicionEstadoActividad = {
  id: number
  estadoNuevo: EstadoDeActividad
  enviando: boolean
  error: string | null
}

type EstadoFranjasDeFila =
  | { estado: 'cargando' }
  | { estado: 'listo'; franjas: FranjaHoraria[] }
  | { estado: 'error'; mensaje: string }

type EstadoFormularioFranja = {
  fecha: string
  horaInicio: string
  horaFin: string
  cupoTotal: string
  enviando: boolean
  error: string | null
}

const FORMULARIO_FRANJA_INICIAL: EstadoFormularioFranja = {
  fecha: '',
  horaInicio: '',
  horaFin: '',
  cupoTotal: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `turnos` (ADR 0026): igual que `PantallaDeDesarrolloSocial`,
 * combina tres audiencias — el vecino anónimo que consulta el catálogo y
 * reserva un turno, y quien gestiona la agenda con `turnos.gestionar`.
 * Navegación por estado local, sin router (ADR 0008): `catalogo` (default,
 * público), `reserva` (pública, formulario de alta contra la franja
 * elegida) y `agenda` (protegida por permiso — el backend vuelve a
 * exigirlo, ADR 0011, esto solo esconde el control por comodidad).
 *
 * Dentro de `catalogo`, cada actividad `ACTIVA` se expande en acordeón
 * (una sola a la vez, mismo criterio que el patrón "una fila en modo
 * edición" ya usado en `PantallaDeArbolado`/`PantallaDeDesarrolloSocial`)
 * para mostrar y, si corresponde, administrar sus franjas horarias.
 */
export function PantallaDeTurnos({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('turnos.gestionar') ?? false

  const [vista, setVista] = useState<'catalogo' | 'reserva' | 'agenda'>('catalogo')
  const [franjaParaReserva, setFranjaParaReserva] = useState<FranjaSeleccionada | null>(null)
  const [franjaParaAgenda, setFranjaParaAgenda] = useState<FranjaSeleccionada | null>(null)

  // Si alguien sin el permiso llega a `agenda` por cualquier motivo, la
  // protección real es el backend (ADR 0011), pero no le mostramos la
  // sección a quien no la va a poder usar: la mandamos de vuelta al
  // catálogo en vez de renderizarla.
  useEffect(() => {
    if (vista === 'agenda' && !puedeGestionar) {
      setVista('catalogo')
    }
  }, [vista, puedeGestionar])

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  // --- Catálogo de actividades ---

  const [tipoFiltro, setTipoFiltro] = useState<TipoDeActividad | ''>('')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeActividad | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    tipo: TipoDeActividad | ''
    estado: EstadoDeActividad | ''
    q: string
  }>({ tipo: '', estado: '', q: '' })

  const [catalogo, setCatalogo] = useState<EstadoCatalogo>({ estado: 'cargando' })

  const cargarActividades = useCallback(
    async (filtros: { tipo: TipoDeActividad | ''; estado: EstadoDeActividad | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.tipo !== '') {
        parametros.set('tipo', filtros.tipo)
      }
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const actividades = await pedir<Actividad[]>(
          `/api/turnos/actividades${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de actividades.',
        )
        if (vigente.current) {
          setCatalogo({ estado: 'listo', actividades })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setCatalogo({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'turnos' })
        } else {
          setCatalogo({
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
    // patrón que PantallaDeArbolado): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarActividades(filtrosAplicados)
  }, [cargarActividades, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    if (vista === 'catalogo') {
      titulo.current?.focus()
    }
  }, [vista])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setCatalogo({ estado: 'cargando' })
    setFiltrosAplicados({ tipo: tipoFiltro, estado: estadoFiltro, q: qFiltro })
  }

  // --- Publicar una actividad ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [registro, setRegistro] = useState<EstadoRegistroActividad>(REGISTRO_ACTIVIDAD_INICIAL)

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

  function abrirFormularioDePublicacion() {
    setRegistro(REGISTRO_ACTIVIDAD_INICIAL)
    setFormularioAbierto(true)
  }

  function cerrarFormularioDePublicacion() {
    setFormularioAbierto(false)
    botonRegistrar.current?.focus()
  }

  async function publicarActividad(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/turnos/actividades',
        'POST',
        {
          nombre: registro.nombre,
          tipo: registro.tipo,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
        },
        'No se pudo publicar la actividad.',
      )
      if (!vigente.current) {
        return
      }
      await cargarActividades(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'turnos'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la actividad.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-publicacion-actividad'

  // --- Cambio de estado de una actividad, por fila ---

  const [edicion, setEdicion] = useState<EdicionEstadoActividad | null>(null)
  const botonesCambiarEstado = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLSelectElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el error no hay que robarle
    // el foco a lo que esté tocando, mismo criterio que PantallaDeArbolado.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(actividad: Actividad) {
    setEdicion({ id: actividad.id, estadoNuevo: OPUESTO_ESTADO_ACTIVIDAD[actividad.estado], enviando: false, error: null })
  }

  function cerrarEdicion(idActividad: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idActividad)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/turnos/actividades/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado de la actividad.',
      )
      await cargarActividades(filtrosAplicados)
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
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado de la actividad.',
              }
            : actual,
        )
      }
    }
  }

  // --- Franjas horarias de la actividad expandida (una fila a la vez) ---

  const [filaAbiertaId, setFilaAbiertaId] = useState<number | null>(null)
  const [franjasDeFila, setFranjasDeFila] = useState<EstadoFranjasDeFila | null>(null)
  const [formularioFranja, setFormularioFranja] = useState<EstadoFormularioFranja>(FORMULARIO_FRANJA_INICIAL)

  const botonesVerFranjas = useRef<Map<number, HTMLButtonElement>>(new Map())
  const tituloFranjas = useRef<HTMLHeadingElement>(null)
  const errorFranjaRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (filaAbiertaId !== null) {
      tituloFranjas.current?.focus()
    }
  }, [filaAbiertaId])

  useEffect(() => {
    if (formularioFranja.error) {
      errorFranjaRef.current?.focus()
    }
  }, [formularioFranja.error])

  const cargarFranjas = useCallback(async (actividadId: number) => {
    try {
      const franjas = await pedir<FranjaHoraria[]>(
        `/api/turnos/franjas?actividadId=${actividadId}`,
        'No se pudieron cargar las franjas horarias de esta actividad.',
      )
      if (vigente.current) {
        setFranjasDeFila({ estado: 'listo', franjas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setFranjasDeFila({
        estado: 'error',
        mensaje: fallo instanceof Error ? fallo.message : 'No se pudieron cargar las franjas horarias de esta actividad.',
      })
    }
  }, [])

  function alternarFranjas(actividad: Actividad) {
    if (filaAbiertaId === actividad.id) {
      setFilaAbiertaId(null)
      setFranjasDeFila(null)
      setFormularioFranja(FORMULARIO_FRANJA_INICIAL)
      botonesVerFranjas.current.get(actividad.id)?.focus()
      return
    }
    setFilaAbiertaId(actividad.id)
    setFranjasDeFila({ estado: 'cargando' })
    setFormularioFranja(FORMULARIO_FRANJA_INICIAL)
    void cargarFranjas(actividad.id)
  }

  async function agregarFranja(evento: FormEvent, actividadId: number) {
    evento.preventDefault()
    setFormularioFranja((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        `/api/turnos/actividades/${actividadId}/franjas`,
        'POST',
        {
          fecha: formularioFranja.fecha,
          horaInicio: formularioFranja.horaInicio,
          horaFin: formularioFranja.horaFin,
          cupoTotal: formularioFranja.cupoTotal === '' ? null : Number(formularioFranja.cupoTotal),
        },
        'No se pudo agregar la franja horaria.',
      )
      if (!vigente.current) {
        return
      }
      await cargarFranjas(actividadId)
      if (vigente.current) {
        setFormularioFranja(FORMULARIO_FRANJA_INICIAL)
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setFormularioFranja((actual) => ({
        ...actual,
        enviando: false,
        error: fallo instanceof Error ? fallo.message : 'No se pudo agregar la franja horaria.',
      }))
    }
  }

  function irAReservar(actividad: Actividad, franja: FranjaHoraria) {
    setFranjaParaReserva({
      id: franja.id,
      nombreActividad: actividad.nombre,
      fecha: franja.fecha,
      horaInicio: franja.horaInicio,
      horaFin: franja.horaFin,
      cupoDisponible: franja.cupoDisponible,
    })
    setVista('reserva')
  }

  function irAAgenda(actividad: Actividad, franja: FranjaHoraria) {
    setFranjaParaAgenda({
      id: franja.id,
      nombreActividad: actividad.nombre,
      fecha: franja.fecha,
      horaInicio: franja.horaInicio,
      horaFin: franja.horaFin,
      cupoDisponible: franja.cupoDisponible,
    })
    setVista('agenda')
  }

  if (vista === 'reserva' && franjaParaReserva) {
    return <FormularioDeReserva franja={franjaParaReserva} modulo={modulo} onVolver={() => setVista('catalogo')} />
  }

  if (vista === 'agenda') {
    if (!puedeGestionar || !franjaParaAgenda) {
      return null
    }
    return <AgendaDeFranja franja={franjaParaAgenda} modulo={modulo} onVolver={() => setVista('catalogo')} />
  }

  const columnas = puedeGestionar ? 6 : 5

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Turnos'}
      </h1>
      <p className="contenido__bajada">
        Actividades recreativas municipales con franjas horarias de cupo
        limitado. No hace falta tener cuenta ni iniciar sesión para
        consultarlas o reservar un turno.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda-actividades">
        <h2 id="titulo-busqueda-actividades">Buscar actividades</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="turnos-filtro-tipo">Tipo</label>
            <select
              id="turnos-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeActividad | '')}
            >
              <option value="">Todos</option>
              {TIPOS_DE_ACTIVIDAD.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="turnos-filtro-estado">Estado</label>
            <select
              id="turnos-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeActividad | '')}
            >
              <option value="">Todos</option>
              {ESTADOS_DE_ACTIVIDAD.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="turnos-filtro-q">Buscar en nombre o descripción</label>
            <input id="turnos-filtro-q" value={qFiltro} onChange={(evento) => setQFiltro(evento.target.value)} />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {catalogo.estado === 'cargando' && <p role="status">Buscando actividades…</p>}

        {catalogo.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? catalogo.moduloDelError)}
          </p>
        )}

        {catalogo.estado === 'error' && <p role="alert">{catalogo.mensaje}</p>}

        {catalogo.estado === 'listo' && catalogo.actividades.length === 0 && <p>No se encontraron actividades.</p>}

        {catalogo.estado === 'listo' && catalogo.actividades.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Actividades municipales publicadas por el municipio. Se
                puede filtrar por tipo, estado y por texto en el nombre o
                la descripción. Las actividades activas se pueden expandir
                para ver sus franjas horarias y reservar un turno.
                {puedeGestionar &&
                  ' Se puede cambiar el estado de cada actividad y administrar sus franjas y reservas.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Tipo</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Franjas</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {catalogo.actividades.map((actividad) => {
                  const enEdicion = edicion && edicion.id === actividad.id ? edicion : null
                  const filaAbierta = filaAbiertaId === actividad.id

                  return (
                    <Fragment key={actividad.id}>
                      <tr>
                        <th scope="row">{actividad.nombre}</th>
                        <td>{ETIQUETA_TIPO_ACTIVIDAD[actividad.tipo]}</td>
                        <td>{actividad.ubicacion}</td>
                        <td>{ETIQUETA_ESTADO_ACTIVIDAD[actividad.estado]}</td>
                        <td>
                          {actividad.estado === 'ACTIVA' ? (
                            <button
                              type="button"
                              className="boton boton--secundario"
                              aria-expanded={filaAbierta}
                              ref={(elemento) => {
                                if (elemento) {
                                  botonesVerFranjas.current.set(actividad.id, elemento)
                                } else {
                                  botonesVerFranjas.current.delete(actividad.id)
                                }
                              }}
                              onClick={() => alternarFranjas(actividad)}
                            >
                              {filaAbierta ? 'Ocultar franjas' : 'Ver franjas'}
                            </button>
                          ) : (
                            '—'
                          )}
                        </td>
                        {puedeGestionar && (
                          <td>
                            {enEdicion ? (
                              <div className="formulario__acciones formulario__acciones--compacto">
                                <div className="campo">
                                  <label htmlFor={`turnos-actividad-${actividad.id}-estado`}>Nuevo estado</label>
                                  <select
                                    id={`turnos-actividad-${actividad.id}-estado`}
                                    ref={primerCampoEdicion}
                                    value={enEdicion.estadoNuevo}
                                    onChange={(evento) =>
                                      setEdicion((actual) =>
                                        actual
                                          ? { ...actual, estadoNuevo: evento.target.value as EstadoDeActividad }
                                          : actual,
                                      )
                                    }
                                  >
                                    <option value={OPUESTO_ESTADO_ACTIVIDAD[actividad.estado]}>
                                      {ETIQUETA_ESTADO_ACTIVIDAD[OPUESTO_ESTADO_ACTIVIDAD[actividad.estado]]}
                                    </option>
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
                                  onClick={() => cerrarEdicion(actividad.id)}
                                >
                                  Cancelar
                                </button>
                                {enEdicion.error && (
                                  <p className="formulario__error" role="alert" tabIndex={-1} ref={errorEdicionRef}>
                                    {enEdicion.error}
                                  </p>
                                )}
                              </div>
                            ) : (
                              <button
                                type="button"
                                className="boton boton--secundario"
                                ref={(elemento) => {
                                  if (elemento) {
                                    botonesCambiarEstado.current.set(actividad.id, elemento)
                                  } else {
                                    botonesCambiarEstado.current.delete(actividad.id)
                                  }
                                }}
                                onClick={() => abrirEdicion(actividad)}
                              >
                                Cambiar estado
                              </button>
                            )}
                          </td>
                        )}
                      </tr>

                      {filaAbierta && (
                        <tr>
                          <td colSpan={columnas}>
                            <h3 ref={tituloFranjas} tabIndex={-1}>
                              Franjas horarias — {actividad.nombre}
                            </h3>

                            {franjasDeFila?.estado === 'cargando' && <p role="status">Cargando franjas horarias…</p>}

                            {franjasDeFila?.estado === 'error' && <p role="alert">{franjasDeFila.mensaje}</p>}

                            {franjasDeFila?.estado === 'listo' && franjasDeFila.franjas.length === 0 && (
                              <p>Todavía no hay franjas horarias cargadas para esta actividad.</p>
                            )}

                            {franjasDeFila?.estado === 'listo' && franjasDeFila.franjas.length > 0 && (
                              <ul className="lista-compacta">
                                {franjasDeFila.franjas.map((franja) => (
                                  <li key={franja.id}>
                                    {formatearFecha(franja.fecha)}, {formatearHora(franja.horaInicio)}–
                                    {formatearHora(franja.horaFin)} —{' '}
                                    {franja.cupoDisponible > 0 ? (
                                      <span>
                                        {franja.cupoDisponible}{' '}
                                        {franja.cupoDisponible === 1 ? 'lugar disponible' : 'lugares disponibles'}
                                      </span>
                                    ) : (
                                      <span className="badge badge--atencion" aria-label="Sin cupo disponible para esta franja">
                                        Sin cupo
                                      </span>
                                    )}
                                    <div className="formulario__acciones formulario__acciones--compacto">
                                      {franja.cupoDisponible > 0 && (
                                        <button
                                          type="button"
                                          className="boton boton--secundario"
                                          onClick={() => irAReservar(actividad, franja)}
                                        >
                                          Reservar
                                        </button>
                                      )}
                                      {puedeGestionar && (
                                        <button
                                          type="button"
                                          className="boton boton--secundario"
                                          onClick={() => irAAgenda(actividad, franja)}
                                        >
                                          Ver reservas
                                        </button>
                                      )}
                                    </div>
                                  </li>
                                ))}
                              </ul>
                            )}

                            {puedeGestionar && (
                              <form className="formulario" onSubmit={(evento) => void agregarFranja(evento, actividad.id)}>
                                <h4>Agregar franja</h4>

                                {formularioFranja.error && (
                                  <p
                                    className="formulario__error"
                                    id={`turnos-actividad-${actividad.id}-error-franja`}
                                    role="alert"
                                    tabIndex={-1}
                                    ref={errorFranjaRef}
                                  >
                                    {formularioFranja.error}
                                  </p>
                                )}

                                <div className="campo">
                                  <label htmlFor={`turnos-actividad-${actividad.id}-franja-fecha`}>Fecha</label>
                                  <input
                                    id={`turnos-actividad-${actividad.id}-franja-fecha`}
                                    type="date"
                                    required
                                    value={formularioFranja.fecha}
                                    onChange={(evento) =>
                                      setFormularioFranja((actual) => ({ ...actual, fecha: evento.target.value }))
                                    }
                                    aria-invalid={formularioFranja.error ? true : undefined}
                                    aria-describedby={
                                      formularioFranja.error ? `turnos-actividad-${actividad.id}-error-franja` : undefined
                                    }
                                  />
                                </div>

                                <div className="campo">
                                  <label htmlFor={`turnos-actividad-${actividad.id}-franja-inicio`}>Hora inicio</label>
                                  <input
                                    id={`turnos-actividad-${actividad.id}-franja-inicio`}
                                    type="time"
                                    required
                                    value={formularioFranja.horaInicio}
                                    onChange={(evento) =>
                                      setFormularioFranja((actual) => ({ ...actual, horaInicio: evento.target.value }))
                                    }
                                    aria-invalid={formularioFranja.error ? true : undefined}
                                    aria-describedby={
                                      formularioFranja.error ? `turnos-actividad-${actividad.id}-error-franja` : undefined
                                    }
                                  />
                                </div>

                                <div className="campo">
                                  <label htmlFor={`turnos-actividad-${actividad.id}-franja-fin`}>Hora fin</label>
                                  <input
                                    id={`turnos-actividad-${actividad.id}-franja-fin`}
                                    type="time"
                                    required
                                    value={formularioFranja.horaFin}
                                    onChange={(evento) =>
                                      setFormularioFranja((actual) => ({ ...actual, horaFin: evento.target.value }))
                                    }
                                    aria-invalid={formularioFranja.error ? true : undefined}
                                    aria-describedby={
                                      formularioFranja.error ? `turnos-actividad-${actividad.id}-error-franja` : undefined
                                    }
                                  />
                                </div>

                                <div className="campo">
                                  <label htmlFor={`turnos-actividad-${actividad.id}-franja-cupo`}>Cupo total</label>
                                  <input
                                    id={`turnos-actividad-${actividad.id}-franja-cupo`}
                                    type="number"
                                    min="1"
                                    required
                                    value={formularioFranja.cupoTotal}
                                    onChange={(evento) =>
                                      setFormularioFranja((actual) => ({ ...actual, cupoTotal: evento.target.value }))
                                    }
                                    aria-invalid={formularioFranja.error ? true : undefined}
                                    aria-describedby={
                                      formularioFranja.error ? `turnos-actividad-${actividad.id}-error-franja` : undefined
                                    }
                                  />
                                </div>

                                <div className="formulario__acciones">
                                  <button
                                    type="submit"
                                    className="boton"
                                    disabled={formularioFranja.enviando}
                                    aria-busy={formularioFranja.enviando}
                                  >
                                    {formularioFranja.enviando ? 'Agregando…' : 'Agregar franja'}
                                  </button>
                                </div>
                              </form>
                            )}
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
      </section>

      {puedeGestionar && (
        <section aria-labelledby="titulo-publicar-actividad">
          <h2 id="titulo-publicar-actividad">Publicar una actividad</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormularioDePublicacion}>
                Publicar actividad
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarActividad(evento)}>
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
                <label htmlFor="turnos-publicar-nombre">Nombre</label>
                <input
                  id="turnos-publicar-nombre"
                  ref={primerCampoRegistro}
                  required
                  value={registro.nombre}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, nombre: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="turnos-publicar-tipo">Tipo</label>
                <select
                  id="turnos-publicar-tipo"
                  required
                  value={registro.tipo}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, tipo: evento.target.value as TipoDeActividad }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                >
                  <option value="" disabled>
                    Elegí un tipo
                  </option>
                  {TIPOS_DE_ACTIVIDAD.map((opcion) => (
                    <option key={opcion.valor} value={opcion.valor}>
                      {opcion.etiqueta}
                    </option>
                  ))}
                </select>
              </div>

              <div className="campo">
                <label htmlFor="turnos-publicar-ubicacion">Ubicación</label>
                <input
                  id="turnos-publicar-ubicacion"
                  required
                  value={registro.ubicacion}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, ubicacion: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="turnos-publicar-descripcion">Descripción (opcional)</label>
                <textarea
                  id="turnos-publicar-descripcion"
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
                  onClick={cerrarFormularioDePublicacion}
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

// --- Formulario público de reserva de un turno (sin sesión) ---

type PropsFormularioDeReserva = {
  franja: FranjaSeleccionada
  modulo?: Modulo
  onVolver: () => void
}

function FormularioDeReserva({ franja, modulo, onVolver }: PropsFormularioDeReserva) {
  const [nombreSolicitante, setNombreSolicitante] = useState('')
  const [dniSolicitante, setDniSolicitante] = useState('')
  const [contacto, setContacto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<ReservaPublica | null>(null)
  const [cupoAgotado, setCupoAgotado] = useState(false)

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const confirmacionRef = useRef<HTMLDivElement>(null)
  const cupoAgotadoRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  useEffect(() => {
    if (error) {
      errorRef.current?.focus()
    }
  }, [error])

  useEffect(() => {
    if (confirmacion) {
      confirmacionRef.current?.focus()
    }
  }, [confirmacion])

  useEffect(() => {
    if (cupoAgotado) {
      cupoAgotadoRef.current?.focus()
    }
  }, [cupoAgotado])

  async function reservar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setConfirmacion(null)
    setCupoAgotado(false)
    setEnviando(true)
    try {
      const respuesta = await enviar<ReservaPublica>(
        '/api/turnos/reservas',
        'POST',
        {
          franjaId: franja.id,
          nombreSolicitante,
          dniSolicitante,
          contacto,
        },
        'No se pudo registrar la reserva.',
      )
      if (!vigente.current) {
        return
      }
      if (respuesta) {
        setConfirmacion(respuesta)
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setError(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'turnos'))
      } else if (fallo instanceof Error && fallo.message === MENSAJE_CUPO_AGOTADO) {
        setCupoAgotado(true)
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo registrar la reserva.')
      }
    } finally {
      if (vigente.current) {
        setEnviando(false)
      }
    }
  }

  const idDelError = 'error-de-reserva-turno'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Reservar un turno
      </h1>
      <p className="contenido__bajada">
        Contanos tus datos: no hace falta tener cuenta para reservar un
        turno en {modulo?.nombre ?? 'Turnos'}.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al catálogo
        </button>
      </div>

      <dl className="ficha">
        <div className="ficha__fila">
          <dt>Actividad</dt>
          <dd>{franja.nombreActividad}</dd>
        </div>
        <div className="ficha__fila">
          <dt>Fecha</dt>
          <dd>{formatearFecha(franja.fecha)}</dd>
        </div>
        <div className="ficha__fila">
          <dt>Horario</dt>
          <dd>
            {formatearHora(franja.horaInicio)}–{formatearHora(franja.horaFin)}
          </dd>
        </div>
        <div className="ficha__fila">
          <dt>Cupo disponible</dt>
          <dd>
            {franja.cupoDisponible} {franja.cupoDisponible === 1 ? 'lugar' : 'lugares'}
          </dd>
        </div>
      </dl>

      {cupoAgotado && (
        <div role="alert" tabIndex={-1} ref={cupoAgotadoRef}>
          <p className="formulario__error">
            Se agotó el cupo de esta franja justo ahora — elegí otra.
          </p>
          <div className="formulario__acciones">
            <button type="button" className="boton" onClick={onVolver}>
              Volver al catálogo
            </button>
          </div>
        </div>
      )}

      {confirmacion && (
        <div role="status" tabIndex={-1} ref={confirmacionRef}>
          <p>Tu turno quedó reservado.</p>
          <dl className="ficha">
            <div className="ficha__fila">
              <dt>Actividad</dt>
              <dd>{confirmacion.nombreActividad}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Fecha</dt>
              <dd>{formatearFecha(confirmacion.fecha)}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Horario</dt>
              <dd>
                {formatearHora(confirmacion.horaInicio)}–{formatearHora(confirmacion.horaFin)}
              </dd>
            </div>
            <div className="ficha__fila">
              <dt>Cupo restante</dt>
              <dd>{confirmacion.cupoDisponibleRestante}</dd>
            </div>
          </dl>
        </div>
      )}

      {!cupoAgotado && !confirmacion && (
        <form className="formulario" onSubmit={(evento) => void reservar(evento)}>
          {error && (
            <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
              {error}
            </p>
          )}

          <div className="campo">
            <label htmlFor="turnos-reserva-nombre">Nombre y apellido</label>
            <input
              id="turnos-reserva-nombre"
              required
              value={nombreSolicitante}
              onChange={(evento) => setNombreSolicitante(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="turnos-reserva-dni">DNI</label>
            <input
              id="turnos-reserva-dni"
              required
              value={dniSolicitante}
              onChange={(evento) => setDniSolicitante(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="turnos-reserva-contacto">Contacto</label>
            <input
              id="turnos-reserva-contacto"
              required
              value={contacto}
              onChange={(evento) => setContacto(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${idDelError} turnos-reserva-contacto-ayuda` : 'turnos-reserva-contacto-ayuda'}
            />
            <p className="campo__ayuda" id="turnos-reserva-contacto-ayuda">
              Un teléfono o un email donde el municipio pueda contactarte.
            </p>
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
              {enviando ? 'Reservando…' : 'Reservar turno'}
            </button>
          </div>
        </form>
      )}
    </main>
  )
}

// --- Agenda de reservas de una franja (con turnos.gestionar) ---

type PropsAgendaDeFranja = {
  franja: FranjaSeleccionada
  modulo?: Modulo
  onVolver: () => void
}

type EstadoAgenda =
  | { estado: 'cargando' }
  | { estado: 'listo'; turnos: TurnoDeGestion[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

function AgendaDeFranja({ franja, modulo, onVolver }: PropsAgendaDeFranja) {
  const [estado, setEstado] = useState<EstadoAgenda>({ estado: 'cargando' })

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  useEffect(() => {
    async function cargar() {
      try {
        const turnos = await pedir<TurnoDeGestion[]>(
          `/api/turnos/reservas?franjaId=${franja.id}`,
          'No se pudo cargar el listado de reservas de esta franja.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', turnos })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'turnos' })
        } else {
          setEstado({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      }
    }
    // eslint-disable-next-line react/set-state-in-effect
    void cargar()
  }, [franja.id])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Reservas de la franja
      </h1>
      <p className="contenido__bajada">
        Reservas recibidas para esta franja horaria, con sus datos
        completos. Esta vista solo la ve quien tiene el permiso para
        gestionar {modulo?.nombre ?? 'Turnos'}.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al catálogo
        </button>
      </div>

      <dl className="ficha">
        <div className="ficha__fila">
          <dt>Actividad</dt>
          <dd>{franja.nombreActividad}</dd>
        </div>
        <div className="ficha__fila">
          <dt>Fecha</dt>
          <dd>{formatearFecha(franja.fecha)}</dd>
        </div>
        <div className="ficha__fila">
          <dt>Horario</dt>
          <dd>
            {formatearHora(franja.horaInicio)}–{formatearHora(franja.horaFin)}
          </dd>
        </div>
      </dl>

      {estado.estado === 'cargando' && <p role="status">Cargando reservas…</p>}

      {estado.estado === 'no-contratado' && (
        <p className="formulario__error" role="alert">
          {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
        </p>
      )}

      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && estado.turnos.length === 0 && <p>Todavía no hay reservas para esta franja.</p>}

      {estado.estado === 'listo' && estado.turnos.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>Reservas registradas para esta franja horaria, con sus datos personales.</caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">DNI</th>
                <th scope="col">Contacto</th>
                <th scope="col">Reservado el</th>
              </tr>
            </thead>
            <tbody>
              {estado.turnos.map((turno) => (
                <tr key={turno.id}>
                  <th scope="row">{turno.nombreSolicitante}</th>
                  <td>{turno.dniSolicitante}</td>
                  <td>{turno.contacto}</td>
                  <td>{FECHA_HORA.format(new Date(turno.creadoEn))}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
