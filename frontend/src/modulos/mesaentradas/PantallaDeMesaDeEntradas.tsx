import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { Usuario } from '../../acceso/useSesion'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type Estado = 'INICIADO' | 'EN_REVISION' | 'INSPECCION' | 'APROBADO' | 'RECHAZADO'

type TipoDeTramite = 'CERTIFICADO_DOMICILIO' | 'HABILITACION_COMERCIAL_SIMPLE' | 'PERMISO_OBRA_MENOR'

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
  tipo: TipoDeTramite
  estado: Estado
  solicitanteNombre: string
  solicitanteContacto: string | null
  domicilioACertificar: string | null
  rubroComercial: string | null
  direccionLocal: string | null
  direccionObra: string | null
  descripcionObra: string | null
  creadoEn: string
  actualizadoEn: string
  movimientos: Movimiento[]
}

type RespuestaAlta = {
  id: number
  tipo: TipoDeTramite
  estado: Estado
  creadoEn: string
  /**
   * Secreto que habilita la consulta pública posterior (ADR 0017): el
   * backend lo devuelve en claro una única vez, acá. No se vuelve a poder
   * leer después, así que la pantalla tiene que dejarlo bien visible y
   * copiable.
   */
  tokenDeSeguimiento: string
}

/** Historial sin datos de quién de la planta municipal lo gestionó (ADR
 * 0017 §5): es un dato interno, no algo que el vecino necesite. */
type MovimientoSeguimiento = {
  estadoAnterior: Estado | null
  estadoNuevo: Estado
  comentario: string | null
  fecha: string
}

/** Respuesta de `GET /api/mesaentradas/seguimiento/{token}` (ADR 0017 §5):
 * subconjunto de `Expediente`, sin `solicitanteContacto` ni actor por
 * movimiento. */
type SeguimientoDeExpediente = {
  id: number
  tipo: TipoDeTramite
  estado: Estado
  domicilioACertificar: string | null
  rubroComercial: string | null
  direccionLocal: string | null
  direccionObra: string | null
  descripcionObra: string | null
  creadoEn: string
  actualizadoEn: string
  movimientos: MovimientoSeguimiento[]
}

const ETIQUETA_ESTADO: Record<Estado, string> = {
  INICIADO: 'Iniciado',
  EN_REVISION: 'En revisión',
  INSPECCION: 'En inspección',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
}

const ETIQUETA_TIPO: Record<TipoDeTramite, string> = {
  CERTIFICADO_DOMICILIO: 'Certificado de domicilio',
  HABILITACION_COMERCIAL_SIMPLE: 'Habilitación comercial simple',
  PERMISO_OBRA_MENOR: 'Permiso de obra menor',
}

// Mismos circuitos que valida el backend (CircuitosDeTramite, ADR 0015),
// uno por tipo de trámite: acá solo decide qué opciones ofrecer en el
// `<select>`, el enforcement real sigue siendo del backend (ADR 0011).
const TRANSICIONES_VALIDAS: Record<TipoDeTramite, Record<Estado, Estado[]>> = {
  CERTIFICADO_DOMICILIO: {
    INICIADO: ['EN_REVISION'],
    EN_REVISION: ['APROBADO', 'RECHAZADO'],
    INSPECCION: [],
    APROBADO: [],
    RECHAZADO: [],
  },
  PERMISO_OBRA_MENOR: {
    INICIADO: ['EN_REVISION'],
    EN_REVISION: ['APROBADO', 'RECHAZADO'],
    INSPECCION: [],
    APROBADO: [],
    RECHAZADO: [],
  },
  HABILITACION_COMERCIAL_SIMPLE: {
    INICIADO: ['EN_REVISION'],
    EN_REVISION: ['INSPECCION', 'RECHAZADO'],
    INSPECCION: ['APROBADO', 'RECHAZADO'],
    APROBADO: [],
    RECHAZADO: [],
  },
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
 * Detalle legible de los campos propios de cada tipo de trámite (ADR
 * 0016): una rama por tipo, igual que el `switch` de validación del
 * backend — agregar un tipo cuarto agrega un `case` acá.
 *
 * Toma solo los campos propios del trámite (no todo `Expediente`) para
 * poder reusarse también con `SeguimientoDeExpediente`, que trae los
 * mismos campos propios del tipo pero no el resto del expediente.
 */
function textoDetalle(expediente: {
  tipo: TipoDeTramite
  domicilioACertificar: string | null
  rubroComercial: string | null
  direccionLocal: string | null
  direccionObra: string | null
  descripcionObra: string | null
}): string {
  switch (expediente.tipo) {
    case 'CERTIFICADO_DOMICILIO':
      return expediente.domicilioACertificar ?? ''
    case 'HABILITACION_COMERCIAL_SIMPLE':
      return `Rubro: ${expediente.rubroComercial ?? ''} · Local: ${expediente.direccionLocal ?? ''}`
    case 'PERMISO_OBRA_MENOR':
      return `Obra en ${expediente.direccionObra ?? ''}: ${expediente.descripcionObra ?? ''}`
  }
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
  // Sin router de URLs en este frontend (ADR 0008): la sub-vista de
  // consulta pública por token es un estado local más, igual que el resto
  // de la navegación de la app (ver `App.tsx`).
  const [vista, setVista] = useState<'formulario' | 'consulta'>('formulario')

  const [tipo, setTipo] = useState<TipoDeTramite>('CERTIFICADO_DOMICILIO')
  const [solicitanteNombre, setSolicitanteNombre] = useState('')
  const [solicitanteContacto, setSolicitanteContacto] = useState('')
  const [domicilioACertificar, setDomicilioACertificar] = useState('')
  const [rubroComercial, setRubroComercial] = useState('')
  const [direccionLocal, setDireccionLocal] = useState('')
  const [direccionObra, setDireccionObra] = useState('')
  const [descripcionObra, setDescripcionObra] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [confirmacion, setConfirmacion] = useState<RespuestaAlta | null>(null)
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
    // `vista` como dependencia: al volver de la consulta de seguimiento
    // `FormularioDeAlta` no se remonta (es el mismo `return` condicional
    // dentro del mismo componente), así que sin esto el título nunca
    // recuperaba el foco y quedaba en `<body>`. Cuando `vista` pasa a
    // 'consulta' el componente ya devolvió `<ConsultaDeSeguimiento />` antes
    // de este render, así que `titulo.current` apunta al `h1` que se acaba
    // de desmontar (React lo deja en `null`) y `focus()` no hace nada.
    titulo.current?.focus()
  }, [vista])

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

  /** Cambiar de tipo resetea los campos propios de los otros tipos. */
  function cambiarTipo(nuevoTipo: TipoDeTramite) {
    setTipo(nuevoTipo)
    setDomicilioACertificar('')
    setRubroComercial('')
    setDireccionLocal('')
    setDireccionObra('')
    setDescripcionObra('')
  }

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
          tipo,
          solicitanteNombre,
          solicitanteContacto: solicitanteContacto.trim() === '' ? null : solicitanteContacto,
          domicilioACertificar: tipo === 'CERTIFICADO_DOMICILIO' ? domicilioACertificar : null,
          rubroComercial: tipo === 'HABILITACION_COMERCIAL_SIMPLE' ? rubroComercial : null,
          direccionLocal: tipo === 'HABILITACION_COMERCIAL_SIMPLE' ? direccionLocal : null,
          direccionObra: tipo === 'PERMISO_OBRA_MENOR' ? direccionObra : null,
          descripcionObra: tipo === 'PERMISO_OBRA_MENOR' ? descripcionObra : null,
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
        setRubroComercial('')
        setDireccionLocal('')
        setDireccionObra('')
        setDescripcionObra('')
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

  /**
   * Copia el token al portapapeles como comodidad extra. El código ya
   * queda visible y seleccionable a mano en el campo de solo lectura de
   * abajo, así que si el navegador no permite usar el portapapeles (sin
   * HTTPS, permiso denegado) no hay nada más que hacer: no es un error que
   * el vecino necesite ver.
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

  if (vista === 'consulta') {
    return <ConsultaDeSeguimiento modulo={modulo} onVolver={() => setVista('formulario')} />
  }

  const idDelError = 'error-de-alta-tramite'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Mesa de Entradas'}
      </h1>
      <p className="contenido__bajada">
        Iniciá acá tu trámite de certificado de domicilio, habilitación
        comercial simple o permiso de obra menor. No hace falta que tengas
        cuenta ni que inicies sesión.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
        <button type="button" className="boton boton--secundario" onClick={() => setVista('consulta')}>
          ¿Ya iniciaste un trámite? Consultá su estado
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void enviarTramite(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        {confirmacion && (
          <div role="status" tabIndex={-1} ref={confirmacionRef}>
            <p>
              Tu trámite quedó registrado con el número {confirmacion.id}.
              Vas a ver el estado «Iniciado» hasta que Mesa de Entradas lo
              empiece a revisar.
            </p>
            <p>
              <strong>Guardá este código de seguimiento: es la única forma
              de volver a consultar el estado de tu trámite más
              adelante.</strong> No lo vamos a reenviar por ningún otro
              medio ni lo vas a poder recuperar si lo perdés.
            </p>
            <div className="campo">
              <label htmlFor="tramite-token-generado">Código de seguimiento</label>
              <div className="formulario__acciones formulario__acciones--compacto">
                <input
                  id="tramite-token-generado"
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
          <label htmlFor="tramite-tipo">Tipo de trámite</label>
          <select
            id="tramite-tipo"
            required
            value={tipo}
            onChange={(evento) => cambiarTipo(evento.target.value as TipoDeTramite)}
          >
            {(Object.keys(ETIQUETA_TIPO) as TipoDeTramite[]).map((opcion) => (
              <option key={opcion} value={opcion}>
                {ETIQUETA_TIPO[opcion]}
              </option>
            ))}
          </select>
        </div>

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

        {tipo === 'CERTIFICADO_DOMICILIO' && (
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
        )}

        {tipo === 'HABILITACION_COMERCIAL_SIMPLE' && (
          <>
            <div className="campo">
              <label htmlFor="tramite-rubro">Rubro del comercio</label>
              <input
                id="tramite-rubro"
                required
                value={rubroComercial}
                onChange={(evento) => setRubroComercial(evento.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? `${idDelError} tramite-rubro-ayuda` : 'tramite-rubro-ayuda'}
              />
              <p className="campo__ayuda" id="tramite-rubro-ayuda">
                Por ejemplo, kiosco, restaurante, peluquería.
              </p>
            </div>

            <div className="campo">
              <label htmlFor="tramite-direccion-local">Dirección del local a habilitar</label>
              <input
                id="tramite-direccion-local"
                required
                value={direccionLocal}
                onChange={(evento) => setDireccionLocal(evento.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? idDelError : undefined}
              />
            </div>
          </>
        )}

        {tipo === 'PERMISO_OBRA_MENOR' && (
          <>
            <div className="campo">
              <label htmlFor="tramite-direccion-obra">Dirección de la obra</label>
              <input
                id="tramite-direccion-obra"
                required
                value={direccionObra}
                onChange={(evento) => setDireccionObra(evento.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? idDelError : undefined}
              />
            </div>

            <div className="campo">
              <label htmlFor="tramite-descripcion-obra">Descripción de la obra</label>
              <textarea
                id="tramite-descripcion-obra"
                required
                value={descripcionObra}
                onChange={(evento) => setDescripcionObra(evento.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={
                  error ? `${idDelError} tramite-descripcion-obra-ayuda` : 'tramite-descripcion-obra-ayuda'
                }
              />
              <p className="campo__ayuda" id="tramite-descripcion-obra-ayuda">
                Qué se va a hacer: por ejemplo, arreglo de vereda, cerco,
                revoque de fachada.
              </p>
            </div>
          </>
        )}

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Enviando…' : 'Iniciar trámite'}
          </button>
        </div>
      </form>
    </main>
  )
}

// --- Consulta pública por token de seguimiento (ADR 0017) ---

type PropsConsulta = {
  modulo?: Modulo
  onVolver: () => void
}

/**
 * Sub-vista de `FormularioDeAlta`: un vecino sin sesión, con el token que
 * recibió al iniciar un trámite, consulta en qué quedó — de solo lectura,
 * sin ninguna acción posible (ADR 0017 §5/§6). El historial de movimientos
 * viene sin actor (`actorNombre`/`actorEmail`): quién de la planta
 * municipal lo atendió es un dato interno, no algo que el vecino necesite.
 */
function ConsultaDeSeguimiento({ modulo, onVolver }: PropsConsulta) {
  const [codigo, setCodigo] = useState('')
  const [consultando, setConsultando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<SeguimientoDeExpediente | null>(null)

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const resultadoRef = useRef<HTMLDivElement>(null)

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
      const respuesta = await pedir<SeguimientoDeExpediente>(
        `/api/mesaentradas/seguimiento/${encodeURIComponent(codigo.trim())}`,
        'No pudimos encontrar un trámite con ese código.',
      )
      if (!vigente.current) {
        return
      }
      setResultado(respuesta)
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setError(fallo instanceof Error ? fallo.message : 'No pudimos encontrar un trámite con ese código.')
    } finally {
      if (vigente.current) {
        setConsultando(false)
      }
    }
  }

  const idDelError = 'error-de-consulta-tramite'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Consultar el estado de un trámite
      </h1>
      <p className="contenido__bajada">
        Ingresá el código de seguimiento que recibiste al iniciar tu
        trámite en {modulo?.nombre ?? 'Mesa de Entradas'}, sin espacios ni
        caracteres de más.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al formulario de alta
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void consultar(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        <div className="campo">
          <label htmlFor="tramite-codigo-seguimiento">Código de seguimiento</label>
          <input
            id="tramite-codigo-seguimiento"
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
        <div role="status" tabIndex={-1} ref={resultadoRef}>
          <dl className="ficha">
            <div className="ficha__fila">
              <dt>Tipo de trámite</dt>
              <dd>{ETIQUETA_TIPO[resultado.tipo]}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Detalle</dt>
              <dd>{textoDetalle(resultado)}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Estado</dt>
              <dd>{ETIQUETA_ESTADO[resultado.estado]}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Creado</dt>
              <dd>{FECHA.format(new Date(resultado.creadoEn))}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Última actualización</dt>
              <dd>{FECHA.format(new Date(resultado.actualizadoEn))}</dd>
            </div>
          </dl>

          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Historial de movimientos de tu trámite, sin el nombre de
                quién lo gestionó en el municipio.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Estado anterior</th>
                  <th scope="col">Estado nuevo</th>
                  <th scope="col">Comentario</th>
                  <th scope="col">Fecha</th>
                </tr>
              </thead>
              <tbody>
                {resultado.movimientos.map((movimiento, indice) => (
                  <tr key={indice}>
                    <th scope="row">
                      {movimiento.estadoAnterior ? ETIQUETA_ESTADO[movimiento.estadoAnterior] : 'Alta pública'}
                    </th>
                    <td>{ETIQUETA_ESTADO[movimiento.estadoNuevo]}</td>
                    <td>{movimiento.comentario ?? 'Sin comentario'}</td>
                    <td>{FECHA.format(new Date(movimiento.fecha))}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}
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
    const opciones = TRANSICIONES_VALIDAS[expediente.tipo][expediente.estado]
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
                <th scope="col">Tipo</th>
                <th scope="col">Detalle del trámite</th>
                <th scope="col">Estado</th>
                <th scope="col">Historial</th>
                <th scope="col">Creado</th>
                {puedeGestionar && <th scope="col">Acción</th>}
              </tr>
            </thead>
            <tbody>
              {estado.expedientes.map((expediente) => {
                const enEdicion = edicion && edicion.id === expediente.id ? edicion : null
                const opcionesValidas = TRANSICIONES_VALIDAS[expediente.tipo][expediente.estado]

                return (
                  <tr key={expediente.id}>
                    <th scope="row">{expediente.solicitanteNombre}</th>
                    <td>{textoContacto(expediente)}</td>
                    <td>{ETIQUETA_TIPO[expediente.tipo]}</td>
                    <td>{textoDetalle(expediente)}</td>
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
