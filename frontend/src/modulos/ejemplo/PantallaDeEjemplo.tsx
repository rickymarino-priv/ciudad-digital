import { useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { Modulo } from '../useModulos'

type RespuestaPing = {
  modulo: string
  municipio: string
  momento: string
}

type RespuestaEco = {
  mensaje: string
  municipio: string
  usuario: string
}

type EstadoPing =
  | { estado: 'cargando' }
  | { estado: 'listo'; respuesta: RespuestaPing }
  | { estado: 'no-contratado'; mensaje: string }
  | { estado: 'error'; mensaje: string }

type Props = {
  /** Entrada del catálogo que corresponde a este módulo, si ya cargó. */
  modulo?: Modulo
  onVolver: () => void
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'medium' })

/** Mismo texto para el aviso de "no contratado" en el ping y en el eco. */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Pantalla del módulo `ejemplo`: un ping y un eco (ADR 0012 §10).
 *
 * Es deliberadamente mínima: existe para demostrar el mecanismo de
 * contratación de módulos, no como funcionalidad de producto. Se puede
 * llegar acá aunque el municipio no tenga el módulo contratado —desde el
 * botón "Abrir de todos modos" del catálogo—: esa es justamente la
 * demostración de que ocultar en el frontend no es enforcement (ADR 0009):
 * la pantalla se abre igual, y el backend rechaza el pedido.
 */
export function PantallaDeEjemplo({ modulo, onVolver }: Props) {
  const [ping, setPing] = useState<EstadoPing>({ estado: 'cargando' })
  const [mensaje, setMensaje] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [respuestaEco, setRespuestaEco] = useState<RespuestaEco | null>(null)
  const [errorEco, setErrorEco] = useState<string | null>(null)

  const titulo = useRef<HTMLHeadingElement>(null)
  const avisoNoContratadoRef = useRef<HTMLParagraphElement>(null)
  const errorEcoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  useEffect(() => {
    let vigente = true
    setPing({ estado: 'cargando' })

    pedir<RespuestaPing>('/api/ejemplo/ping', 'No se pudo consultar el módulo de ejemplo.')
      .then((respuesta) => {
        if (vigente) {
          setPing({ estado: 'listo', respuesta })
        }
      })
      .catch((fallo: unknown) => {
        if (!vigente) {
          return
        }
        if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
          setPing({
            estado: 'no-contratado',
            mensaje: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'ejemplo'),
          })
        } else {
          setPing({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigente = false
    }
  }, [modulo])

  useEffect(() => {
    if (ping.estado === 'no-contratado') {
      avisoNoContratadoRef.current?.focus()
    }
  }, [ping])

  useEffect(() => {
    if (errorEco) {
      errorEcoRef.current?.focus()
    }
  }, [errorEco])

  async function enviarEco(evento: FormEvent) {
    evento.preventDefault()
    setErrorEco(null)
    setRespuestaEco(null)
    setEnviando(true)
    try {
      const respuesta = await enviar<RespuestaEco>(
        '/api/ejemplo/eco',
        'POST',
        { mensaje },
        'No se pudo enviar el eco.',
      )
      if (respuesta) {
        setRespuestaEco(respuesta)
      }
    } catch (fallo: unknown) {
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setErrorEco(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'ejemplo'))
      } else {
        setErrorEco(fallo instanceof Error ? fallo.message : 'No se pudo enviar el eco.')
      }
    } finally {
      setEnviando(false)
    }
  }

  const idDelErrorEco = 'error-de-eco'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Módulo de ejemplo'}
      </h1>
      <p className="contenido__bajada">
        Pantalla de demostración del mecanismo de contratación de módulos: un
        ping público y un eco que requiere sesión y permiso.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-ping">
        <h2 id="titulo-ping">Estado del módulo</h2>

        {ping.estado === 'cargando' && <p role="status">Consultando el módulo…</p>}

        {ping.estado === 'no-contratado' && (
          <p
            className="formulario__error"
            role="alert"
            tabIndex={-1}
            ref={avisoNoContratadoRef}
          >
            {ping.mensaje}
          </p>
        )}

        {ping.estado === 'error' && <p role="alert">{ping.mensaje}</p>}

        {ping.estado === 'listo' && (
          <dl className="ficha">
            <div className="ficha__fila">
              <dt>Municipio</dt>
              <dd>{ping.respuesta.municipio}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Momento</dt>
              <dd>{FECHA.format(new Date(ping.respuesta.momento))}</dd>
            </div>
          </dl>
        )}
      </section>

      <section aria-labelledby="titulo-eco">
        <h2 id="titulo-eco">Eco</h2>
        <p className="contenido__nota">
          Requiere sesión y el permiso <code>ejemplo.usar</code>. Si no lo
          tenés, o si el municipio no tiene contratado el módulo, la API
          rechaza el pedido y acá se muestra el motivo.
        </p>

        <form className="formulario" onSubmit={(evento) => void enviarEco(evento)}>
          {errorEco && (
            <p
              className="formulario__error"
              id={idDelErrorEco}
              role="alert"
              tabIndex={-1}
              ref={errorEcoRef}
            >
              {errorEco}
            </p>
          )}

          <div className="campo">
            <label htmlFor="ejemplo-mensaje">Mensaje</label>
            <input
              id="ejemplo-mensaje"
              required
              value={mensaje}
              onChange={(evento) => setMensaje(evento.target.value)}
              aria-invalid={errorEco ? true : undefined}
              aria-describedby={errorEco ? idDelErrorEco : undefined}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
              {enviando ? 'Enviando…' : 'Enviar eco'}
            </button>
          </div>
        </form>

        {respuestaEco && (
          <dl className="ficha">
            <div className="ficha__fila">
              <dt>Mensaje</dt>
              <dd>{respuestaEco.mensaje}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Municipio</dt>
              <dd>{respuestaEco.municipio}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Usuario</dt>
              <dd>{respuestaEco.usuario}</dd>
            </div>
          </dl>
        )}
      </section>
    </main>
  )
}
