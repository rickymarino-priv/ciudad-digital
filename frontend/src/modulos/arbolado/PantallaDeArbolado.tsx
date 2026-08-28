import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type EstadoDeArbol = 'PLANTADO' | 'SANO' | 'REQUIERE_INTERVENCION' | 'RETIRADO'

type Arbol = {
  id: number
  especie: string
  ubicacion: string
  descripcion: string | null
  estado: EstadoDeArbol
  fechaDePlantacion: string | null
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
  actualizadoEn: string
}

const ESTADOS: { valor: EstadoDeArbol; etiqueta: string }[] = [
  { valor: 'PLANTADO', etiqueta: 'Plantado' },
  { valor: 'SANO', etiqueta: 'Sano' },
  { valor: 'REQUIERE_INTERVENCION', etiqueta: 'Requiere intervención' },
  { valor: 'RETIRADO', etiqueta: 'Retirado' },
]

const ETIQUETA_ESTADO: Record<EstadoDeArbol, string> = ESTADOS.reduce(
  (mapa, estado) => ({ ...mapa, [estado.valor]: estado.etiqueta }),
  {} as Record<EstadoDeArbol, string>,
)

// Mismo mapa de transiciones válidas que valida el backend
// (`GestionDeArbolado`, ADR 0024 §4): acá solo decide qué opciones ofrecer
// en el `<select>` de cada fila, el enforcement real sigue siendo del
// backend (ADR 0011), mismo criterio que `TRANSICIONES_VALIDAS` en
// `PantallaDeObras`.
const TRANSICIONES_VALIDAS: Record<EstadoDeArbol, EstadoDeArbol[]> = {
  PLANTADO: ['SANO'],
  SANO: ['REQUIERE_INTERVENCION'],
  REQUIERE_INTERVENCION: ['SANO', 'RETIRADO'],
  RETIRADO: [],
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })

/** Mismo texto que en `PantallaDeObras`/`PantallaDeBoletin` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario, mismo
 * criterio que `PantallaDeObras#formatearFecha`: pasarle ese string directo
 * a `new Date(...)` lo interpreta en UTC, y en un huso negativo puede
 * mostrar el día anterior. Se arma la fecha a partir de los componentes, en
 * la zona local. `null` (fecha no cargada) se muestra como "—".
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
  | { estado: 'listo'; arboles: Arbol[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  especie: string
  ubicacion: string
  descripcion: string
  fechaDePlantacion: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  especie: '',
  ubicacion: '',
  descripcion: '',
  fechaDePlantacion: '',
  enviando: false,
  error: null,
}

type EdicionEstado = {
  id: number
  estadoNuevo: EstadoDeArbol
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `arbolado`: búsqueda pública del padrón de árboles
 * urbanos (sin sesión) y, dentro de la misma vista, la acción de registrar
 * un árbol nuevo y de cambiar su estado sanitario, visibles solo para
 * quien tiene `arbolado.gestionar` (ADR 0011: se esconde por comodidad, el
 * backend vuelve a exigir el permiso). Mismo patrón exacto que
 * `PantallaDeObras`/`PantallaDeBoletin` — no el de `PantallaDeReclamos`,
 * que muestra vistas *alternativas* según permiso: acá el listado es el
 * mismo para todos, solo cambia qué acciones se ven (ADR 0024).
 */
export function PantallaDeArbolado({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeGestionar = usuario?.permisos.includes('arbolado.gestionar') ?? false

  const [estadoFiltro, setEstadoFiltro] = useState<EstadoDeArbol | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    estado: EstadoDeArbol | ''
    q: string
  }>({ estado: '', q: '' })

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

  const cargarArboles = useCallback(
    async (filtros: { estado: EstadoDeArbol | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.estado !== '') {
        parametros.set('estado', filtros.estado)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const arboles = await pedir<Arbol[]>(
          `/api/arbolado${query ? `?${query}` : ''}`,
          'No se pudo cargar el padrón de arbolado urbano.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', arboles })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'arbolado' })
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
    void cargarArboles(filtrosAplicados)
  }, [cargarArboles, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ estado: estadoFiltro, q: qFiltro })
  }

  // --- Registro de árbol ---

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

  async function registrarArbol(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/arbolado',
        'POST',
        {
          especie: registro.especie,
          ubicacion: registro.ubicacion,
          descripcion: registro.descripcion.trim() === '' ? null : registro.descripcion,
          fechaDePlantacion: registro.fechaDePlantacion.trim() === '' ? null : registro.fechaDePlantacion,
        },
        'No se pudo registrar el árbol.',
      )
      if (!vigente.current) {
        return
      }
      await cargarArboles(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'arbolado'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar el árbol.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-arbol'

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
    // PantallaDeObras.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(arbol: Arbol) {
    const opciones = TRANSICIONES_VALIDAS[arbol.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({ id: arbol.id, estadoNuevo: opciones[0], enviando: false, error: null })
  }

  function cerrarEdicion(idArbol: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idArbol)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/arbolado/${edicion.id}/estado`,
        'PATCH',
        { estadoNuevo: edicion.estadoNuevo },
        'No se pudo actualizar el estado del árbol.',
      )
      await cargarArboles(filtrosAplicados)
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
                error: fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del árbol.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Arbolado Urbano'}
      </h1>
      <p className="contenido__bajada">
        Árboles urbanos registrados por el municipio: especie, ubicación y
        estado sanitario. No hace falta tener cuenta ni iniciar sesión para
        consultarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar árboles</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="arbolado-filtro-estado">Estado</label>
            <select
              id="arbolado-filtro-estado"
              value={estadoFiltro}
              onChange={(evento) => setEstadoFiltro(evento.target.value as EstadoDeArbol | '')}
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
            <label htmlFor="arbolado-filtro-q">Buscar en especie o ubicación</label>
            <input
              id="arbolado-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando árboles…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.arboles.length === 0 && <p>No se encontraron árboles.</p>}

        {estado.estado === 'listo' && estado.arboles.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Árboles urbanos registrados por el municipio. Se puede
                filtrar por estado y texto en la especie o la ubicación.
                {puedeGestionar &&
                  ' Se puede cambiar el estado de los que todavía admiten una transición.'}
              </caption>
              <thead>
                <tr>
                  <th scope="col">Especie</th>
                  <th scope="col">Ubicación</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha de plantación</th>
                  {puedeGestionar && <th scope="col">Acción</th>}
                </tr>
              </thead>
              <tbody>
                {estado.arboles.map((arbol) => {
                  const enEdicion = edicion && edicion.id === arbol.id ? edicion : null
                  const opcionesValidas = TRANSICIONES_VALIDAS[arbol.estado]

                  return (
                    <tr key={arbol.id}>
                      <th scope="row">{arbol.especie}</th>
                      <td>{arbol.ubicacion}</td>
                      <td>{ETIQUETA_ESTADO[arbol.estado]}</td>
                      <td>{formatearFecha(arbol.fechaDePlantacion)}</td>
                      {puedeGestionar && (
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`arbol-${arbol.id}-estado`}>Nuevo estado</label>
                                <select
                                  id={`arbol-${arbol.id}-estado`}
                                  ref={primerCampoEdicion}
                                  value={enEdicion.estadoNuevo}
                                  onChange={(evento) =>
                                    setEdicion((actual) =>
                                      actual
                                        ? { ...actual, estadoNuevo: evento.target.value as EstadoDeArbol }
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
                                onClick={() => cerrarEdicion(arbol.id)}
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
                                  botonesCambiarEstado.current.set(arbol.id, elemento)
                                } else {
                                  botonesCambiarEstado.current.delete(arbol.id)
                                }
                              }}
                              onClick={() => abrirEdicion(arbol)}
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
          <h2 id="titulo-registrar">Registrar un árbol</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormulario}>
                Registrar árbol
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void registrarArbol(evento)}>
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
                <label htmlFor="arbolado-especie">Especie</label>
                <input
                  id="arbolado-especie"
                  ref={primerCampoRegistro}
                  required
                  value={registro.especie}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, especie: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="arbolado-ubicacion">Ubicación</label>
                <input
                  id="arbolado-ubicacion"
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
                <label htmlFor="arbolado-descripcion">Descripción (opcional)</label>
                <textarea
                  id="arbolado-descripcion"
                  value={registro.descripcion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, descripcion: evento.target.value }))
                  }
                />
              </div>

              <div className="campo">
                <label htmlFor="arbolado-fecha-plantacion">Fecha de plantación (opcional)</label>
                <input
                  id="arbolado-fecha-plantacion"
                  type="date"
                  value={registro.fechaDePlantacion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fechaDePlantacion: evento.target.value }))
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
