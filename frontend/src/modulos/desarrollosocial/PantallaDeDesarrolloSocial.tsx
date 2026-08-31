import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type EstadoDePrograma = 'ABIERTO' | 'CERRADO'

type EstadoDeInscripcion = 'RECIBIDA' | 'EN_EVALUACION' | 'APROBADA' | 'RECHAZADA'

type SituacionDeclarada =
  | 'DESOCUPADO'
  | 'EMPLEO_INFORMAL'
  | 'EMPLEO_FORMAL'
  | 'JUBILADO_O_PENSIONADO'
  | 'OTRO'

type ProgramaSocial = {
  id: number
  nombre: string
  descripcion: string | null
  criteriosDeElegibilidad: string | null
  estado: EstadoDePrograma
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

/** Respuesta de `POST /api/desarrollosocial/inscripciones` (ADR 0025 §6):
 * deliberadamente sin ningún otro campo enviado por el vecino. */
type InscripcionPublica = {
  id: number
  estado: EstadoDeInscripcion
  tokenDeSeguimiento: string
}

/** Respuesta de `GET /api/desarrollosocial/inscripciones/seguimiento/{token}`
 * (ADR 0025 §6): subconjunto minimizado, sin ningún dato personal. */
type SeguimientoDeInscripcion = {
  id: number
  nombrePrograma: string
  estado: EstadoDeInscripcion
  comentarioDeResolucion: string | null
  creadoEn: string
  actualizadoEn: string
}

/** Shape completo de `GET /api/desarrollosocial/inscripciones`, con datos
 * personales: solo lo ve quien tiene `desarrollosocial.revisarInscripciones`
 * (ADR 0025 §7). */
type InscripcionCompleta = {
  id: number
  programaId: number
  nombreSolicitante: string
  dniSolicitante: string
  contacto: string
  cantidadIntegrantesGrupoFamiliar: number
  situacionDeclarada: SituacionDeclarada
  comentarioAdicional: string | null
  estado: EstadoDeInscripcion
  comentarioDeResolucion: string | null
  resueltoPorNombre: string | null
  resueltoPorEmail: string | null
  resueltoEn: string | null
  creadoEn: string
  actualizadoEn: string
}

const ESTADOS_DE_PROGRAMA: { valor: EstadoDePrograma; etiqueta: string }[] = [
  { valor: 'ABIERTO', etiqueta: 'Abierto' },
  { valor: 'CERRADO', etiqueta: 'Cerrado' },
]

const ETIQUETA_ESTADO_PROGRAMA: Record<EstadoDePrograma, string> = {
  ABIERTO: 'Abierto',
  CERRADO: 'Cerrado',
}

/** Único destino posible desde cada estado (ADR 0025 §3): a diferencia de
 * Obras/Arbolado no hace falta un mapa de varias opciones por estado. */
const OPUESTO_ESTADO_PROGRAMA: Record<EstadoDePrograma, EstadoDePrograma> = {
  ABIERTO: 'CERRADO',
  CERRADO: 'ABIERTO',
}

const SITUACIONES_DECLARADAS: { valor: SituacionDeclarada; etiqueta: string }[] = [
  { valor: 'DESOCUPADO', etiqueta: 'Desocupado/a' },
  { valor: 'EMPLEO_INFORMAL', etiqueta: 'Empleo informal' },
  { valor: 'EMPLEO_FORMAL', etiqueta: 'Empleo formal' },
  { valor: 'JUBILADO_O_PENSIONADO', etiqueta: 'Jubilado/a o pensionado/a' },
  { valor: 'OTRO', etiqueta: 'Otra' },
]

const ETIQUETA_SITUACION_DECLARADA: Record<SituacionDeclarada, string> = SITUACIONES_DECLARADAS.reduce(
  (mapa, opcion) => ({ ...mapa, [opcion.valor]: opcion.etiqueta }),
  {} as Record<SituacionDeclarada, string>,
)

const ESTADOS_DE_INSCRIPCION: { valor: EstadoDeInscripcion; etiqueta: string }[] = [
  { valor: 'RECIBIDA', etiqueta: 'Recibida' },
  { valor: 'EN_EVALUACION', etiqueta: 'En evaluación' },
  { valor: 'APROBADA', etiqueta: 'Aprobada' },
  { valor: 'RECHAZADA', etiqueta: 'Rechazada' },
]

const ETIQUETA_ESTADO_INSCRIPCION: Record<EstadoDeInscripcion, string> = {
  RECIBIDA: 'Recibida',
  EN_EVALUACION: 'En evaluación',
  APROBADA: 'Aprobada',
  RECHAZADA: 'Rechazada',
}

// Mismo mapa de transiciones válidas que valida el backend
// (`GestionDeInscripcionesSociales`, ADR 0025 §3): acá solo decide qué
// opciones ofrecer en el `<select>` de cada fila, el enforcement real sigue
// siendo del backend (ADR 0011), mismo criterio que `TRANSICIONES_VALIDAS`
// en `PantallaDeObras`/`PantallaDeReclamos`.
const TRANSICIONES_VALIDAS_INSCRIPCION: Record<EstadoDeInscripcion, EstadoDeInscripcion[]> = {
  RECIBIDA: ['EN_EVALUACION'],
  EN_EVALUACION: ['APROBADA', 'RECHAZADA'],
  APROBADA: [],
  RECHAZADA: [],
}

/** Mismo texto que en `PantallaDeObras`/`PantallaDeReclamos` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

function textoOVacio(valor: string | null): string {
  return valor && valor.trim() !== '' ? valor : '—'
}

/** Estado de carga del catálogo de programas: se comparte entre la vista
 * `catalogo` (que lo carga) y las sub-vistas `inscripcion`/`bandeja` (que
 * lo reutilizan para poblar sus `<select>` sin volver a pedirlo). */
type EstadoCatalogo =
  | { estado: 'cargando' }
  | { estado: 'listo'; programas: ProgramaSocial[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistroPrograma = {
  nombre: string
  descripcion: string
  criteriosDeElegibilidad: string
  enviando: boolean
  error: string | null
}

const REGISTRO_PROGRAMA_INICIAL: EstadoRegistroPrograma = {
  nombre: '',
  descripcion: '',
  criteriosDeElegibilidad: '',
  enviando: false,
  error: null,
}

type EdicionEstadoPrograma = {
  id: number
  estadoNuevo: EstadoDePrograma
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `desarrollosocial` (ADR 0025): a diferencia de
 * `PantallaDeObras` (una única vista con acciones condicionadas por
 * permiso) y de `PantallaDeReclamos` (vistas alternativas según permiso),
 * acá combina ambos patrones porque hay tres audiencias reales: el vecino
 * anónimo, quien solo gestiona programas (`gestionarProgramas`) y quien
 * además revisa inscripciones (`revisarInscripciones`). Navegación por
 * estado local, sin router (ADR 0008): `catalogo` (default, público),
 * `inscripcion` y `seguimiento` (públicas), `bandeja` (protegida por
 * permiso, esconde el control por comodidad — el backend vuelve a exigirlo,
 * ADR 0011).
 */
export function PantallaDeDesarrolloSocial({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionarProgramas = usuario?.permisos.includes('desarrollosocial.gestionarProgramas') ?? false
  const puedeRevisarInscripciones = usuario?.permisos.includes('desarrollosocial.revisarInscripciones') ?? false

  const [vista, setVista] = useState<'catalogo' | 'inscripcion' | 'seguimiento' | 'bandeja'>('catalogo')

  // Si alguien sin el permiso llega a `bandeja` por cualquier motivo, la
  // protección real es el backend (ADR 0011), pero no le mostramos la
  // sección a quien no la va a poder usar: la mandamos de vuelta al
  // catálogo en vez de renderizarla.
  useEffect(() => {
    if (vista === 'bandeja' && !puedeRevisarInscripciones) {
      setVista('catalogo')
    }
  }, [vista, puedeRevisarInscripciones])

  // --- Catálogo de programas: compartido entre `catalogo`, `inscripcion` y
  // `bandeja` (esta última solo para poblar el filtro por programa). ---

  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDePrograma | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{ estado: EstadoDePrograma | ''; q: string }>({
    estado: '',
    q: '',
  })

  const [catalogo, setCatalogo] = useState<EstadoCatalogo>({ estado: 'cargando' })

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

  const cargarCatalogo = useCallback(
    async (filtros: { estado: EstadoDePrograma | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const programas = await pedir<ProgramaSocial[]>(
          `/api/desarrollosocial/programas${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de programas sociales.',
        )
        if (vigente.current) {
          setCatalogo({ estado: 'listo', programas })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setCatalogo({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'desarrollosocial' })
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
    // patrón que PantallaDeObras): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarCatalogo(filtrosAplicados)
  }, [cargarCatalogo, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    // `vista` como dependencia: `inscripcion`/`seguimiento`/`bandeja` son
    // componentes propios que se montan aparte, pero al volver a `catalogo`
    // este mismo componente no se remonta (es el mismo `return`), así que
    // sin esto el título nunca recuperaba el foco, mismo criterio que
    // `FormularioDeAlta` en `PantallaDeReclamos`.
    if (vista === 'catalogo') {
      titulo.current?.focus()
    }
  }, [vista])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setCatalogo({ estado: 'cargando' })
    setFiltrosAplicados({ estado: estadoFiltro, q: qFiltro })
  }

  // --- Publicar un programa ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [registro, setRegistro] = useState<EstadoRegistroPrograma>(REGISTRO_PROGRAMA_INICIAL)

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
    setRegistro(REGISTRO_PROGRAMA_INICIAL)
    setFormularioAbierto(true)
  }

  function cerrarFormularioDePublicacion() {
    setFormularioAbierto(false)
    botonRegistrar.current?.focus()
  }

  async function publicarPrograma(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/desarrollosocial/programas',
        'POST',
        {
          nombre: registro.nombre,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
          criteriosDeElegibilidad:
            registro.criteriosDeElegibilidad.trim() === '' ? null : registro.criteriosDeElegibilidad,
        },
        'No se pudo publicar el programa.',
      )
      if (!vigente.current) {
        return
      }
      await cargarCatalogo(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'desarrollosocial'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar el programa.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-publicacion-programa'

  // --- Cambio de estado de un programa, por fila ---

  const [edicion, setEdicion] = useState<EdicionEstadoPrograma | null>(null)
  const botonesCambiarEstado = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLSelectElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el error no hay que robarle
    // el foco a lo que esté tocando, mismo criterio que PantallaDeObras.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(programa: ProgramaSocial) {
    setEdicion({ id: programa.id, estadoNuevo: OPUESTO_ESTADO_PROGRAMA[programa.estado], enviando: false, error: null })
  }

  function cerrarEdicion(idPrograma: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idPrograma)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/desarrollosocial/programas/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado del programa.',
      )
      await cargarCatalogo(filtrosAplicados)
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
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del programa.',
              }
            : actual,
        )
      }
    }
  }

  if (vista === 'inscripcion') {
    return <FormularioDeInscripcion modulo={modulo} catalogo={catalogo} onVolver={() => setVista('catalogo')} />
  }

  if (vista === 'seguimiento') {
    return <ConsultaDeSeguimiento modulo={modulo} onVolver={() => setVista('catalogo')} />
  }

  if (vista === 'bandeja') {
    if (!puedeRevisarInscripciones) {
      return null
    }
    return <BandejaDeInscripciones modulo={modulo} catalogo={catalogo} onVolver={() => setVista('catalogo')} />
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Desarrollo Social'}
      </h1>
      <p className="contenido__bajada">
        Programas sociales vigentes en este municipio y su convocatoria. No
        hace falta tener cuenta ni iniciar sesión para consultarlos o
        inscribirte.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
        <button type="button" className="boton boton--secundario" onClick={() => setVista('inscripcion')}>
          Inscribirme a un programa
        </button>
        <button type="button" className="boton boton--secundario" onClick={() => setVista('seguimiento')}>
          ¿Ya te inscribiste? Consultá el estado
        </button>
        {puedeRevisarInscripciones && (
          <button type="button" className="boton boton--secundario" onClick={() => setVista('bandeja')}>
            Ver inscripciones recibidas
          </button>
        )}
      </div>

      <section aria-labelledby="titulo-busqueda-programas">
        <h2 id="titulo-busqueda-programas">Buscar programas sociales</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="ds-catalogo-filtro-estado">Estado</label>
            <select
              id="ds-catalogo-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDePrograma | '')}
            >
              <option value="">Todos</option>
              {ESTADOS_DE_PROGRAMA.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="ds-catalogo-filtro-q">Buscar en nombre o descripción</label>
            <input id="ds-catalogo-filtro-q" value={qFiltro} onChange={(evento) => setQFiltro(evento.target.value)} />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {catalogo.estado === 'cargando' && <p role="status">Buscando programas sociales…</p>}

        {catalogo.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? catalogo.moduloDelError)}
          </p>
        )}

        {catalogo.estado === 'error' && <p role="alert">{catalogo.mensaje}</p>}

        {catalogo.estado === 'listo' && catalogo.programas.length === 0 && (
          <p>No se encontraron programas sociales.</p>
        )}

        {catalogo.estado === 'listo' && catalogo.programas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Programas sociales publicados por el municipio. Se puede
                filtrar por estado y por texto en el nombre o la
                descripción.
                {puedeGestionarProgramas &&
                  ' Se puede abrir o cerrar la convocatoria de cada uno.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Nombre</th>
                  <th scope="col">Descripción</th>
                  <th scope="col">Estado</th>
                  {puedeGestionarProgramas && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {catalogo.programas.map((programa) => {
                  const enEdicion = edicion && edicion.id === programa.id ? edicion : null

                  return (
                    <tr key={programa.id}>
                      <th scope="row">{programa.nombre}</th>
                      <td>{textoOVacio(programa.descripcion)}</td>
                      <td>{ETIQUETA_ESTADO_PROGRAMA[programa.estado]}</td>
                      {puedeGestionarProgramas && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`ds-programa-${programa.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`ds-programa-${programa.id}-estado`}
                                  ref={primerCampoEdicion}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicion((actual) =>
                                      actual
                                        ? { ...actual, estadoNuevo: evento.target.value as EstadoDePrograma }
                                        : actual,
                                    )
                                  }
                                >
                                  <option value={OPUESTO_ESTADO_PROGRAMA[programa.estado]}>
                                    {ETIQUETA_ESTADO_PROGRAMA[OPUESTO_ESTADO_PROGRAMA[programa.estado]]}
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
                                onClick={() => cerrarEdicion(programa.id)}
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
                                  botonesCambiarEstado.current.set(programa.id, elemento)
                                } else {
                                  botonesCambiarEstado.current.delete(programa.id)
                                }
                              }}
                              onClick={() => abrirEdicion(programa)}
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
      </section>

      {puedeGestionarProgramas && (
        <section aria-labelledby="titulo-publicar-programa">
          <h2 id="titulo-publicar-programa">Publicar un programa</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormularioDePublicacion}>
                Publicar programa
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarPrograma(evento)}>
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
                <label htmlFor="ds-publicar-nombre">Nombre</label>
                <input
                  id="ds-publicar-nombre"
                  ref={primerCampoRegistro}
                  required
                  value={registro.nombre}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, nombre: evento.target.value }))}
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="ds-publicar-descripcion">Descripción (opcional)</label>
                <textarea
                  id="ds-publicar-descripcion"
                  value={registro.descripcion}
                  onChange={(evento) => setRegistro((actual) => ({ ...actual, descripcion: evento.target.value }))}
                />
              </div>

              <div className="campo">
                <label htmlFor="ds-publicar-criterios">Criterios de elegibilidad (opcional)</label>
                <textarea
                  id="ds-publicar-criterios"
                  value={registro.criteriosDeElegibilidad}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, criteriosDeElegibilidad: evento.target.value }))
                  }
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

// --- Formulario público de alta de inscripción (sin sesión) ---

type PropsFormularioDeInscripcion = {
  modulo?: Modulo
  catalogo: EstadoCatalogo
  onVolver: () => void
}

function FormularioDeInscripcion({ modulo, catalogo, onVolver }: PropsFormularioDeInscripcion) {
  const [programaId, setProgramaId] = useState<number | ''>('')
  const [nombreSolicitante, setNombreSolicitante] = useState('')
  const [dniSolicitante, setDniSolicitante] = useState('')
  const [contacto, setContacto] = useState('')
  const [cantidadIntegrantes, setCantidadIntegrantes] = useState('')
  const [situacionDeclarada, setSituacionDeclarada] = useState<SituacionDeclarada | ''>('')
  const [comentarioAdicional, setComentarioAdicional] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<InscripcionPublica | null>(null)
  const [tokenCopiado, setTokenCopiado] = useState(false)

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

  const programasAbiertos = catalogo.estado === 'listo' ? catalogo.programas.filter((p) => p.estado === 'ABIERTO') : []

  async function inscribirse(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setConfirmacion(null)
    setEnviando(true)
    try {
      const respuesta = await enviar<InscripcionPublica>(
        '/api/desarrollosocial/inscripciones',
        'POST',
        {
          programaId,
          nombreSolicitante,
          dniSolicitante,
          contacto,
          cantidadIntegrantesGrupoFamiliar: Number(cantidadIntegrantes),
          situacionDeclarada,
          comentarioAdicional: comentarioAdicional.trim() === '' ? null : comentarioAdicional,
        },
        'No se pudo registrar la inscripción.',
      )
      if (!vigente.current) {
        return
      }
      if (respuesta) {
        setConfirmacion(respuesta)
        setProgramaId('')
        setNombreSolicitante('')
        setDniSolicitante('')
        setContacto('')
        setCantidadIntegrantes('')
        setSituacionDeclarada('')
        setComentarioAdicional('')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setError(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'desarrollosocial'))
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo registrar la inscripción.')
      }
    } finally {
      if (vigente.current) {
        setEnviando(false)
      }
    }
  }

  /**
   * Copia el token al portapapeles como comodidad extra, mismo criterio que
   * `PantallaDeReclamos#copiarToken`: el código ya queda visible y
   * seleccionable a mano en el campo de solo lectura de abajo.
   */
  async function copiarToken(token: string) {
    try {
      await navigator.clipboard.writeText(token)
      if (vigente.current) {
        setTokenCopiado(true)
        window.setTimeout(() => {
          if (vigente.current) {
            setTokenCopiado(false)
          }
        }, 2000)
      }
    } catch {
      // Sin portapapeles disponible: el código sigue ahí para copiar a mano.
    }
  }

  const idDelError = 'error-de-alta-inscripcion'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Inscribirme a un programa social
      </h1>
      <p className="contenido__bajada">
        Contanos tu situación: no hace falta subir ningún comprobante ni
        tener cuenta para inscribirte.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al catálogo
        </button>
      </div>

      {catalogo.estado === 'cargando' && <p role="status">Cargando programas sociales…</p>}

      {catalogo.estado === 'no-contratado' && (
        <p className="formulario__error" role="alert">
          {mensajeModuloNoContratado(modulo?.nombre ?? catalogo.moduloDelError)}
        </p>
      )}

      {catalogo.estado === 'error' && <p role="alert">{catalogo.mensaje}</p>}

      {catalogo.estado === 'listo' && programasAbiertos.length === 0 && (
        <p>No hay programas sociales con inscripción abierta en este momento.</p>
      )}

      {catalogo.estado === 'listo' && programasAbiertos.length > 0 && (
        <form className="formulario" onSubmit={(evento) => void inscribirse(evento)}>
          {error && (
            <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
              {error}
            </p>
          )}

          {confirmacion && (
            <div role="status" tabIndex={-1} ref={confirmacionRef}>
              <p>
                Tu inscripción quedó registrada con el número {confirmacion.id}.
                Vas a ver el estado «Recibida» hasta que el municipio la
                revise.
              </p>
              <p>
                <strong>
                  Guardá este código de seguimiento: es la única forma de
                  volver a consultar el estado de tu inscripción más
                  adelante.
                </strong>{' '}
                No lo vamos a reenviar por ningún otro medio ni lo vas a
                poder recuperar si lo perdés.
              </p>
              <div className="campo">
                <label htmlFor="ds-inscripcion-token-generado">Código de seguimiento</label>
                <div className="formulario__acciones formulario__acciones--compacto">
                  <input
                    id="ds-inscripcion-token-generado"
                    readOnly
                    value={confirmacion.tokenDeSeguimiento}
                    onFocus={(evento) => evento.target.select()}
                  />
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={() => void copiarToken(confirmacion.tokenDeSeguimiento)}
                  >
                    {tokenCopiado ? 'Copiado' : 'Copiar'}
                  </button>
                </div>
              </div>
            </div>
          )}

          <div className="campo">
            <label htmlFor="ds-inscripcion-programa">Programa</label>
            <select
              id="ds-inscripcion-programa"
              required
              value={programaId}
              onChange={(evento) => setProgramaId(evento.target.value === '' ? '' : Number(evento.target.value))}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            >
              <option value="" disabled>
                Elegí un programa
              </option>
              {programasAbiertos.map((programa) => (
                <option key={programa.id} value={programa.id}>
                  {programa.nombre}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-nombre">Nombre y apellido</label>
            <input
              id="ds-inscripcion-nombre"
              required
              value={nombreSolicitante}
              onChange={(evento) => setNombreSolicitante(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-dni">DNI</label>
            <input
              id="ds-inscripcion-dni"
              required
              value={dniSolicitante}
              onChange={(evento) => setDniSolicitante(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-contacto">Contacto</label>
            <input
              id="ds-inscripcion-contacto"
              required
              value={contacto}
              onChange={(evento) => setContacto(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${idDelError} ds-inscripcion-contacto-ayuda` : 'ds-inscripcion-contacto-ayuda'}
            />
            <p className="campo__ayuda" id="ds-inscripcion-contacto-ayuda">
              Un teléfono o un email donde el municipio pueda contactarte.
            </p>
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-integrantes">Cantidad de integrantes del grupo familiar</label>
            <input
              id="ds-inscripcion-integrantes"
              type="number"
              min="1"
              required
              value={cantidadIntegrantes}
              onChange={(evento) => setCantidadIntegrantes(evento.target.value)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-situacion">Situación declarada</label>
            <select
              id="ds-inscripcion-situacion"
              required
              value={situacionDeclarada}
              onChange={(evento) => setSituacionDeclarada(evento.target.value as SituacionDeclarada)}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? idDelError : undefined}
            >
              <option value="" disabled>
                Elegí una situación
              </option>
              {SITUACIONES_DECLARADAS.map((opcion) => (
                <option key={opcion.valor} value={opcion.valor}>
                  {opcion.etiqueta}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="ds-inscripcion-comentario">Comentario adicional (opcional)</label>
            <textarea
              id="ds-inscripcion-comentario"
              value={comentarioAdicional}
              onChange={(evento) => setComentarioAdicional(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
              {enviando ? 'Enviando…' : 'Inscribirme'}
            </button>
          </div>
        </form>
      )}
    </main>
  )
}

// --- Consulta pública por token de seguimiento (ADR 0017) ---

type PropsConsulta = {
  modulo?: Modulo
  onVolver: () => void
}

/**
 * Sub-vista pública: un vecino sin sesión, con el token que recibió al
 * inscribirse, consulta en qué quedó su inscripción — de solo lectura, sin
 * ninguna acción posible. Mismo patrón exacto que `ConsultaDeSeguimiento`
 * en `PantallaDeReclamos.tsx`, adaptado al shape minimizado de
 * `SeguimientoDeInscripcionResponse` (ADR 0025 §6).
 */
function ConsultaDeSeguimiento({ modulo, onVolver }: PropsConsulta) {
  const [codigo, setCodigo] = useState('')
  const [consultando, setConsultando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<SeguimientoDeInscripcion | null>(null)

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const resultadoRef = useRef<HTMLDListElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  useEffect(() => {
    if (error) {
      errorRef.current?.focus()
    }
  }, [error])

  useEffect(() => {
    if (resultado) {
      resultadoRef.current?.focus()
    }
  }, [resultado])

  async function consultar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setResultado(null)
    setConsultando(true)
    try {
      const respuesta = await pedir<SeguimientoDeInscripcion>(
        `/api/desarrollosocial/inscripciones/seguimiento/${encodeURIComponent(codigo.trim())}`,
        'No pudimos encontrar una inscripción con ese código.',
      )
      if (!vigente.current) {
        return
      }
      setResultado(respuesta)
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setError(fallo instanceof Error ? fallo.message : 'No pudimos encontrar una inscripción con ese código.')
    } finally {
      if (vigente.current) {
        setConsultando(false)
      }
    }
  }

  const idDelError = 'error-de-consulta-inscripcion'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Consultar el estado de una inscripción
      </h1>
      <p className="contenido__bajada">
        Ingresá el código de seguimiento que recibiste al inscribirte en{' '}
        {modulo?.nombre ?? 'Desarrollo Social'}, sin espacios ni caracteres
        de más.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al catálogo
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void consultar(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        <div className="campo">
          <label htmlFor="ds-codigo-seguimiento">Código de seguimiento</label>
          <input
            id="ds-codigo-seguimiento"
            required
            value={codigo}
            onChange={(evento) => setCodigo(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={consultando} aria-busy={consultando}>
            {consultando ? 'Consultando…' : 'Consultar'}
          </button>
        </div>
      </form>

      {resultado && (
        <dl className="ficha" role="status" tabIndex={-1} ref={resultadoRef}>
          <div className="ficha__fila">
            <dt>Programa</dt>
            <dd>{resultado.nombrePrograma}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Estado</dt>
            <dd>{ETIQUETA_ESTADO_INSCRIPCION[resultado.estado]}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Comentario de resolución</dt>
            <dd>{resultado.comentarioDeResolucion ?? 'Todavía no hay comentario de resolución.'}</dd>
          </div>
        </dl>
      )}
    </main>
  )
}

// --- Bandeja de inscripciones (con desarrollosocial.revisarInscripciones) ---

type PropsBandeja = {
  modulo?: Modulo
  catalogo: EstadoCatalogo
  onVolver: () => void
}

type EstadoBandeja =
  | { estado: 'cargando' }
  | { estado: 'listo'; inscripciones: InscripcionCompleta[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EdicionInscripcion = {
  id: number
  estadoNuevo: EstadoDeInscripcion
  comentarioDeResolucion: string
  enviando: boolean
  error: string | null
}

function BandejaDeInscripciones({ modulo, catalogo, onVolver }: PropsBandeja) {
  const [programaIdFiltro, setProgramaIdFiltro] = useState<number | ''>('')
  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeInscripcion | ''>('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    programaId: number | ''
    estado: EstadoDeInscripcion | ''
  }>({ programaId: '', estado: '' })

  const [estado, setEstado] = useState<EstadoBandeja>({ estado: 'cargando' })

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarInscripciones = useCallback(
    async (filtros: { programaId: number | ''; estado: EstadoDeInscripcion | '' }) => {
      const parametros = new URLSearchParams()
      if (filtros.programaId !== '') {
        parametros.set('programaId', String(filtros.programaId))
      }
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      const query = parametros.toString()
      try {
        const inscripciones = await pedir<InscripcionCompleta[]>(
          `/api/desarrollosocial/inscripciones${query ? `?${query}` : ''}`,
          'No se pudo cargar el listado de inscripciones.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', inscripciones })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'desarrollosocial' })
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
    // eslint-disable-next-line react/set-state-in-effect
    void cargarInscripciones(filtrosAplicados)
  }, [cargarInscripciones, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ programaId: programaIdFiltro, estado: estadoFiltro })
  }

  // --- Cambio de estado de una inscripción, por fila ---

  const [edicion, setEdicion] = useState<EdicionInscripcion | null>(null)
  const botonesCambiarEstado = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLSelectElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el select, el comentario o el
    // error no hay que robarle el foco a lo que esté tocando, mismo
    // criterio que PantallaDeReclamos.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(inscripcion: InscripcionCompleta) {
    const opciones = TRANSICIONES_VALIDAS_INSCRIPCION[inscripcion.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({
      id: inscripcion.id,
      estadoNuevo: opciones[0],
      comentarioDeResolucion: '',
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion(idInscripcion: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idInscripcion)?.focus()
  }

  // El comentario de resolución solo se pide (y solo se muestra el campo)
  // cuando el destino elegido es APROBADA o RECHAZADA: si el destino es
  // EN_EVALUACION no hace falta comentario, mismo criterio que exige el
  // backend (`GestionDeInscripcionesSociales.actualizarEstado`).
  function requiereComentario(estadoNuevo: EstadoDeInscripcion): boolean {
    return estadoNuevo === 'APROBADA' || estadoNuevo === 'RECHAZADA'
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/desarrollosocial/inscripciones/${edicion.id}/estado`,
        'PATCH',
        {
          estadoNuevo: edicion.estadoNuevo,
          comentarioDeResolucion:
            edicion.comentarioDeResolucion.trim() === '' ? null : edicion.comentarioDeResolucion,
        },
        'No se pudo actualizar el estado de la inscripción.',
      )
      await cargarInscripciones(filtrosAplicados)
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
                  fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado de la inscripción.',
              }
            : actual,
        )
      }
    }
  }

  const programasParaFiltro = catalogo.estado === 'listo' ? catalogo.programas : []

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Inscripciones recibidas
      </h1>
      <p className="contenido__bajada">
        Inscripciones a programas sociales, con sus datos completos. Esta
        vista solo la ve quien tiene el permiso para revisarlas.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al catálogo
        </button>
      </div>

      <form className="formulario" onSubmit={buscar}>
        <div className="campo">
          <label htmlFor="ds-bandeja-filtro-programa">Programa</label>
          <select
            id="ds-bandeja-filtro-programa"
            value={programaIdFiltro}
            onChange={(evento) => setProgramaIdFiltro(evento.target.value === '' ? '' : Number(evento.target.value))}
          >
            <option value="">Todos</option>
            {programasParaFiltro.map((programa) => (
              <option key={programa.id} value={programa.id}>
                {programa.nombre}
              </option>
            ))}
          </select>
        </div>

        <div className="campo">
          <label htmlFor="ds-bandeja-filtro-estado">Estado</label>
          <select
            id="ds-bandeja-filtro-estado"
            value={estadoFiltro}
            onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeInscripcion | '')}
          >
            <option value="">Todos</option>
            {ESTADOS_DE_INSCRIPCION.map((opcion) => (
              <option key={opcion.valor} value={opcion.valor}>
                {opcion.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton">
            Buscar
          </button>
        </div>
      </form>

      {estado.estado === 'cargando' && <p role="status">Cargando inscripciones…</p>}

      {estado.estado === 'no-contratado' && (
        <p className="formulario__error" role="alert">
          {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
        </p>
      )}

      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && estado.inscripciones.length === 0 && <p>No se encontraron inscripciones.</p>}

      {estado.estado === 'listo' && estado.inscripciones.length > 0 && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>
              Inscripciones a programas sociales recibidas por el municipio,
              con sus datos personales. Se puede filtrar por programa y por
              estado, y cambiar el estado de las que todavía admiten una
              transición.
            </caption>
            <thead>
              <tr>
                <th scope="col">Nombre</th>
                <th scope="col">DNI</th>
                <th scope="col">Contacto</th>
                <th scope="col">Integrantes</th>
                <th scope="col">Situación declarada</th>
                <th scope="col">Comentario adicional</th>
                <th scope="col">Estado</th>
                <th scope="col">Comentario de resolución</th>
                <th scope="col">Acción</th>
              </tr>
            </thead>
            <tbody>
              {estado.inscripciones.map((inscripcion) => {
                const enEdicion = edicion && edicion.id === inscripcion.id ? edicion : null
                const opcionesValidas = TRANSICIONES_VALIDAS_INSCRIPCION[inscripcion.estado]
                const comentarioObligatorio = enEdicion ? requiereComentario(enEdicion.estadoNuevo) : false

                return (
                  <tr key={inscripcion.id}>
                    <th scope="row">{inscripcion.nombreSolicitante}</th>
                    <td>{inscripcion.dniSolicitante}</td>
                    <td>{inscripcion.contacto}</td>
                    <td>{inscripcion.cantidadIntegrantesGrupoFamiliar}</td>
                    <td>{ETIQUETA_SITUACION_DECLARADA[inscripcion.situacionDeclarada]}</td>
                    <td>{textoOVacio(inscripcion.comentarioAdicional)}</td>
                    <td>{ETIQUETA_ESTADO_INSCRIPCION[inscripcion.estado]}</td>
                    <td>{textoOVacio(inscripcion.comentarioDeResolucion)}</td>
                    <td>
                      {enEdicion ? (
                        <div className="formulario__acciones formulario__acciones--compacto">
                          <div className="campo">
                            <label htmlFor={`ds-inscripcion-${inscripcion.id}-estado`}>Nuevo estado</label>
                            <select
                              id={`ds-inscripcion-${inscripcion.id}-estado`}
                              ref={primerCampoEdicion}
                              value={enEdicion.estadoNuevo}
                              onChange={(evento) =>
                                setEdicion((actual) =>
                                  actual
                                    ? { ...actual, estadoNuevo: evento.target.value as EstadoDeInscripcion }
                                    : actual,
                                )
                              }
                            >
                              {opcionesValidas.map((opcion) => (
                                <option key={opcion} value={opcion}>
                                  {ETIQUETA_ESTADO_INSCRIPCION[opcion]}
                                </option>
                              ))}
                            </select>
                          </div>

                          {comentarioObligatorio && (
                            <div className="campo">
                              <label htmlFor={`ds-inscripcion-${inscripcion.id}-comentario`}>
                                Comentario de resolución
                              </label>
                              <textarea
                                id={`ds-inscripcion-${inscripcion.id}-comentario`}
                                required
                                value={enEdicion.comentarioDeResolucion}
                                onChange={(evento) =>
                                  setEdicion((actual) =>
                                    actual ? { ...actual, comentarioDeResolucion: evento.target.value } : actual,
                                  )
                                }
                                aria-invalid={enEdicion.error ? true : undefined}
                                aria-describedby={
                                  enEdicion.error ? `error-de-edicion-inscripcion-${inscripcion.id}` : undefined
                                }
                              />
                            </div>
                          )}

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
                            onClick={() => cerrarEdicion(inscripcion.id)}
                          >
                            Cancelar
                          </button>
                          {enEdicion.error && (
                            <p
                              className="formulario__error"
                              id={`error-de-edicion-inscripcion-${inscripcion.id}`}
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
                              botonesCambiarEstado.current.set(inscripcion.id, elemento)
                            } else {
                              botonesCambiarEstado.current.delete(inscripcion.id)
                            }
                          }}
                          onClick={() => abrirEdicion(inscripcion)}
                        >
                          Cambiar estado
                        </button>
                      ) : (
                        'Sin cambios de estado disponibles'
                      )}
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </main>
  )
}
