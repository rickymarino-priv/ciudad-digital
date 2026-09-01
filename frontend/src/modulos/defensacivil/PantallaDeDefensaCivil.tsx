import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

// === Alertas ===

type TipoDeAlerta = 'METEOROLOGICA' | 'INUNDACION' | 'OLA_DE_CALOR' | 'INCENDIO' | 'OTRA'
type NivelDeAlerta = 'AMARILLO' | 'NARANJA' | 'ROJO'
type EstadoDeAlerta = 'VIGENTE' | 'FINALIZADA'

type Alerta = {
  id: number
  tipo: TipoDeAlerta
  nivel: NivelDeAlerta
  titulo: string
  descripcion: string
  recomendaciones: string
  zonaAfectada: string | null
  estado: EstadoDeAlerta
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const TIPOS_DE_ALERTA: { valor: TipoDeAlerta; etiqueta: string }[] = [
  { valor: 'METEOROLOGICA', etiqueta: 'Meteorológica' },
  { valor: 'INUNDACION', etiqueta: 'Inundación' },
  { valor: 'OLA_DE_CALOR', etiqueta: 'Ola de calor' },
  { valor: 'INCENDIO', etiqueta: 'Incendio' },
  { valor: 'OTRA', etiqueta: 'Otra' },
]

const ETIQUETA_TIPO_ALERTA: Record<TipoDeAlerta, string> = TIPOS_DE_ALERTA.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeAlerta, string>,
)

const NIVELES_DE_ALERTA: { valor: NivelDeAlerta; etiqueta: string }[] = [
  { valor: 'AMARILLO', etiqueta: 'Amarillo' },
  { valor: 'NARANJA', etiqueta: 'Naranja' },
  { valor: 'ROJO', etiqueta: 'Rojo' },
]

const ETIQUETA_NIVEL_ALERTA: Record<NivelDeAlerta, string> = NIVELES_DE_ALERTA.reduce(
  (mapa, nivel) => ({ ...mapa, [nivel.valor]: nivel.etiqueta }),
  {} as Record<NivelDeAlerta, string>,
)

const ESTADOS_DE_ALERTA: { valor: EstadoDeAlerta; etiqueta: string }[] = [
  { valor: 'VIGENTE', etiqueta: 'Vigente' },
  { valor: 'FINALIZADA', etiqueta: 'Finalizada' },
]

const ETIQUETA_ESTADO_ALERTA: Record<EstadoDeAlerta, string> = ESTADOS_DE_ALERTA.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeAlerta, string>,
)

// === Recursos ===

type TipoDeRecurso = 'REFUGIO' | 'PUNTO_DE_ENCUENTRO' | 'CENTRO_DE_ACOPIO' | 'OTRO'
type EstadoDeRecurso = 'ACTIVO' | 'INACTIVO'

type Recurso = {
  id: number
  tipo: TipoDeRecurso
  nombre: string
  direccion: string
  capacidad: number | null
  telefonoContacto: string | null
  descripcion: string | null
  estado: EstadoDeRecurso
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const TIPOS_DE_RECURSO: { valor: TipoDeRecurso; etiqueta: string }[] = [
  { valor: 'REFUGIO', etiqueta: 'Refugio' },
  { valor: 'PUNTO_DE_ENCUENTRO', etiqueta: 'Punto de encuentro' },
  { valor: 'CENTRO_DE_ACOPIO', etiqueta: 'Centro de acopio' },
  { valor: 'OTRO', etiqueta: 'Otro' },
]

const ETIQUETA_TIPO_RECURSO: Record<TipoDeRecurso, string> = TIPOS_DE_RECURSO.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeRecurso, string>,
)

const ESTADOS_DE_RECURSO: { valor: EstadoDeRecurso; etiqueta: string }[] = [
  { valor: 'ACTIVO', etiqueta: 'Activo' },
  { valor: 'INACTIVO', etiqueta: 'Inactivo' },
]

const ETIQUETA_ESTADO_RECURSO: Record<EstadoDeRecurso, string> = ESTADOS_DE_RECURSO.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeRecurso, string>,
)

/** Único destino posible desde cada estado: transición libre en ambos
 * sentidos (ADR 0031 §5), mismo criterio que `OPUESTO_ESTADO_PROGRAMA` en
 * `PantallaDeDesarrolloSocial`. */
const OPUESTO_ESTADO_RECURSO: Record<EstadoDeRecurso, EstadoDeRecurso> = {
  ACTIVO: 'INACTIVO',
  INACTIVO: 'ACTIVO',
}

/** Mismo texto que en `PantallaDeArbolado`/`PantallaDeEventos` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

function textoOVacio(valor: string | null): string {
  return valor && valor.trim() !== '' ? valor : '—'
}

function numeroOVacio(valor: number | null): string {
  return valor === null ? '—' : String(valor)
}

type EstadoListadoAlertas =
  | { estado: 'cargando' }
  | { estado: 'listo'; alertas: Alerta[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoListadoRecursos =
  | { estado: 'cargando' }
  | { estado: 'listo'; recursos: Recurso[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistroAlerta = {
  tipo: TipoDeAlerta | ''
  nivel: NivelDeAlerta | ''
  titulo: string
  descripcion: string
  recomendaciones: string
  zonaAfectada: string
  enviando: boolean
  error: string | null
}

const REGISTRO_ALERTA_INICIAL: EstadoRegistroAlerta = {
  tipo: '',
  nivel: '',
  titulo: '',
  descripcion: '',
  recomendaciones: '',
  zonaAfectada: '',
  enviando: false,
  error: null,
}

type EstadoFinalizacion = {
  id: number
  enviando: boolean
  error: string | null
}

type EstadoRegistroRecurso = {
  tipo: TipoDeRecurso | ''
  nombre: string
  direccion: string
  capacidad: string
  telefonoContacto: string
  descripcion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_RECURSO_INICIAL: EstadoRegistroRecurso = {
  tipo: '',
  nombre: '',
  direccion: '',
  capacidad: '',
  telefonoContacto: '',
  descripcion: '',
  enviando: false,
  error: null,
}

type EdicionEstadoRecurso = {
  id: number
  estadoNuevo: EstadoDeRecurso
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `defensacivil`: dos secciones independientes, mismo
 * patrón exacto que `PantallaDeArbolado`/`PantallaDeEventos` (no el de
 * `PantallaDeReclamos`, que muestra vistas *alternativas* según permiso —
 * acá el listado es el mismo para todos, solo cambia qué acciones se ven,
 * ADR 0031):
 *
 * - **Alertas**: listado público con filtros, alta protegida ("Publicar
 *   alerta") y una única transición posible por fila ("Finalizar alerta",
 *   sin `<select>` de destino, mismo patrón que "Cancelar evento" de
 *   `PantallaDeEventos`).
 * - **Recursos**: listado público con filtros, alta protegida ("Registrar
 *   recurso") y cambio de estado libre en ambos sentidos por fila (mismo
 *   patrón de `<select>` con una única opción —el estado contrario al
 *   actual— que el cambio de estado de `PantallaDeDesarrolloSocial`).
 *
 * Ninguna entidad referencia a la otra (ADR 0031 §1): comparten pantalla
 * por afinidad de dominio, no por relación de datos.
 */
export function PantallaDeDefensaCivil({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('defensacivil.gestionar') ?? false

  // Mismo patrón que PanelDeGestion/PanelDeUsuarios: evita pisar estado de
  // un componente que ya no está montado cuando un pedido en vuelo termina
  // después. Se comparte entre las dos secciones de esta pantalla.
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

  // ======================= Alertas =======================

  const [tipoFiltroAlerta, setTipoFiltroAlerta] = useState<TipoDeAlerta | ''>('')
  const [nivelFiltroAlerta, setNivelFiltroAlerta] = useState<NivelDeAlerta | ''>('')
  const [estadoFiltroAlerta, setEstadoFiltroAlerta] = useState<EstadoDeAlerta | ''>('')
  const [qFiltroAlerta, setQFiltroAlerta] = useState('')
  const [filtrosAlertasAplicados, setFiltrosAlertasAplicados] = useState<{
    tipo: TipoDeAlerta | ''
    nivel: NivelDeAlerta | ''
    estado: EstadoDeAlerta | ''
    q: string
  }>({ tipo: '', nivel: '', estado: '', q: '' })

  const [estadoAlertas, setEstadoAlertas] = useState<EstadoListadoAlertas>({ estado: 'cargando' })

  const cargarAlertas = useCallback(
    async (filtros: { tipo: TipoDeAlerta | ''; nivel: NivelDeAlerta | ''; estado: EstadoDeAlerta | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.tipo !== '') {
        parametros.set('tipo', filtros.tipo)
      }
      if (filtros.nivel !== '') {
        parametros.set('nivel', filtros.nivel)
      }
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const alertas = await pedir<Alerta[]>(
          `/api/defensacivil/alertas${query ? `?${query}` : ''}`,
          'No se pudieron cargar las alertas de Defensa Civil.',
        )
        if (vigente.current) {
          setEstadoAlertas({ estado: 'listo', alertas })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstadoAlertas({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'defensacivil' })
        } else {
          setEstadoAlertas({
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
    void cargarAlertas(filtrosAlertasAplicados)
  }, [cargarAlertas, filtrosAlertasAplicados])

  function buscarAlertas(evento: FormEvent) {
    evento.preventDefault()
    setEstadoAlertas({ estado: 'cargando' })
    setFiltrosAlertasAplicados({
      tipo: tipoFiltroAlerta,
      nivel: nivelFiltroAlerta,
      estado: estadoFiltroAlerta,
      q: qFiltroAlerta,
    })
  }

  // --- Publicación de alerta ---

  const [formularioAlertaAbierto, setFormularioAlertaAbierto] = useState(false)
  const [registroAlerta, setRegistroAlerta] = useState<EstadoRegistroAlerta>(REGISTRO_ALERTA_INICIAL)

  const botonPublicarAlerta = useRef<HTMLButtonElement>(null)
  const primerCampoRegistroAlerta = useRef<HTMLSelectElement>(null)
  const errorRegistroAlertaRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioAlertaAbierto) {
      primerCampoRegistroAlerta.current?.focus()
    }
  }, [formularioAlertaAbierto])

  useEffect(() => {
    if (registroAlerta.error) {
      errorRegistroAlertaRef.current?.focus()
    }
  }, [registroAlerta.error])

  function abrirFormularioAlerta() {
    setRegistroAlerta(REGISTRO_ALERTA_INICIAL)
    setFormularioAlertaAbierto(true)
  }

  function cerrarFormularioAlerta() {
    setFormularioAlertaAbierto(false)
    botonPublicarAlerta.current?.focus()
  }

  async function publicarAlerta(evento: FormEvent) {
    evento.preventDefault()
    setRegistroAlerta((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/defensacivil/alertas',
        'POST',
        {
          tipo: registroAlerta.tipo,
          nivel: registroAlerta.nivel,
          titulo: registroAlerta.titulo,
          descripcion: registroAlerta.descripcion,
          recomendaciones: registroAlerta.recomendaciones,
          zonaAfectada: registroAlerta.zonaAfectada.trim() === '' ? null : registroAlerta.zonaAfectada,
        },
        'No se pudo publicar la alerta.',
      )
      if (!vigente.current) {
        return
      }
      await cargarAlertas(filtrosAlertasAplicados)
      if (vigente.current) {
        setFormularioAlertaAbierto(false)
        botonPublicarAlerta.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistroAlerta((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'defensacivil'),
        }))
      } else {
        setRegistroAlerta((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la alerta.',
        }))
      }
    }
  }

  const idDelErrorRegistroAlerta = 'error-de-registro-alerta'

  // --- Finalización de alerta, por fila ---
  //
  // Única transición posible (VIGENTE → FINALIZADA, ADR 0031 §4): sin
  // `<select>` de estado destino, el botón dispara directo el PATCH, con
  // `window.confirm` como confirmación previa, mismo patrón que
  // `cancelarEvento` en `PantallaDeEventos`.

  const [finalizacion, setFinalizacion] = useState<EstadoFinalizacion | null>(null)
  const [alertaFinalizada, setAlertaFinalizada] = useState<string | null>(null)
  const errorFinalizacionRef = useRef<HTMLParagraphElement>(null)
  const confirmacionFinalizacionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (finalizacion?.error) {
      errorFinalizacionRef.current?.focus()
    }
  }, [finalizacion?.error])

  useEffect(() => {
    if (alertaFinalizada) {
      confirmacionFinalizacionRef.current?.focus()
    }
  }, [alertaFinalizada])

  async function finalizarAlerta(alerta: Alerta) {
    if (!window.confirm(`¿Finalizar la alerta "${alerta.titulo}"? Esta acción no se puede deshacer.`)) {
      return
    }
    setAlertaFinalizada(null)
    setFinalizacion({ id: alerta.id, enviando: true, error: null })
    try {
      await enviar(
        `/api/defensacivil/alertas/${alerta.id}/estado`,
        'PATCH',
        { estadoNuevo: 'FINALIZADA' },
        'No se pudo finalizar la alerta.',
      )
      if (!vigente.current) {
        return
      }
      await cargarAlertas(filtrosAlertasAplicados)
      if (vigente.current) {
        setFinalizacion(null)
        setAlertaFinalizada(alerta.titulo)
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setFinalizacion({
          id: alerta.id,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo finalizar la alerta.',
        })
      }
    }
  }

  // ======================= Recursos =======================

  const [tipoFiltroRecurso, setTipoFiltroRecurso] = useState<TipoDeRecurso | ''>('')
  const [estadoFiltroRecurso, setEstadoFiltroRecurso] = useState<EstadoDeRecurso | ''>('')
  const [qFiltroRecurso, setQFiltroRecurso] = useState('')
  const [filtrosRecursosAplicados, setFiltrosRecursosAplicados] = useState<{
    tipo: TipoDeRecurso | ''
    estado: EstadoDeRecurso | ''
    q: string
  }>({ tipo: '', estado: '', q: '' })

  const [estadoRecursos, setEstadoRecursos] = useState<EstadoListadoRecursos>({ estado: 'cargando' })

  const cargarRecursos = useCallback(
    async (filtros: { tipo: TipoDeRecurso | ''; estado: EstadoDeRecurso | ''; q: string }) => {
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
        const recursos = await pedir<Recurso[]>(
          `/api/defensacivil/recursos${query ? `?${query}` : ''}`,
          'No se pudieron cargar los recursos de Defensa Civil.',
        )
        if (vigente.current) {
          setEstadoRecursos({ estado: 'listo', recursos })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstadoRecursos({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'defensacivil' })
        } else {
          setEstadoRecursos({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      }
    },
    [],
  )

  useEffect(() => {
    // Carga inicial y recarga al cambiar los filtros aplicados.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarRecursos(filtrosRecursosAplicados)
  }, [cargarRecursos, filtrosRecursosAplicados])

  function buscarRecursos(evento: FormEvent) {
    evento.preventDefault()
    setEstadoRecursos({ estado: 'cargando' })
    setFiltrosRecursosAplicados({ tipo: tipoFiltroRecurso, estado: estadoFiltroRecurso, q: qFiltroRecurso })
  }

  // --- Registro de recurso ---

  const [formularioRecursoAbierto, setFormularioRecursoAbierto] = useState(false)
  const [registroRecurso, setRegistroRecurso] = useState<EstadoRegistroRecurso>(REGISTRO_RECURSO_INICIAL)

  const botonRegistrarRecurso = useRef<HTMLButtonElement>(null)
  const primerCampoRegistroRecurso = useRef<HTMLSelectElement>(null)
  const errorRegistroRecursoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioRecursoAbierto) {
      primerCampoRegistroRecurso.current?.focus()
    }
  }, [formularioRecursoAbierto])

  useEffect(() => {
    if (registroRecurso.error) {
      errorRegistroRecursoRef.current?.focus()
    }
  }, [registroRecurso.error])

  function abrirFormularioRecurso() {
    setRegistroRecurso(REGISTRO_RECURSO_INICIAL)
    setFormularioRecursoAbierto(true)
  }

  function cerrarFormularioRecurso() {
    setFormularioRecursoAbierto(false)
    botonRegistrarRecurso.current?.focus()
  }

  async function registrarRecurso(evento: FormEvent) {
    evento.preventDefault()

    // Misma validación que `GestionDeRecursos#registrar` en el backend
    // (ADR 0031 §5): se duplica acá para dar el error sin ida y vuelta al
    // servidor, pero el backend la vuelve a hacer igual, es la que cuenta.
    if (registroRecurso.capacidad.trim() !== '' && Number(registroRecurso.capacidad) < 0) {
      setRegistroRecurso((actual) => ({ ...actual, error: 'La capacidad no puede ser negativa.' }))
      return
    }

    setRegistroRecurso((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/defensacivil/recursos',
        'POST',
        {
          tipo: registroRecurso.tipo,
          nombre: registroRecurso.nombre,
          direccion: registroRecurso.direccion,
          capacidad: registroRecurso.capacidad.trim() === '' ? null : Number(registroRecurso.capacidad),
          telefonoContacto:
            registroRecurso.telefonoContacto.trim() === '' ? null : registroRecurso.telefonoContacto,
          descripcion: registroRecurso.descripcion.trim() === '' ? null : registroRecurso.descripcion,
        },
        'No se pudo registrar el recurso.',
      )
      if (!vigente.current) {
        return
      }
      await cargarRecursos(filtrosRecursosAplicados)
      if (vigente.current) {
        setFormularioRecursoAbierto(false)
        botonRegistrarRecurso.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistroRecurso((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'defensacivil'),
        }))
      } else {
        setRegistroRecurso((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar el recurso.',
        }))
      }
    }
  }

  const idDelErrorRegistroRecurso = 'error-de-registro-recurso'

  // --- Cambio de estado de un recurso, por fila ---
  //
  // Transición libre en ambos sentidos (ADR 0031 §5): mismo patrón de
  // `<select>` con una única opción —el estado contrario al actual— que el
  // cambio de estado de un programa en `PantallaDeDesarrolloSocial`.

  const [edicionRecurso, setEdicionRecurso] = useState<EdicionEstadoRecurso | null>(null)
  const botonesCambiarEstadoRecurso = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicionRecurso = useRef<HTMLSelectElement>(null)
  const errorEdicionRecursoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicionRecurso) {
      primerCampoEdicionRecurso.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el error no hay que robarle
    // el foco a lo que esté tocando, mismo criterio que PantallaDeObras.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicionRecurso?.id])

  useEffect(() => {
    if (edicionRecurso?.error) {
      errorEdicionRecursoRef.current?.focus()
    }
  }, [edicionRecurso?.error])

  function abrirEdicionRecurso(recurso: Recurso) {
    setEdicionRecurso({
      id: recurso.id,
      estadoNuevo: OPUESTO_ESTADO_RECURSO[recurso.estado],
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicionRecurso(idRecurso: number) {
    setEdicionRecurso(null)
    botonesCambiarEstadoRecurso.current.get(idRecurso)?.focus()
  }

  async function guardarEdicionRecurso() {
    if (!edicionRecurso) {
      return
    }
    setEdicionRecurso({ ...edicionRecurso, enviando: true, error: null })
    try {
      await enviar(
        `/api/defensacivil/recursos/${edicionRecurso.id}/estado`,
        'PATCH',
        { estadoNuevo: edicionRecurso.estadoNuevo },
        'No se pudo actualizar el estado del recurso.',
      )
      await cargarRecursos(filtrosRecursosAplicados)
      if (vigente.current) {
        cerrarEdicionRecurso(edicionRecurso.id)
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEdicionRecurso((actual) =>
          actual
            ? {
                ...actual,
                enviando: false,
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del recurso.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Defensa Civil'}
      </h1>
      <p className="contenido__bajada">
        Alertas y recomendaciones vigentes de Defensa Civil, y los recursos
        del municipio (refugios, puntos de encuentro, centros de acopio). No
        hace falta tener cuenta ni iniciar sesión para consultarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {/* ======================= Alertas ======================= */}

      <section aria-labelledby="defensacivil-alertas-titulo">
        <h2 id="defensacivil-alertas-titulo">Alertas de Defensa Civil</h2>

        <form className="formulario" onSubmit={buscarAlertas}>
          <div className="campo">
            <label htmlFor="defensacivil-alertas-filtro-tipo">Tipo</label>
            <select
              id="defensacivil-alertas-filtro-tipo"
              value={tipoFiltroAlerta}
              onChange={(evento) => setTipoFiltroAlerta(evento.target.value as TipoDeAlerta | '')}
            >
              <option value="">Todos</option>
              {TIPOS_DE_ALERTA.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="defensacivil-alertas-filtro-nivel">Nivel</label>
            <select
              id="defensacivil-alertas-filtro-nivel"
              value={nivelFiltroAlerta}
              onChange={(evento) => setNivelFiltroAlerta(evento.target.value as NivelDeAlerta | '')}
            >
              <option value="">Todos</option>
              {NIVELES_DE_ALERTA.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="defensacivil-alertas-filtro-estado">Estado</label>
            <select
              id="defensacivil-alertas-filtro-estado"
              value={estadoFiltroAlerta}
              onChange={(evento) => setEstadoFiltroAlerta(evento.target.value as EstadoDeAlerta | '')}
            >
              <option value="">Todas</option>
              {ESTADOS_DE_ALERTA.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="defensacivil-alertas-filtro-q">Buscar en título o descripción</label>
            <input
              id="defensacivil-alertas-filtro-q"
              value={qFiltroAlerta}
              onChange={(evento) => setQFiltroAlerta(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estadoAlertas.estado === 'cargando' && <p role="status">Buscando alertas…</p>}

        {estadoAlertas.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estadoAlertas.moduloDelError)}
          </p>
        )}

        {estadoAlertas.estado === 'error' && <p role="alert">{estadoAlertas.mensaje}</p>}

        {estadoAlertas.estado === 'listo' && estadoAlertas.alertas.length === 0 && (
          <p>No se encontraron alertas.</p>
        )}

        {estadoAlertas.estado === 'listo' && estadoAlertas.alertas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Alertas de Defensa Civil publicadas por el municipio, con
                sus recomendaciones. Se puede filtrar por tipo, nivel,
                estado y texto en el título o la descripción. Las alertas
                vigentes no desaparecen del listado al finalizarse.
                {puedeGestionar && ' Se puede finalizar una alerta que todavía esté vigente.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Tipo</th>
                  <th scope="col">Nivel</th>
                  <th scope="col">Título</th>
                  <th scope="col">Descripción</th>
                  <th scope="col">Recomendaciones</th>
                  <th scope="col">Zona afectada</th>
                  <th scope="col">Estado</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estadoAlertas.alertas.map((alerta) => (
                  <tr key={alerta.id} className={alerta.estado === 'VIGENTE' ? 'tabla__fila--destacada' : undefined}>
                    <th scope="row">{ETIQUETA_TIPO_ALERTA[alerta.tipo]}</th>
                    <td>{ETIQUETA_NIVEL_ALERTA[alerta.nivel]}</td>
                    <td>{alerta.titulo}</td>
                    <td>{alerta.descripcion}</td>
                    <td>{alerta.recomendaciones}</td>
                    <td>{textoOVacio(alerta.zonaAfectada)}</td>
                    <td>
                      {alerta.estado === 'VIGENTE' ? (
                        <span className="badge">Vigente</span>
                      ) : (
                        ETIQUETA_ESTADO_ALERTA[alerta.estado]
                      )}
                    </td>
                    {puedeGestionar && (
                      <td>
                        {alerta.estado === 'VIGENTE' ? (
                          <button
                            type="button"
                            className="boton boton--secundario"
                            disabled={finalizacion?.id === alerta.id && finalizacion.enviando}
                            aria-busy={finalizacion?.id === alerta.id && finalizacion.enviando}
                            onClick={() => void finalizarAlerta(alerta)}
                          >
                            {finalizacion?.id === alerta.id && finalizacion.enviando
                              ? 'Finalizando…'
                              : 'Finalizar alerta'}
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

        {finalizacion?.error && (
          <p className="formulario__error" role="alert" tabIndex={-1} ref={errorFinalizacionRef}>
            {finalizacion.error}
          </p>
        )}

        {alertaFinalizada && (
          <p role="status" tabIndex={-1} ref={confirmacionFinalizacionRef}>
            Se finalizó la alerta «{alertaFinalizada}».
          </p>
        )}

        {puedeGestionar && (
          <div>
            <h3 id="defensacivil-publicar-alerta-titulo">Publicar alerta</h3>

            {!formularioAlertaAbierto ? (
              <div className="administracion__barra">
                <button type="button" className="boton" ref={botonPublicarAlerta} onClick={abrirFormularioAlerta}>
                  Publicar alerta
                </button>
              </div>
            ) : (
              <form
                className="formulario"
                aria-labelledby="defensacivil-publicar-alerta-titulo"
                onSubmit={(evento) => void publicarAlerta(evento)}
              >
                {registroAlerta.error && (
                  <p
                    className="formulario__error"
                    id={idDelErrorRegistroAlerta}
                    role="alert"
                    tabIndex={-1}
                    ref={errorRegistroAlertaRef}
                  >
                    {registroAlerta.error}
                  </p>
                )}

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-tipo">Tipo</label>
                  <select
                    id="defensacivil-alerta-tipo"
                    ref={primerCampoRegistroAlerta}
                    required
                    value={registroAlerta.tipo}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({
                        ...actual,
                        tipo: evento.target.value as TipoDeAlerta,
                      }))
                    }
                    aria-invalid={registroAlerta.error ? true : undefined}
                    aria-describedby={registroAlerta.error ? idDelErrorRegistroAlerta : undefined}
                  >
                    <option value="" disabled>
                      Elegí un tipo
                    </option>
                    {TIPOS_DE_ALERTA.map((opcion) => (
                      <option key={opcion.valor} value={opcion.valor}>
                        {opcion.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-nivel">Nivel</label>
                  <select
                    id="defensacivil-alerta-nivel"
                    required
                    value={registroAlerta.nivel}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({
                        ...actual,
                        nivel: evento.target.value as NivelDeAlerta,
                      }))
                    }
                    aria-invalid={registroAlerta.error ? true : undefined}
                    aria-describedby={registroAlerta.error ? idDelErrorRegistroAlerta : undefined}
                  >
                    <option value="" disabled>
                      Elegí un nivel
                    </option>
                    {NIVELES_DE_ALERTA.map((opcion) => (
                      <option key={opcion.valor} value={opcion.valor}>
                        {opcion.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-titulo">Título</label>
                  <input
                    id="defensacivil-alerta-titulo"
                    required
                    value={registroAlerta.titulo}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({ ...actual, titulo: evento.target.value }))
                    }
                    aria-invalid={registroAlerta.error ? true : undefined}
                    aria-describedby={registroAlerta.error ? idDelErrorRegistroAlerta : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-descripcion">Descripción</label>
                  <textarea
                    id="defensacivil-alerta-descripcion"
                    required
                    value={registroAlerta.descripcion}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({ ...actual, descripcion: evento.target.value }))
                    }
                    aria-invalid={registroAlerta.error ? true : undefined}
                    aria-describedby={registroAlerta.error ? idDelErrorRegistroAlerta : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-recomendaciones">Recomendaciones</label>
                  <textarea
                    id="defensacivil-alerta-recomendaciones"
                    required
                    value={registroAlerta.recomendaciones}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({ ...actual, recomendaciones: evento.target.value }))
                    }
                    aria-invalid={registroAlerta.error ? true : undefined}
                    aria-describedby={registroAlerta.error ? idDelErrorRegistroAlerta : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-alerta-zona-afectada">Zona afectada (opcional)</label>
                  <input
                    id="defensacivil-alerta-zona-afectada"
                    value={registroAlerta.zonaAfectada}
                    onChange={(evento) =>
                      setRegistroAlerta((actual) => ({ ...actual, zonaAfectada: evento.target.value }))
                    }
                  />
                </div>

                <div className="formulario__acciones">
                  <button
                    type="submit"
                    className="boton"
                    disabled={registroAlerta.enviando}
                    aria-busy={registroAlerta.enviando}
                  >
                    {registroAlerta.enviando ? 'Publicando…' : 'Publicar'}
                  </button>
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={cerrarFormularioAlerta}
                    disabled={registroAlerta.enviando}
                  >
                    Cancelar
                  </button>
                </div>
              </form>
            )}
          </div>
        )}
      </section>

      {/* ======================= Recursos ======================= */}

      <section aria-labelledby="defensacivil-recursos-titulo">
        <h2 id="defensacivil-recursos-titulo">Recursos de Defensa Civil</h2>

        <form className="formulario" onSubmit={buscarRecursos}>
          <div className="campo">
            <label htmlFor="defensacivil-recursos-filtro-tipo">Tipo</label>
            <select
              id="defensacivil-recursos-filtro-tipo"
              value={tipoFiltroRecurso}
              onChange={(evento) => setTipoFiltroRecurso(evento.target.value as TipoDeRecurso | '')}
            >
              <option value="">Todos</option>
              {TIPOS_DE_RECURSO.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="defensacivil-recursos-filtro-estado">Estado</label>
            <select
              id="defensacivil-recursos-filtro-estado"
              value={estadoFiltroRecurso}
              onChange={(evento) => setEstadoFiltroRecurso(evento.target.value as EstadoDeRecurso | '')}
            >
              <option value="">Todos</option>
              {ESTADOS_DE_RECURSO.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="defensacivil-recursos-filtro-q">Buscar en nombre o dirección</label>
            <input
              id="defensacivil-recursos-filtro-q"
              value={qFiltroRecurso}
              onChange={(evento) => setQFiltroRecurso(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estadoRecursos.estado === 'cargando' && <p role="status">Buscando recursos…</p>}

        {estadoRecursos.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estadoRecursos.moduloDelError)}
          </p>
        )}

        {estadoRecursos.estado === 'error' && <p role="alert">{estadoRecursos.mensaje}</p>}

        {estadoRecursos.estado === 'listo' && estadoRecursos.recursos.length === 0 && (
          <p>No se encontraron recursos.</p>
        )}

        {estadoRecursos.estado === 'listo' && estadoRecursos.recursos.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Recursos de Defensa Civil del municipio: refugios, puntos
                de encuentro y centros de acopio. Se puede filtrar por
                tipo, estado y texto en el nombre o la dirección.
                {puedeGestionar && ' Se puede activar o desactivar cada recurso.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Tipo</th>
                  <th scope="col">Nombre</th>
                  <th scope="col">Dirección</th>
                  <th scope="col">Capacidad</th>
                  <th scope="col">Teléfono</th>
                  <th scope="col">Estado</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estadoRecursos.recursos.map((recurso) => {
                  const enEdicion = edicionRecurso && edicionRecurso.id === recurso.id ? edicionRecurso : null

                  return (
                    <tr key={recurso.id}>
                      <th scope="row">{ETIQUETA_TIPO_RECURSO[recurso.tipo]}</th>
                      <td>{recurso.nombre}</td>
                      <td>{recurso.direccion}</td>
                      <td>{numeroOVacio(recurso.capacidad)}</td>
                      <td>{textoOVacio(recurso.telefonoContacto)}</td>
                      <td>{ETIQUETA_ESTADO_RECURSO[recurso.estado]}</td>
                      {puedeGestionar && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`defensacivil-recurso-${recurso.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`defensacivil-recurso-${recurso.id}-estado`}
                                  ref={primerCampoEdicionRecurso}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicionRecurso((actual) =>
                                      actual
                                        ? { ...actual, estadoNuevo: evento.target.value as EstadoDeRecurso }
                                        : actual,
                                    )
                                  }
                                >
                                  <option value={OPUESTO_ESTADO_RECURSO[recurso.estado]}>
                                    {ETIQUETA_ESTADO_RECURSO[OPUESTO_ESTADO_RECURSO[recurso.estado]]}
                                  </option>
                                </select>
                              </div>

                              <button
                                type="button"
                                className="boton"
                                disabled={enEdicion.enviando}
                                aria-busy={enEdicion.enviando}
                                onClick={() => void guardarEdicionRecurso()}
                              >
                                {enEdicion.enviando ? 'Actualizando…' : 'Actualizar estado'}
                              </button>
                              <button
                                type="button"
                                className="boton boton--secundario"
                                onClick={() => cerrarEdicionRecurso(recurso.id)}
                              >
                                Cancelar
                              </button>
                              {enEdicion.error && (
                                <p
                                  className="formulario__error"
                                  role="alert"
                                  tabIndex={-1}
                                  ref={errorEdicionRecursoRef}
                                >
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
                                  botonesCambiarEstadoRecurso.current.set(recurso.id, elemento)
                                } else {
                                  botonesCambiarEstadoRecurso.current.delete(recurso.id)
                                }
                              }}
                              onClick={() => abrirEdicionRecurso(recurso)}
                            >
                              Cambiar estado
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

        {puedeGestionar && (
          <div>
            <h3 id="defensacivil-registrar-recurso-titulo">Registrar recurso</h3>

            {!formularioRecursoAbierto ? (
              <div className="administracion__barra">
                <button type="button" className="boton" ref={botonRegistrarRecurso} onClick={abrirFormularioRecurso}>
                  Registrar recurso
                </button>
              </div>
            ) : (
              <form
                className="formulario"
                aria-labelledby="defensacivil-registrar-recurso-titulo"
                onSubmit={(evento) => void registrarRecurso(evento)}
              >
                {registroRecurso.error && (
                  <p
                    className="formulario__error"
                    id={idDelErrorRegistroRecurso}
                    role="alert"
                    tabIndex={-1}
                    ref={errorRegistroRecursoRef}
                  >
                    {registroRecurso.error}
                  </p>
                )}

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-tipo">Tipo</label>
                  <select
                    id="defensacivil-recurso-tipo"
                    ref={primerCampoRegistroRecurso}
                    required
                    value={registroRecurso.tipo}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({
                        ...actual,
                        tipo: evento.target.value as TipoDeRecurso,
                      }))
                    }
                    aria-invalid={registroRecurso.error ? true : undefined}
                    aria-describedby={registroRecurso.error ? idDelErrorRegistroRecurso : undefined}
                  >
                    <option value="" disabled>
                      Elegí un tipo
                    </option>
                    {TIPOS_DE_RECURSO.map((opcion) => (
                      <option key={opcion.valor} value={opcion.valor}>
                        {opcion.etiqueta}
                      </option>
                    ))}
                  </select>
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-nombre">Nombre</label>
                  <input
                    id="defensacivil-recurso-nombre"
                    required
                    value={registroRecurso.nombre}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({ ...actual, nombre: evento.target.value }))
                    }
                    aria-invalid={registroRecurso.error ? true : undefined}
                    aria-describedby={registroRecurso.error ? idDelErrorRegistroRecurso : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-direccion">Dirección</label>
                  <input
                    id="defensacivil-recurso-direccion"
                    required
                    value={registroRecurso.direccion}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({ ...actual, direccion: evento.target.value }))
                    }
                    aria-invalid={registroRecurso.error ? true : undefined}
                    aria-describedby={registroRecurso.error ? idDelErrorRegistroRecurso : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-capacidad">Capacidad (opcional)</label>
                  <input
                    id="defensacivil-recurso-capacidad"
                    type="number"
                    min="0"
                    value={registroRecurso.capacidad}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({ ...actual, capacidad: evento.target.value }))
                    }
                    aria-invalid={registroRecurso.error ? true : undefined}
                    aria-describedby={registroRecurso.error ? idDelErrorRegistroRecurso : undefined}
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-telefono">Teléfono de contacto (opcional)</label>
                  <input
                    id="defensacivil-recurso-telefono"
                    value={registroRecurso.telefonoContacto}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({ ...actual, telefonoContacto: evento.target.value }))
                    }
                  />
                </div>

                <div className="campo">
                  <label htmlFor="defensacivil-recurso-descripcion">Descripción (opcional)</label>
                  <textarea
                    id="defensacivil-recurso-descripcion"
                    value={registroRecurso.descripcion}
                    onChange={(evento) =>
                      setRegistroRecurso((actual) => ({ ...actual, descripcion: evento.target.value }))
                    }
                  />
                </div>

                <div className="formulario__acciones">
                  <button
                    type="submit"
                    className="boton"
                    disabled={registroRecurso.enviando}
                    aria-busy={registroRecurso.enviando}
                  >
                    {registroRecurso.enviando ? 'Registrando…' : 'Registrar'}
                  </button>
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={cerrarFormularioRecurso}
                    disabled={registroRecurso.enviando}
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
