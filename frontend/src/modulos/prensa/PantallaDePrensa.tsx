import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type CategoriaDeGacetilla =
  | 'INSTITUCIONAL'
  | 'OBRAS'
  | 'CULTURA'
  | 'DEPORTES'
  | 'SALUD'
  | 'SEGURIDAD'
  | 'OTRAS'

type Gacetilla = {
  id: number
  categoria: CategoriaDeGacetilla
  titulo: string
  texto: string
  fechaPublicacion: string
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
}

const CATEGORIAS: { valor: CategoriaDeGacetilla; etiqueta: string }[] = [
  { valor: 'INSTITUCIONAL', etiqueta: 'Institucional' },
  { valor: 'OBRAS', etiqueta: 'Obras' },
  { valor: 'CULTURA', etiqueta: 'Cultura' },
  { valor: 'DEPORTES', etiqueta: 'Deportes' },
  { valor: 'SALUD', etiqueta: 'Salud' },
  { valor: 'SEGURIDAD', etiqueta: 'Seguridad' },
  { valor: 'OTRAS', etiqueta: 'Otras' },
]

const ETIQUETA_CATEGORIA: Record<CategoriaDeGacetilla, string> = CATEGORIAS.reduce(
  (mapa, categoria) => ({ ...mapa, [categoria.valor]: categoria.etiqueta }),
  {} as Record<CategoriaDeGacetilla, string>,
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
  | { estado: 'listo'; gacetillas: Gacetilla[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoPublicacion = {
  categoria: CategoriaDeGacetilla | ''
  titulo: string
  fechaPublicacion: string
  texto: string
  enviando: boolean
  error: string | null
}

const PUBLICACION_INICIAL: EstadoPublicacion = {
  categoria: '',
  titulo: '',
  fechaPublicacion: '',
  texto: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `prensa`: búsqueda pública de gacetillas (sin sesión)
 * y, dentro de la misma vista, la acción de publicar una gacetilla nueva,
 * visible solo para quien tiene `prensa.publicar` (ADR 0011: se esconde por
 * comodidad, el backend vuelve a exigir el permiso). A diferencia de
 * `reclamos` (R6), acá no hay dos pantallas alternativas según el permiso:
 * la búsqueda es una única vista para todos, y publicar es una acción que
 * aparece adentro.
 */
export function PantallaDePrensa({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedePublicar = usuario?.permisos.includes('prensa.publicar') ?? false

  const [categoriaFiltro, setCategoriaFiltro] = useState<CategoriaDeGacetilla | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{
    categoria: CategoriaDeGacetilla | ''
    q: string
  }>({
    categoria: '',
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

  const cargarGacetillas = useCallback(
    async (filtros: { categoria: CategoriaDeGacetilla | ''; q: string }) => {
      const parametros = new URLSearchParams()
      if (filtros.categoria !== '') {
        parametros.set('categoria', filtros.categoria)
      }
      if (filtros.q.trim() !== '') {
        parametros.set('q', filtros.q.trim())
      }
      const query = parametros.toString()
      try {
        const gacetillas = await pedir<Gacetilla[]>(
          `/api/prensa${query ? `?${query}` : ''}`,
          'No se pudieron cargar las gacetillas de prensa.',
        )
        if (vigente.current) {
          setEstado({ estado: 'listo', gacetillas })
        }
      } catch (fallo: unknown) {
        if (!vigente.current) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'prensa' })
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
    // patrón que PanelDeAuditoria): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarGacetillas(filtrosAplicados)
  }, [cargarGacetillas, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ categoria: categoriaFiltro, q: qFiltro })
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

  async function publicarGacetilla(evento: FormEvent) {
    evento.preventDefault()
    setPublicacion((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/prensa',
        'POST',
        {
          categoria: publicacion.categoria,
          titulo: publicacion.titulo,
          texto: publicacion.texto,
          fechaPublicacion: publicacion.fechaPublicacion,
        },
        'No se pudo publicar la gacetilla.',
      )
      if (!vigente.current) {
        return
      }
      await cargarGacetillas(filtrosAplicados)
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
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'prensa'),
        }))
      } else {
        setPublicacion((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la gacetilla.',
        }))
      }
    }
  }

  const idDelErrorPublicacion = 'error-de-publicacion-gacetilla'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Prensa y Comunicación'}
      </h1>
      <p className="contenido__bajada">
        Gacetillas y comunicados de prensa publicados por el municipio. No
        hace falta tener cuenta ni iniciar sesión para buscarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar gacetillas</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="prensa-filtro-categoria">Categoría</label>
            <select
              id="prensa-filtro-categoria"
              value={categoriaFiltro}
              onChange={(evento) =>
                setCategoriaFiltro(evento.target.value as CategoriaDeGacetilla | '')
              }
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
            <label htmlFor="prensa-filtro-q">Buscar en el título</label>
            <input
              id="prensa-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando gacetillas…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.gacetillas.length === 0 && (
          <p>No se encontraron gacetillas.</p>
        )}

        {estado.estado === 'listo' && estado.gacetillas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Gacetillas de prensa publicadas por este municipio. Se puede
                filtrar por categoría y por texto en el título.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Categoría</th>
                  <th scope="col">Título</th>
                  <th scope="col">Fecha de publicación</th>
                  <th scope="col">Publicado por</th>
                  <th scope="col">Texto</th>
                </tr>
              </thead>
              <tbody>
                {estado.gacetillas.map((gacetilla) => (
                  <tr key={gacetilla.id}>
                    <th scope="row">{ETIQUETA_CATEGORIA[gacetilla.categoria]}</th>
                    <td>{gacetilla.titulo}</td>
                    <td>{formatearFecha(gacetilla.fechaPublicacion)}</td>
                    <td>{gacetilla.publicadoPorNombre}</td>
                    <td>{gacetilla.texto}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {puedePublicar && (
        <section aria-labelledby="titulo-publicar">
          <h2 id="titulo-publicar">Publicar una gacetilla</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonPublicar} onClick={abrirFormulario}>
                Publicar gacetilla
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarGacetilla(evento)}>
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
                <label htmlFor="prensa-categoria">Categoría</label>
                <select
                  id="prensa-categoria"
                  ref={primerCampoPublicacion}
                  required
                  value={publicacion.categoria}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({
                      ...actual,
                      categoria: evento.target.value as CategoriaDeGacetilla,
                    }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
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
                <label htmlFor="prensa-titulo">Título</label>
                <input
                  id="prensa-titulo"
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
                <label htmlFor="prensa-fecha">Fecha de publicación</label>
                <input
                  id="prensa-fecha"
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
                <label htmlFor="prensa-texto">Texto</label>
                <textarea
                  id="prensa-texto"
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
