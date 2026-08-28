import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, pedir } from './api'
import { useModulos, type Modulo } from '../modulos/useModulos'

/**
 * Textos de tramo poblacional y estado de facturación.
 *
 * Duplicados a propósito de `frontend/src/plataforma/tipos.ts`: son dos
 * objetos chicos, y `plataforma/` es la consola del proveedor, con otro
 * ciclo de vida — este directorio no la importa.
 */
const TEXTO_TRAMO_POBLACIONAL: Record<string, string> = {
  CHICO: 'Chico',
  MEDIANO: 'Mediano',
  GRANDE: 'Grande',
}

const TEXTO_ESTADO_DE_FACTURACION: Record<string, string> = {
  AL_DIA: 'Al día',
  ATRASADO: 'Atrasado',
}

const TEXTO_TIPO_DE_SOLICITUD: Record<string, string> = {
  ALTA: 'Alta',
  BAJA: 'Baja',
}

const TEXTO_ESTADO_DE_SOLICITUD: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  ATENDIDA: 'Atendida',
}

type Contrato = {
  tramoPoblacional: string
  estadoFacturacion: string
}

type SolicitudDeModulo = {
  id: number
  moduloCodigo: string
  tipo: string
  justificacion: string
  estado: string
  creadaEn: string
  atendidaEn: string | null
}

type EstadoContrato =
  | { estado: 'cargando' }
  | { estado: 'listo'; contrato: Contrato }
  | { estado: 'error'; mensaje: string }

type EstadoSolicitudes =
  | { estado: 'cargando' }
  | { estado: 'listo'; solicitudes: SolicitudDeModulo[] }
  | { estado: 'error'; mensaje: string }

const FECHA = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

type Props = {
  /** Si además puede pedir el alta o la baja de un módulo, no solo ver. */
  puedeSolicitar: boolean
}

/**
 * "Mi municipio": módulos contratados, contrato (tramo poblacional y
 * estado de facturación, sin la nota interna de la plataforma) y el
 * historial de solicitudes de alta/baja de módulo, con el formulario para
 * crear una nueva (ADR 0022).
 *
 * Se muestra a quien tiene `municipio.verContrato`, pero eso es comodidad:
 * el backend lo verifica igual en cada ruta (ADR 0011).
 */
export function PanelDeMiMunicipio({ puedeSolicitar }: Props) {
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  // --- Módulos contratados (solo lectura) ---

  const modulos = useModulos()
  const modulosContratados: Modulo[] =
    modulos.estado === 'listo' ? modulos.modulos.filter((modulo) => modulo.habilitado) : []

  // --- Mi contrato (solo lectura) ---

  const [contrato, setContrato] = useState<EstadoContrato>({ estado: 'cargando' })

  useEffect(() => {
    let vigenteEfecto = true

    // El backend devuelve 204 solo si el tenant no existiera en la base de
    // control, algo que no puede pasar con una sesión ya autenticada contra
    // ese mismo tenant (mismo motivo por el que `ContratoDelTenant.actual()`
    // resuelve siempre en este caso, a diferencia de los datos de contacto).
    pedir<Contrato>('/api/municipio/contrato', 'No se pudo cargar el contrato.')
      .then((respuesta) => {
        if (vigenteEfecto) {
          setContrato({ estado: 'listo', contrato: respuesta })
        }
      })
      .catch((fallo: unknown) => {
        if (vigenteEfecto) {
          setContrato({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigenteEfecto = false
    }
  }, [])

  // --- Historial de solicitudes ---

  const [solicitudes, setSolicitudes] = useState<EstadoSolicitudes>({ estado: 'cargando' })

  const cargarSolicitudes = useCallback(async () => {
    try {
      const respuesta = await pedir<SolicitudDeModulo[]>(
        '/api/municipio/solicitudes-de-modulo',
        'No se pudo cargar el historial de solicitudes.',
      )
      if (vigente.current) {
        setSolicitudes({ estado: 'listo', solicitudes: respuesta })
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setSolicitudes({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    // Carga inicial de datos remotos (mismo patrón que PanelDeUsuarios/
    // PanelDeAuditoria): el setState está protegido por `vigente`, no
    // dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarSolicitudes()
  }, [cargarSolicitudes])

  // --- Formulario de solicitud ---

  const [moduloCodigo, setModuloCodigo] = useState('')
  const [tipo, setTipo] = useState<'ALTA' | 'BAJA'>('ALTA')
  const [justificacion, setJustificacion] = useState('')
  const [enviandoSolicitud, setEnviandoSolicitud] = useState(false)
  const [errorSolicitud, setErrorSolicitud] = useState<string | null>(null)
  const [exitoSolicitud, setExitoSolicitud] = useState<string | null>(null)

  const errorSolicitudRef = useRef<HTMLParagraphElement>(null)
  const exitoSolicitudRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (errorSolicitud) {
      errorSolicitudRef.current?.focus()
    }
  }, [errorSolicitud])

  useEffect(() => {
    if (exitoSolicitud) {
      exitoSolicitudRef.current?.focus()
    }
  }, [exitoSolicitud])

  // El selector de módulo se completa con el primer código disponible en
  // cuanto carga el catálogo, para no dejarlo vacío innecesariamente. Se
  // deriva en el render en vez de sincronizarlo con un efecto: es un valor
  // calculable a partir del estado que ya existe, no una sincronización con
  // algo externo.
  const moduloCodigoSeleccionado =
    moduloCodigo !== ''
      ? moduloCodigo
      : modulos.estado === 'listo' && modulos.modulos.length > 0
        ? modulos.modulos[0].codigo
        : ''

  async function solicitarModulo(evento: FormEvent) {
    evento.preventDefault()
    setErrorSolicitud(null)
    setExitoSolicitud(null)
    setEnviandoSolicitud(true)
    try {
      await enviar(
        '/api/municipio/solicitudes-de-modulo',
        'POST',
        { moduloCodigo: moduloCodigoSeleccionado, tipo, justificacion },
        'No se pudo enviar la solicitud.',
      )
      await cargarSolicitudes()
      if (vigente.current) {
        setJustificacion('')
        setExitoSolicitud('Se envió la solicitud. La vas a ver en el historial como "Pendiente".')
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorSolicitud(fallo instanceof Error ? fallo.message : 'No se pudo enviar la solicitud.')
      }
    } finally {
      if (vigente.current) {
        setEnviandoSolicitud(false)
      }
    }
  }

  return (
    <>
      <section aria-labelledby="titulo-modulos-contratados">
        <h2 id="titulo-modulos-contratados">Módulos contratados</h2>

        {modulos.estado === 'cargando' && <p role="status">Cargando los módulos contratados…</p>}
        {modulos.estado === 'error' && <p role="alert">{modulos.mensaje}</p>}

        {modulos.estado === 'listo' && (
          <>
            {modulosContratados.length === 0 ? (
              <p>Este municipio todavía no tiene ningún módulo contratado.</p>
            ) : (
              <ul>
                {modulosContratados.map((modulo) => (
                  <li key={modulo.codigo}>
                    <strong>{modulo.nombre}</strong>
                    {modulo.descripcion && <> — {modulo.descripcion}</>}
                  </li>
                ))}
              </ul>
            )}
          </>
        )}
      </section>

      <section aria-labelledby="titulo-mi-contrato">
        <h2 id="titulo-mi-contrato">Mi contrato</h2>

        {contrato.estado === 'cargando' && <p role="status">Cargando el contrato…</p>}
        {contrato.estado === 'error' && <p role="alert">{contrato.mensaje}</p>}

        {contrato.estado === 'listo' && (
          <dl>
            <dt>Tramo poblacional</dt>
            <dd>
              {TEXTO_TRAMO_POBLACIONAL[contrato.contrato.tramoPoblacional] ??
                contrato.contrato.tramoPoblacional}
            </dd>
            <dt>Estado de facturación</dt>
            <dd>
              {TEXTO_ESTADO_DE_FACTURACION[contrato.contrato.estadoFacturacion] ??
                contrato.contrato.estadoFacturacion}
            </dd>
          </dl>
        )}
      </section>

      {puedeSolicitar && (
        <section aria-labelledby="titulo-solicitar-modulo">
          <h2 id="titulo-solicitar-modulo">Solicitar alta o baja de un módulo</h2>

          <form className="formulario" onSubmit={(evento) => void solicitarModulo(evento)}>
            {errorSolicitud && (
              <p className="formulario__error" role="alert" tabIndex={-1} ref={errorSolicitudRef}>
                {errorSolicitud}
              </p>
            )}
            {exitoSolicitud && (
              <p role="status" tabIndex={-1} ref={exitoSolicitudRef}>
                {exitoSolicitud}
              </p>
            )}

            {modulos.estado === 'cargando' && (
              <p role="status">Cargando el catálogo de módulos…</p>
            )}
            {modulos.estado === 'error' && <p role="alert">{modulos.mensaje}</p>}

            {modulos.estado === 'listo' && (
              <div className="campo">
                <label htmlFor="solicitud-modulo">Módulo</label>
                <select
                  id="solicitud-modulo"
                  required
                  value={moduloCodigoSeleccionado}
                  onChange={(evento) => setModuloCodigo(evento.target.value)}
                >
                  {modulos.modulos.map((modulo) => (
                    <option key={modulo.codigo} value={modulo.codigo}>
                      {modulo.codigo} — {modulo.nombre}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <fieldset className="grupo-checkboxes">
              <legend>Tipo de solicitud</legend>
              <label className="grupo-checkboxes__opcion">
                <input
                  type="radio"
                  name="tipo-solicitud"
                  checked={tipo === 'ALTA'}
                  onChange={() => setTipo('ALTA')}
                />
                Alta
              </label>
              <label className="grupo-checkboxes__opcion">
                <input
                  type="radio"
                  name="tipo-solicitud"
                  checked={tipo === 'BAJA'}
                  onChange={() => setTipo('BAJA')}
                />
                Baja
              </label>
            </fieldset>

            <div className="campo">
              <label htmlFor="solicitud-justificacion">Justificación</label>
              <textarea
                id="solicitud-justificacion"
                required
                aria-describedby="solicitud-justificacion-ayuda"
                value={justificacion}
                onChange={(evento) => setJustificacion(evento.target.value)}
              />
              <p className="campo__ayuda" id="solicitud-justificacion-ayuda">
                Contale a la plataforma por qué pedís este cambio.
              </p>
            </div>

            <div className="formulario__acciones">
              <button
                type="submit"
                className="boton"
                disabled={enviandoSolicitud}
                aria-busy={enviandoSolicitud}
              >
                {enviandoSolicitud ? 'Enviando…' : 'Enviar solicitud'}
              </button>
            </div>
          </form>
        </section>
      )}

      <section aria-labelledby="titulo-historial-solicitudes">
        <h2 id="titulo-historial-solicitudes">Historial de solicitudes</h2>

        {solicitudes.estado === 'cargando' && <p role="status">Cargando el historial de solicitudes…</p>}
        {solicitudes.estado === 'error' && <p role="alert">{solicitudes.mensaje}</p>}

        {solicitudes.estado === 'listo' &&
          (solicitudes.solicitudes.length === 0 ? (
            <p>Todavía no se hizo ninguna solicitud de alta o baja de módulo.</p>
          ) : (
            <div className="tabla-contenedor">
              <table className="tabla">
                <caption>Solicitudes de alta o baja de módulo hechas por este municipio.</caption>
                <thead>
                  <tr>
                    <th scope="col">Módulo</th>
                    <th scope="col">Tipo</th>
                    <th scope="col">Justificación</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Fecha</th>
                  </tr>
                </thead>
                <tbody>
                  {solicitudes.solicitudes.map((solicitud) => (
                    <tr key={solicitud.id}>
                      <th scope="row">{solicitud.moduloCodigo}</th>
                      <td>{TEXTO_TIPO_DE_SOLICITUD[solicitud.tipo] ?? solicitud.tipo}</td>
                      <td>{solicitud.justificacion}</td>
                      <td>{TEXTO_ESTADO_DE_SOLICITUD[solicitud.estado] ?? solicitud.estado}</td>
                      <td>{FECHA.format(new Date(solicitud.creadaEn))}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </section>
    </>
  )
}
