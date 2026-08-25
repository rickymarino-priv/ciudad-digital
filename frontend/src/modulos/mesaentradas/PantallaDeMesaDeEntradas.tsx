import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { Usuario } from '../../acceso/useSesion'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type Estado = 'INICIADO' | 'EN_REVISION' | 'APROBADO' | 'RECHAZADO'

type Movimiento = {
  estadoAnterior: Estado | null
  estadoNuevo: Estado
  actorNombre: string | null
  actorEmail: string | null
  comentario: string | null
  fecha: string
}

type Expediente = {
  id: number
  tipo: 'CERTIFICADO_DOMICILIO'
  estado: Estado
  solicitanteNombre: string
  solicitanteContacto: string | null
  domicilioACertificar: string
  creadoEn: string
  actualizadoEn: string
  movimientos: Movimiento[]
}

type RespuestaAlta = {
  id: number
  tipo: 'CERTIFICADO_DOMICILIO'
  estado: Estado
  creadoEn: string
}

const ETIQUETA_ESTADO: Record<Estado, string> = {
  INICIADO: 'Iniciado',
  EN_REVISION: 'En revisión',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
}

// Mismo mapa de transiciones válidas que valida el backend
// (CircuitosDeTramite, ADR 0015), definido acá localmente para el único
// tipo de trámite que esta pantalla conoce hoy (CERTIFICADO_DOMICILIO):
// acá solo decide qué opciones ofrecer en el `<select>`, el enforcement
// real sigue siendo del backend (ADR 0011).
const TRANSICIONES_VALIDAS: Record<Estado, Estado[]> = {
  INICIADO: ['EN_REVISION'],
  EN_REVISION: ['APROBADO', 'RECHAZADO'],
  APROBADO: [],
  RECHAZADO: [],
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en `PantallaDeEjemplo` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/** Nombre y datos de contacto que dejó el vecino, o un texto que lo aclara. */
function textoContacto(expediente: Expediente): string {
  return expediente.solicitanteContacto && expediente.solicitanteContacto.trim() !== ''
    ? expediente.solicitanteContacto
    : 'Sin datos de contacto'
}

/** Texto de una línea del historial: `fecha — estado (actor o "Alta pública")`. */
function textoMovimiento(movimiento: Movimiento): string {
  const actor = movimiento.actorNombre ?? 'Alta pública'
  return `${FECHA.format(new Date(movimiento.fecha))} — ${ETIQUETA_ESTADO[movimiento.estadoNuevo]} (${actor})`
}

/**
 * Pantalla del módulo `mesaentradas` (R9): alta pública y anónima de un
 * trámite, y panel de gestión para el personal del municipio (ADR 0015).
 *
 * Qué se muestra no depende de la vista sino del permiso: un vecino sin
 * sesión —o cualquier usuario sin `mesaentradas.ver`— ve el formulario de
 * alta; quien tiene `mesaentradas.ver` ve el panel de gestión. Es el mismo
 * criterio de "esconder por comodidad, no por seguridad" del resto del
 * frontend (ADR 0011): el backend vuelve a exigir el permiso en cada ruta.
 */
export function PantallaDeMesaDeEntradas({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeVer = usuario?.permisos.includes('mesaentradas.ver') ?? false

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
  const [solicitanteNombre, setSolicitanteNombre] = useState('')
  const [solicitanteContacto, setSolicitanteContacto] = useState('')
  const [domicilioACertificar, setDomicilioACertificar] = useState('')
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

  async function enviarTramite(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setConfirmacion(null)
    setEnviando(true)
    try {
      const respuesta = await enviar<RespuestaAlta>(
        '/api/mesaentradas',
        'POST',
        {
          tipo: 'CERTIFICADO_DOMICILIO',
          solicitanteNombre,
          solicitanteContacto: solicitanteContacto.trim() === '' ? null : solicitanteContacto,
          domicilioACertificar,
        },
        'No se pudo registrar el trámite.',
      )
      if (!vigente.current) {
        return
      }
      if (respuesta) {
        setConfirmacion(respuesta)
        setSolicitanteNombre('')
        setSolicitanteContacto('')
        setDomicilioACertificar('')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setError(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'mesaentradas'))
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo registrar el trámite.')
      }
    } finally {
      if (vigente.current) {
        setEnviando(false)
      }
    }
  }

  const idDelError = 'error-de-alta-tramite'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Mesa de Entradas'}
      </h1>
      <p className="contenido__bajada">
        Iniciá acá tu trámite de certificado de domicilio. No hace falta
        que tengas cuenta ni que inicies sesión.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void enviarTramite(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        {confirmacion && (
          <p role="status" tabIndex={-1} ref={confirmacionRef}>
            Tu trámite quedó registrado con el número {confirmacion.id}. Vas
            a ver el estado «Iniciado» hasta que Mesa de Entradas lo empiece
            a revisar: en esta rebanada todavía no hay una pantalla para
            volver a consultarlo más adelante, así que te conviene anotar
            el número.
          </p>
        )}

        <div className="campo">
          <label htmlFor="tramite-solicitante-nombre">Nombre y apellido</label>
          <input
            id="tramite-solicitante-nombre"
            required
            value={solicitanteNombre}
            onChange={(evento) => setSolicitanteNombre(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="tramite-domicilio">Domicilio a certificar</label>
          <textarea
            id="tramite-domicilio"
            required
            value={domicilioACertificar}
            onChange={(evento) => setDomicilioACertificar(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${idDelError} tramite-domicilio-ayuda` : 'tramite-domicilio-ayuda'}
          />
          <p className="campo__ayuda" id="tramite-domicilio-ayuda">
            Es el domicilio sobre el que el municipio va a emitir el
            certificado: indicalo completo, con la referencia más precisa
            que puedas.
          </p>
        </div>

        <div className="campo">
          <label htmlFor="tramite-contacto">Teléfono o email de contacto (opcional)</label>
          <input
            id="tramite-contacto"
            value={solicitanteContacto}
            onChange={(evento) => setSolicitanteContacto(evento.target.value)}
            aria-describedby="tramite-contacto-ayuda"
          />
          <p className="campo__ayuda" id="tramite-contacto-ayuda">
            Es para que el municipio pueda volver a contactarte si hace
            falta. Podés escribirlo como te resulte más cómodo, sin un
            formato exigido.
          </p>
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Enviando…' : 'Iniciar trámite'}
          </button>
        </div>
      </form>
    </main>
  )
}

// --- Panel de gestión (con mesaentradas.ver, y mesaentradas.gestionar si aplica) ---

type PropsGestion = {
  modulo?: Modulo
  usuario: Usuario
  onVolver: () => void
}

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; expedientes: Expediente[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EdicionExpediente = {
  id: number
  estado: Estado
  comentario: string
  enviando: boolean
  error: string | null
}

function PanelDeGestion({ modulo, usuario, onVolver }: PropsGestion) {
  const puedeGestionar = usuario.permisos.includes('mesaentradas.gestionar')

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

  const cargarExpedientes = useCallback(async () => {
    try {
      const expedientes = await pedir<Expediente[]>(
        '/api/mesaentradas',
        'No se pudo cargar la lista de trámites.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', expedientes })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'mesaentradas' })
      } else {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    void cargarExpedientes()
  }, [cargarExpedientes])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // --- Edición de estado por fila ---

  const [edicion, setEdicion] = useState<EdicionExpediente | null>(null)
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

  function abrirEdicion(expediente: Expediente) {
    const opciones = TRANSICIONES_VALIDAS[expediente.estado]
    if (opciones.length === 0) {
      return
    }
    setEdicion({
      id: expediente.id,
      estado: opciones[0],
      comentario: '',
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion(idExpediente: number) {
    setEdicion(null)
    botonesCambiarEstado.current.get(idExpediente)?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/mesaentradas/${edicion.id}/estado`,
        'PATCH',
        {
          estado: edicion.estado,
          comentario: edicion.comentario.trim() === '' ? null : edicion.comentario,
        },
        'No se pudo actualizar el estado del trámite.',
      )
      await cargarExpedientes()
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
                  fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del trámite.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Mesa de Entradas'}
      </h1>
      <p className="contenido__bajada">
        Trámites que los vecinos iniciaron desde el portal público, sin
        cuenta.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {estado.estado === 'cargando' && <p role="status">Cargando los trámites…</p>}

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
              Trámites de Mesa de Entradas iniciados por vecinos del
              municipio, con su estado actual e historial de movimientos.
              {puedeGestionar &&
                ' Se puede cambiar el estado de los que todavía no llegaron a un estado final.'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Solicitante</th>
                <th scope="col">Contacto</th>
                <th scope="col">Domicilio a certificar</th>
                <th scope="col">Estado</th>
                <th scope="col">Historial</th>
                <th scope="col">Creado</th>
                {puedeGestionar && <th scope="col">Acción</th>}
              </tr>
            </thead>
            <tbody>
              {estado.expedientes.map((expediente) => {
                const enEdicion = edicion && edicion.id === expediente.id ? edicion : null
                const opcionesValidas = TRANSICIONES_VALIDAS[expediente.estado]

                return (
                  <tr key={expediente.id}>
                    <th scope="row">{expediente.solicitanteNombre}</th>
                    <td>{textoContacto(expediente)}</td>
                    <td>{expediente.domicilioACertificar}</td>
                    <td>{ETIQUETA_ESTADO[expediente.estado]}</td>
                    <td>
                      <ul className="lista-compacta">
                        {expediente.movimientos.map((movimiento, indice) => (
                          <li key={indice}>{textoMovimiento(movimiento)}</li>
                        ))}
                      </ul>
                    </td>
                    <td>{FECHA.format(new Date(expediente.creadoEn))}</td>
                    {puedeGestionar && (
                      <td>
                        {enEdicion ? (
                          <div className="formulario__acciones formulario__acciones--compacto">
                            <div className="campo">
                              <label htmlFor={`tramite-${expediente.id}-estado`}>Nuevo estado</label>
                              <select
                                id={`tramite-${expediente.id}-estado`}
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
                              <label htmlFor={`tramite-${expediente.id}-comentario`}>
                                Comentario (opcional)
                              </label>
                              <textarea
                                id={`tramite-${expediente.id}-comentario`}
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
                              onClick={() => cerrarEdicion(expediente.id)}
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
                                botonesCambiarEstado.current.set(expediente.id, elemento)
                              } else {
                                botonesCambiarEstado.current.delete(expediente.id)
                              }
                            }}
                            onClick={() => abrirEdicion(expediente)}
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
