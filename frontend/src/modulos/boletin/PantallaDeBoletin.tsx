import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type TipoDeNorma = 'ORDENANZA' | 'DECRETO' | 'RESOLUCION' | 'COMUNICADO'

type Norma = {
  id: number
  tipo: TipoDeNorma
  numero: string
  titulo: string
  texto: string
  fechaPublicacion: string
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
}

const TIPOS: { valor: TipoDeNorma; etiqueta: string }[] = [
  { valor: 'ORDENANZA', etiqueta: 'Ordenanza' },
  { valor: 'DECRETO', etiqueta: 'Decreto' },
  { valor: 'RESOLUCION', etiqueta: 'Resolución' },
  { valor: 'COMUNICADO', etiqueta: 'Comunicado' },
]

const ETIQUETA_TIPO: Record<TipoDeNorma, string> = TIPOS.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeNorma, string>,
)

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })

/** Mismo texto que en `PantallaDeEjemplo`/`PantallaDeReclamos` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario: pasarle
 * ese string directo a `new Date(...)` lo interpreta en UTC, y en un huso
 * negativo puede mostrar el día anterior. Se arma la fecha a partir de los
 * componentes, en la zona local.
 */
function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; normas: Norma[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoPublicacion = {
  tipo: TipoDeNorma | ''
  numero: string
  titulo: string
  fechaPublicacion: string
  texto: string
  enviando: boolean
  error: string | null
}

const PUBLICACION_INICIAL: EstadoPublicacion = {
  tipo: '',
  numero: '',
  titulo: '',
  fechaPublicacion: '',
  texto: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `boletin`: búsqueda pública de normas (sin sesión) y,
 * dentro de la misma vista, la acción de publicar una norma nueva, visible
 * solo para quien tiene `boletin.publicar` (ADR 0011: se esconde por
 * comodidad, el backend vuelve a exigir el permiso). A diferencia de
 * `reclamos` (R6), acá no hay dos pantallas alternativas según el permiso:
 * la búsqueda es una única vista para todos, y publicar es una acción que
 * aparece adentro.
 */
export function PantallaDeBoletin({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedePublicar = usuario?.permisos.includes('boletin.publicar') ?? false

  const [tipoFiltro, setTipoFiltro] = useState<TipoDeNorma | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{ tipo: TipoDeNorma | ''; q: string }>({
    tipo: '',
    q: '',
  })

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

  const cargarNormas = useCallback(async (filtros: { tipo: TipoDeNorma | ''; q: string }) => {
    const parametros = new URLSearchParams()
    if (filtros.tipo !== '') {
      parametros.set('tipo', filtros.tipo)
    }
    if (filtros.q.trim() !== '') {
      parametros.set('q', filtros.q.trim())
    }
    const query = parametros.toString()
    try {
      const normas = await pedir<Norma[]>(
        `/api/boletin${query ? `?${query}` : ''}`,
        'No se pudo cargar el Boletín Oficial.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', normas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'boletin' })
      } else {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    // Carga inicial y recarga al cambiar los filtros aplicados (mismo
    // patrón que PanelDeAuditoria): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarNormas(filtrosAplicados)
  }, [cargarNormas, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ tipo: tipoFiltro, q: qFiltro })
  }

  // --- Publicación ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [publicacion, setPublicacion] = useState<EstadoPublicacion>(PUBLICACION_INICIAL)

  const botonPublicar = useRef<HTMLButtonElement>(null)
  const primerCampoPublicacion = useRef<HTMLSelectElement>(null)
  const errorPublicacionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioAbierto) {
      primerCampoPublicacion.current?.focus()
    }
  }, [formularioAbierto])

  useEffect(() => {
    if (publicacion.error) {
      errorPublicacionRef.current?.focus()
    }
  }, [publicacion.error])

  function abrirFormulario() {
    setPublicacion(PUBLICACION_INICIAL)
    setFormularioAbierto(true)
  }

  function cerrarFormulario() {
    setFormularioAbierto(false)
    botonPublicar.current?.focus()
  }

  async function publicarNorma(evento: FormEvent) {
    evento.preventDefault()
    setPublicacion((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/boletin',
        'POST',
        {
          tipo: publicacion.tipo,
          numero: publicacion.numero,
          titulo: publicacion.titulo,
          texto: publicacion.texto,
          fechaPublicacion: publicacion.fechaPublicacion,
        },
        'No se pudo publicar la norma.',
      )
      if (!vigente.current) {
        return
      }
      await cargarNormas(filtrosAplicados)
      if (vigente.current) {
        setFormularioAbierto(false)
        botonPublicar.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setPublicacion((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'boletin'),
        }))
      } else {
        setPublicacion((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la norma.',
        }))
      }
    }
  }

  const idDelErrorPublicacion = 'error-de-publicacion-norma'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Boletín Oficial'}
      </h1>
      <p className="contenido__bajada">
        Ordenanzas, decretos, resoluciones y comunicados publicados por el
        municipio. No hace falta tener cuenta ni iniciar sesión para
        buscarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar normas</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="boletin-filtro-tipo">Tipo</label>
            <select
              id="boletin-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeNorma | '')}
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
            <label htmlFor="boletin-filtro-q">Buscar en el título</label>
            <input
              id="boletin-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando normas…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.normas.length === 0 && (
          <p>No se encontraron normas.</p>
        )}

        {estado.estado === 'listo' && estado.normas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Normas publicadas en el Boletín Oficial de este municipio. Se
                puede filtrar por tipo y por texto en el título.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Tipo</th>
                  <th scope="col">Número</th>
                  <th scope="col">Título</th>
                  <th scope="col">Fecha de publicación</th>
                  <th scope="col">Publicado por</th>
                  <th scope="col">Texto</th>
                </tr>
              </thead>
              <tbody>
                {estado.normas.map((norma) => (
                  <tr key={norma.id}>
                    <th scope="row">{ETIQUETA_TIPO[norma.tipo]}</th>
                    <td>{norma.numero}</td>
                    <td>{norma.titulo}</td>
                    <td>{formatearFecha(norma.fechaPublicacion)}</td>
                    <td>{norma.publicadoPorNombre}</td>
                    <td>{norma.texto}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {puedePublicar && (
        <section aria-labelledby="titulo-publicar">
          <h2 id="titulo-publicar">Publicar una norma</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonPublicar} onClick={abrirFormulario}>
                Publicar norma
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarNorma(evento)}>
              {publicacion.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorPublicacion}
                  role="alert"
                  tabIndex={-1}
                  ref={errorPublicacionRef}
                >
                  {publicacion.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="boletin-tipo">Tipo</label>
                <select
                  id="boletin-tipo"
                  ref={primerCampoPublicacion}
                  required
                  value={publicacion.tipo}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({
                      ...actual,
                      tipo: evento.target.value as TipoDeNorma,
                    }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
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
                <label htmlFor="boletin-numero">Número</label>
                <input
                  id="boletin-numero"
                  required
                  value={publicacion.numero}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, numero: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="boletin-titulo">Título</label>
                <input
                  id="boletin-titulo"
                  required
                  value={publicacion.titulo}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, titulo: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="boletin-fecha">Fecha de publicación</label>
                <input
                  id="boletin-fecha"
                  type="date"
                  required
                  value={publicacion.fechaPublicacion}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, fechaPublicacion: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="boletin-texto">Texto</label>
                <textarea
                  id="boletin-texto"
                  required
                  value={publicacion.texto}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, texto: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={publicacion.enviando}
                  aria-busy={publicacion.enviando}
                >
                  {publicacion.enviando ? 'Publicando…' : 'Publicar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormulario}
                  disabled={publicacion.enviando}
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
