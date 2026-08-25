import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { Usuario } from '../../acceso/useSesion'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type Categoria = 'BACHE' | 'ALUMBRADO' | 'PODA_ARBOLADO' | 'RESIDUOS' | 'ANIMALES_SUELTOS' | 'OTRO'

type Estado = 'NUEVO' | 'EN_PROCESO' | 'RESUELTO' | 'RECHAZADO'

type Reclamo = {
  id: number
  categoria: Categoria
  descripcion: string
  direccion: string
  nombreContacto: string | null
  contacto: string | null
  estado: Estado
  comentarioGestion: string | null
  creadoEn: string
  actualizadoEn: string
}

type RespuestaAlta = {
  id: number
  categoria: Categoria
  estado: Estado
  creadoEn: string
}

const CATEGORIAS: { valor: Categoria; etiqueta: string }[] = [
  { valor: 'BACHE', etiqueta: 'Bache' },
  { valor: 'ALUMBRADO', etiqueta: 'Alumbrado público' },
  { valor: 'PODA_ARBOLADO', etiqueta: 'Poda y arbolado' },
  { valor: 'RESIDUOS', etiqueta: 'Recolección de residuos' },
  { valor: 'ANIMALES_SUELTOS', etiqueta: 'Animales sueltos' },
  { valor: 'OTRO', etiqueta: 'Otro' },
]

const ETIQUETA_CATEGORIA: Record<Categoria, string> = CATEGORIAS.reduce(
  (mapa, categoria) => ({ ...mapa, [categoria.valor]: categoria.etiqueta }),
  {} as Record<Categoria, string>,
)

const ETIQUETA_ESTADO: Record<Estado, string> = {
  NUEVO: 'Nuevo',
  EN_PROCESO: 'En proceso',
  RESUELTO: 'Resuelto',
  RECHAZADO: 'Rechazado',
}

// Mismo mapa de transiciones válidas que valida el backend (ADR 0014 §3):
// acá solo decide qué opciones ofrecer en el `<select>`, el enforcement real
// sigue siendo del backend (ADR 0011).
const TRANSICIONES_VALIDAS: Record<Estado, Estado[]> = {
  NUEVO: ['EN_PROCESO', 'RECHAZADO'],
  EN_PROCESO: ['RESUELTO', 'RECHAZADO'],
  RESUELTO: [],
  RECHAZADO: [],
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en `PantallaDeEjemplo` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/** Nombre y datos de contacto que dejó el vecino, o un texto que lo aclara. */
function textoContacto(reclamo: Reclamo): string {
  const partes = [reclamo.nombreContacto, reclamo.contacto].filter(
    (valor): valor is string => Boolean(valor && valor.trim() !== ''),
  )
  return partes.length > 0 ? partes.join(' — ') : 'Sin datos de contacto'
}

/**
 * Pantalla del módulo `reclamos` (311): alta pública y anónima de un
 * reclamo, y panel de gestión para el personal del municipio (ADR 0014).
 *
 * Qué se muestra no depende de la vista sino del permiso: un vecino sin
 * sesión —o cualquier usuario sin `reclamos.ver`— ve el formulario de
 * alta; quien tiene `reclamos.ver` ve el panel de gestión. Es el mismo
 * criterio de "esconder por comodidad, no por seguridad" del resto del
 * frontend (ADR 0011): el backend vuelve a exigir el permiso en cada ruta.
 */
export function PantallaDeReclamos({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeVer = usuario?.permisos.includes('reclamos.ver') ?? false

  return puedeVer && usuario ? (
    <PanelDeGestion modulo={modulo} usuario={usuario} onVolver={onVolver} />
  ) : (
    <FormularioDeAlta modulo={modulo} onVolver={onVolver} />
  )
}

// --- Formulario público de alta (sin sesión) ---

type PropsFormulario = {
  modulo?: Modulo
  onVolver: () => void
}

function FormularioDeAlta({ modulo, onVolver }: PropsFormulario) {
  const [categoria, setCategoria] = useState<Categoria | ''>('')
  const [descripcion, setDescripcion] = useState('')
  const [direccion, setDireccion] = useState('')
  const [nombreContacto, setNombreContacto] = useState('')
  const [contacto, setContacto] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<RespuestaAlta | null>(null)

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const confirmacionRef = useRef<HTMLParagraphElement>(null)

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

  async function enviarReclamo(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setConfirmacion(null)
    setEnviando(true)
    try {
      const respuesta = await enviar<RespuestaAlta>(
        '/api/reclamos',
        'POST',
        {
          categoria,
          descripcion,
          direccion,
          nombreContacto: nombreContacto.trim() === '' ? null : nombreContacto,
          contacto: contacto.trim() === '' ? null : contacto,
        },
        'No se pudo registrar el reclamo.',
      )
      if (!vigente.current) {
        return
      }
      if (respuesta) {
        setConfirmacion(respuesta)
        setCategoria('')
        setDescripcion('')
        setDireccion('')
        setNombreContacto('')
        setContacto('')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setError(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'reclamos'))
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo registrar el reclamo.')
      }
    } finally {
      if (vigente.current) {
        setEnviando(false)
      }
    }
  }

  const idDelError = 'error-de-alta-reclamo'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Reclamos'}
      </h1>
      <p className="contenido__bajada">
        Contanos qué pasa y dónde: el municipio va a revisar tu reclamo. No
        hace falta que tengas cuenta ni que inicies sesión.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void enviarReclamo(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        {confirmacion && (
          <p role="status" tabIndex={-1} ref={confirmacionRef}>
            Tu reclamo quedó registrado con el número {confirmacion.id}. Vas a
            ver el estado «Nuevo» hasta que el municipio lo empiece a
            atender: en esta rebanada todavía no hay una pantalla para
            volver a consultarlo más adelante, así que te conviene anotar el
            número.
          </p>
        )}

        <div className="campo">
          <label htmlFor="reclamo-categoria">Categoría</label>
          <select
            id="reclamo-categoria"
            required
            value={categoria}
            onChange={(evento) => setCategoria(evento.target.value as Categoria)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
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
          <label htmlFor="reclamo-descripcion">Descripción</label>
          <textarea
            id="reclamo-descripcion"
            required
            value={descripcion}
            onChange={(evento) => setDescripcion(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="reclamo-direccion">Dirección</label>
          <input
            id="reclamo-direccion"
            required
            value={direccion}
            onChange={(evento) => setDireccion(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${idDelError} reclamo-direccion-ayuda` : 'reclamo-direccion-ayuda'}
          />
          <p className="campo__ayuda" id="reclamo-direccion-ayuda">
            Indicá la dirección o la referencia más precisa que puedas: en
            esta rebanada no hay mapa ni geolocalización.
          </p>
        </div>

        <div className="campo">
          <label htmlFor="reclamo-nombre">Nombre (opcional)</label>
          <input
            id="reclamo-nombre"
            value={nombreContacto}
            onChange={(evento) => setNombreContacto(evento.target.value)}
          />
        </div>

        <div className="campo">
          <label htmlFor="reclamo-contacto">Teléfono o email de contacto (opcional)</label>
          <input
            id="reclamo-contacto"
            value={contacto}
            onChange={(evento) => setContacto(evento.target.value)}
            aria-describedby="reclamo-contacto-ayuda"
          />
          <p className="campo__ayuda" id="reclamo-contacto-ayuda">
            Es para que el municipio pueda volver a contactarte si hace
            falta. Podés escribirlo como te resulte más cómodo, sin un
            formato exigido.
          </p>
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Enviando…' : 'Registrar reclamo'}
          </button>
        </div>
      </form>
    </main>
  )
}

// --- Panel de gestión (con reclamos.ver, y reclamos.gestionar si aplica) ---

type PropsGestion = {
  modulo?: Modulo
  usuario: Usuario
  onVolver: () => void
}

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; reclamos: Reclamo[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EdicionReclamo = {
  id: number
  estado: Estado
  comentario: string
  enviando: boolean
  error: string | null
}

function PanelDeGestion({ modulo, usuario, onVolver }: PropsGestion) {
  const puedeGestionar = usuario.permisos.includes('reclamos.gestionar')

  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })

  // Mismo patrón que PanelDeUsuarios: evita pisar estado de un componente
  // que ya no está montado cuando un pedido en vuelo termina después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarReclamos = useCallback(async () => {
    try {
      const reclamos = await pedir<Reclamo[]>('/api/reclamos', 'No se pudo cargar la lista de reclamos.')
      if (vigente.current) {
        setEstado({ estado: 'listo', reclamos })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'reclamos' })
      } else {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    void cargarReclamos()
  }, [cargarReclamos])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // --- Edición de estado por fila ---

  const [edicion, setEdicion] = useState<EdicionReclamo | null>(null)
  const botonesCambiarEstado = useRef<Map<number, HTMLButtonElement>>(new Map())
  const primerCampoEdicion = useRef<HTMLSelectElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el select, el comentario o el
    // error no hay que robarle el foco a lo que esté tocando.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(reclamo: Reclamo) {
    const opciones = TRANSICIONES_VALIDAS[reclamo.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({
      id: reclamo.id,
      estado: opciones[0],
      comentario: '',
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion(idReclamo: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idReclamo)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/reclamos/${edicion.id}/estado`,
        'PATCH',
        {
          estado: edicion.estado,
          comentario: edicion.comentario.trim() === '' ? null : edicion.comentario,
        },
        'No se pudo actualizar el estado del reclamo.',
      )
      await cargarReclamos()
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
                  fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del reclamo.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Reclamos'}
      </h1>
      <p className="contenido__bajada">
        Reclamos que los vecinos cargaron desde el portal público, sin
        cuenta.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {estado.estado === 'cargando' && <p role="status">Cargando los reclamos…</p>}

      {estado.estado === 'no-contratado' && (
        <p className="formulario__error" role="alert">
          {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
        </p>
      )}

      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>
              Reclamos cargados por vecinos del municipio, con su estado
              actual.
              {puedeGestionar &&
                ' Se puede cambiar el estado de los que todavía no llegaron a un estado final.'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Categoría</th>
                <th scope="col">Descripción</th>
                <th scope="col">Dirección</th>
                <th scope="col">Contacto</th>
                <th scope="col">Estado</th>
                <th scope="col">Creado</th>
                {puedeGestionar && <th scope="col">Acción</th>}
              </tr>
            </thead>
            <tbody>
              {estado.reclamos.map((reclamo) => {
                const enEdicion = edicion && edicion.id === reclamo.id ? edicion : null
                const opcionesValidas = TRANSICIONES_VALIDAS[reclamo.estado]

                return (
                  <tr key={reclamo.id}>
                    <th scope="row">{ETIQUETA_CATEGORIA[reclamo.categoria]}</th>
                    <td>{reclamo.descripcion}</td>
                    <td>{reclamo.direccion}</td>
                    <td>{textoContacto(reclamo)}</td>
                    <td>{ETIQUETA_ESTADO[reclamo.estado]}</td>
                    <td>{FECHA.format(new Date(reclamo.creadoEn))}</td>
                    {puedeGestionar && (
                      <td>
                        {enEdicion ? (
                          <div className="formulario__acciones formulario__acciones--compacto">
                            <div className="campo">
                              <label htmlFor={`reclamo-${reclamo.id}-estado`}>Nuevo estado</label>
                              <select
                                id={`reclamo-${reclamo.id}-estado`}
                                ref={primerCampoEdicion}
                                value={enEdicion.estado}
                                onChange={(evento) =>
                                  setEdicion((actual) =>
                                    actual
                                      ? { ...actual, estado: evento.target.value as Estado }
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

                            <div className="campo">
                              <label htmlFor={`reclamo-${reclamo.id}-comentario`}>
                                Comentario (opcional)
                              </label>
                              <textarea
                                id={`reclamo-${reclamo.id}-comentario`}
                                value={enEdicion.comentario}
                                onChange={(evento) =>
                                  setEdicion((actual) =>
                                    actual ? { ...actual, comentario: evento.target.value } : actual,
                                  )
                                }
                              />
                            </div>

                            <button
                              type="button"
                              className="boton"
                              disabled={enEdicion.enviando}
                              aria-busy={enEdicion.enviando}
                              onClick={() => void guardarEdicion()}
                            >
                              {enEdicion.enviando ? 'Guardando…' : 'Guardar'}
                            </button>
                            <button
                              type="button"
                              className="boton boton--secundario"
                              onClick={() => cerrarEdicion(reclamo.id)}
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
                                botonesCambiarEstado.current.set(reclamo.id, elemento)
                              } else {
                                botonesCambiarEstado.current.delete(reclamo.id)
                              }
                            }}
                            onClick={() => abrirEdicion(reclamo)}
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
    </main>
  )
}
