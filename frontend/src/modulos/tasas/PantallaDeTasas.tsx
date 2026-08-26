import { useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type EstadoDeTasa = 'PENDIENTE' | 'PAGADA'

type Tasa = {
  id: number
  numeroCuenta: string
  concepto: string
  periodo: string
  monto: number
  estado: EstadoDeTasa
  fechaPago: string | null
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
}

type IniciarPagoResponse = {
  referenciaExterna: string
  urlDePago: string | null
}

const ETIQUETA_ESTADO: Record<EstadoDeTasa, string> = {
  PENDIENTE: 'Pendiente',
  PAGADA: 'Pagada',
}

const MONEDA = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })
const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en `PantallaDeBoletin`/`PantallaDeCementerio`/`PantallaDeTransparencia` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

type Vista = 'busqueda' | 'resultados' | 'pago'

type EstadoBusqueda =
  | { estado: 'cargando' }
  | { estado: 'listo'; tasas: Tasa[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoSimulador =
  | { fase: 'iniciando' }
  | { fase: 'listo'; referenciaExterna: string }
  | { fase: 'confirmando'; referenciaExterna: string; aprobado: boolean }
  | { fase: 'error'; mensaje: string }

type ResultadoDePago = { aprobado: boolean; tasa: Tasa }

type EstadoPublicacion = {
  numeroCuenta: string
  concepto: string
  periodo: string
  monto: string
  enviando: boolean
  error: string | null
}

const PUBLICACION_INICIAL: EstadoPublicacion = {
  numeroCuenta: '',
  concepto: '',
  periodo: '',
  monto: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `tasas`: búsqueda pública de tasas por número de
 * cuenta, pago simulado in-app (ADR 0018 §3: sin redirección real, la
 * pantalla se rotula explícitamente como entorno de prueba) y, visible
 * solo con `tasas.publicar`, el alta de una tasa nueva (ADR 0011: se
 * esconde por comodidad, el backend vuelve a exigir el permiso). Sin
 * router de URLs en este frontend (ADR 0008): cada paso es un estado
 * local más, mismo patrón que la sub-vista de seguimiento de
 * `PantallaDeReclamos`.
 */
export function PantallaDeTasas({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedePublicar = usuario?.permisos.includes('tasas.publicar') ?? false

  // Mismo patrón que el resto de las pantallas: evita pisar estado de un
  // componente que ya no está montado cuando un pedido en vuelo termina
  // después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const [vista, setVista] = useState<Vista>('busqueda')

  // --- Búsqueda pública por número de cuenta ---

  const [numeroCuenta, setNumeroCuenta] = useState('')
  const [numeroCuentaBuscado, setNumeroCuentaBuscado] = useState('')
  const [busqueda, setBusqueda] = useState<EstadoBusqueda>({ estado: 'listo', tasas: [] })
  const [resultadoPago, setResultadoPago] = useState<ResultadoDePago | null>(null)

  const tituloBusqueda = useRef<HTMLHeadingElement>(null)
  const tituloResultados = useRef<HTMLHeadingElement>(null)
  const tituloPago = useRef<HTMLHeadingElement>(null)
  const resultadoPagoRef = useRef<HTMLParagraphElement>(null)
  const errorPagoRef = useRef<HTMLParagraphElement>(null)

  // --- Simulador de pago (ADR 0018 §3): estado declarado acá arriba
  // porque el efecto de foco de más abajo lo necesita.
  const [tasaEnPago, setTasaEnPago] = useState<Tasa | null>(null)
  const [simulador, setSimulador] = useState<EstadoSimulador>({ fase: 'iniciando' })

  // Foco al cambiar de vista: en 'resultados', si se viene de confirmar un
  // pago el foco va al resultado (con su propio anuncio), no al título.
  useEffect(() => {
    if (vista === 'busqueda') {
      tituloBusqueda.current?.focus()
    } else if (vista === 'resultados') {
      if (resultadoPago) {
        resultadoPagoRef.current?.focus()
      } else {
        tituloResultados.current?.focus()
      }
    } else if (vista === 'pago') {
      tituloPago.current?.focus()
    }
  }, [vista, resultadoPago])

  useEffect(() => {
    if (simulador.fase === 'error') {
      errorPagoRef.current?.focus()
    }
  }, [simulador])

  async function buscar(evento: FormEvent) {
    evento.preventDefault()
    const cuenta = numeroCuenta.trim()
    if (cuenta === '') {
      return
    }
    setNumeroCuentaBuscado(cuenta)
    setResultadoPago(null)
    setVista('resultados')
    setBusqueda({ estado: 'cargando' })
    try {
      const tasas = await pedir<Tasa[]>(
        `/api/tasas?numeroCuenta=${encodeURIComponent(cuenta)}`,
        'No se pudieron buscar las tasas de esa cuenta.',
      )
      if (vigente.current) {
        setBusqueda({ estado: 'listo', tasas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setBusqueda({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'tasas' })
      } else {
        setBusqueda({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }

  function volverABuscar() {
    setVista('busqueda')
    setResultadoPago(null)
  }

  async function iniciarPago(tasa: Tasa) {
    setTasaEnPago(tasa)
    setResultadoPago(null)
    setVista('pago')
    setSimulador({ fase: 'iniciando' })
    try {
      const resultado = await enviar<IniciarPagoResponse>(
        `/api/tasas/${tasa.id}/pagos`,
        'POST',
        undefined,
        'No se pudo iniciar el pago.',
      )
      if (!vigente.current) {
        return
      }
      if (resultado) {
        setSimulador({ fase: 'listo', referenciaExterna: resultado.referenciaExterna })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setSimulador({
          fase: 'error',
          mensaje: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'tasas'),
        })
      } else {
        setSimulador({
          fase: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'No se pudo iniciar el pago.',
        })
      }
    }
  }

  async function confirmarPago(aprobado: boolean) {
    if (simulador.fase !== 'listo') {
      return
    }
    const referenciaExterna = simulador.referenciaExterna
    setSimulador({ fase: 'confirmando', referenciaExterna, aprobado })
    try {
      const tasaActualizada = await enviar<Tasa>(
        '/api/tasas/pagos/confirmar',
        'POST',
        { referenciaExterna, aprobado },
        'No se pudo confirmar el pago.',
      )
      if (!vigente.current) {
        return
      }
      if (tasaActualizada) {
        setBusqueda((actual) =>
          actual.estado === 'listo'
            ? {
                estado: 'listo',
                tasas: actual.tasas.map((t) => (t.id === tasaActualizada.id ? tasaActualizada : t)),
              }
            : actual,
        )
        setTasaEnPago(null)
        setResultadoPago({ aprobado, tasa: tasaActualizada })
        setVista('resultados')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setSimulador({
          fase: 'error',
          mensaje: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'tasas'),
        })
      } else {
        setSimulador({
          fase: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'No se pudo confirmar el pago.',
        })
      }
    }
  }

  function volverSinPagar() {
    setTasaEnPago(null)
    setVista('resultados')
  }

  // --- Publicar tasa (solo con tasas.publicar, ADR 0011) ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [publicacion, setPublicacion] = useState<EstadoPublicacion>(PUBLICACION_INICIAL)
  const [tasaPublicada, setTasaPublicada] = useState<{ numeroCuenta: string; concepto: string } | null>(null)

  const botonPublicar = useRef<HTMLButtonElement>(null)
  const primerCampoPublicacion = useRef<HTMLInputElement>(null)
  const errorPublicacionRef = useRef<HTMLParagraphElement>(null)
  const confirmacionPublicacionRef = useRef<HTMLParagraphElement>(null)

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

  useEffect(() => {
    if (tasaPublicada) {
      confirmacionPublicacionRef.current?.focus()
    }
  }, [tasaPublicada])

  function abrirFormularioPublicacion() {
    setPublicacion(PUBLICACION_INICIAL)
    setTasaPublicada(null)
    setFormularioAbierto(true)
  }

  function cerrarFormularioPublicacion() {
    setFormularioAbierto(false)
    botonPublicar.current?.focus()
  }

  const idDelErrorPublicacion = 'error-de-publicacion-tasa'

  function montoIngresadoValido(valor: string): boolean {
    const numero = Number(valor)
    return valor.trim() !== '' && Number.isFinite(numero) && numero > 0
  }

  async function publicarTasa(evento: FormEvent) {
    evento.preventDefault()
    if (!montoIngresadoValido(publicacion.monto)) {
      setPublicacion((actual) => ({
        ...actual,
        error: 'Ingresá un monto numérico mayor a cero.',
      }))
      return
    }
    setPublicacion((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/tasas',
        'POST',
        {
          numeroCuenta: publicacion.numeroCuenta,
          concepto: publicacion.concepto,
          periodo: publicacion.periodo,
          monto: Number(publicacion.monto),
        },
        'No se pudo publicar la tasa.',
      )
      if (!vigente.current) {
        return
      }
      setTasaPublicada({ numeroCuenta: publicacion.numeroCuenta, concepto: publicacion.concepto })
      setFormularioAbierto(false)
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setPublicacion((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'tasas'),
        }))
      } else {
        setPublicacion((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la tasa.',
        }))
      }
    }
  }

  // --- Vista: simulador de pago ---

  if (vista === 'pago' && tasaEnPago) {
    return (
      <main id="contenido" className="contenido">
        <h1 ref={tituloPago} tabIndex={-1}>
          Simulador de pago (entorno de prueba)
        </h1>
        <p className="contenido__bajada">
          Esto es una simulación para desarrollo y demostración: en
          producción, acá se abriría el sitio de una pasarela de pago
          real. No se procesa ningún dato de tarjeta ni de cuenta
          bancaria en esta pantalla.
        </p>

        <dl className="ficha">
          <div className="ficha__fila">
            <dt>Concepto</dt>
            <dd>{tasaEnPago.concepto}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Período</dt>
            <dd>{tasaEnPago.periodo}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Monto a pagar</dt>
            <dd>{MONEDA.format(tasaEnPago.monto)}</dd>
          </div>
        </dl>

        {simulador.fase === 'iniciando' && <p role="status">Iniciando el pago…</p>}

        {simulador.fase === 'error' && (
          <p className="formulario__error" role="alert" tabIndex={-1} ref={errorPagoRef}>
            {simulador.mensaje}
          </p>
        )}

        {(simulador.fase === 'listo' || simulador.fase === 'confirmando') && (
          <div className="formulario__acciones">
            <button
              type="button"
              className="boton"
              disabled={simulador.fase === 'confirmando'}
              aria-busy={simulador.fase === 'confirmando' && simulador.aprobado}
              onClick={() => void confirmarPago(true)}
            >
              {simulador.fase === 'confirmando' && simulador.aprobado ? 'Procesando…' : 'Aprobar pago'}
            </button>
            <button
              type="button"
              className="boton boton--secundario"
              disabled={simulador.fase === 'confirmando'}
              aria-busy={simulador.fase === 'confirmando' && !simulador.aprobado}
              onClick={() => void confirmarPago(false)}
            >
              {simulador.fase === 'confirmando' && !simulador.aprobado ? 'Procesando…' : 'Rechazar pago'}
            </button>
          </div>
        )}

        <div className="formulario__acciones">
          <button
            type="button"
            className="boton boton--secundario"
            disabled={simulador.fase === 'confirmando'}
            onClick={volverSinPagar}
          >
            Volver sin pagar
          </button>
        </div>
      </main>
    )
  }

  // --- Vista: resultados de la búsqueda ---

  if (vista === 'resultados') {
    return (
      <main id="contenido" className="contenido">
        <h1 ref={tituloResultados} tabIndex={-1}>
          Tasas de la cuenta {numeroCuentaBuscado}
        </h1>

        <div className="formulario__acciones">
          <button type="button" className="boton boton--secundario" onClick={volverABuscar}>
            Buscar otro número de cuenta
          </button>
          <button type="button" className="boton boton--secundario" onClick={onVolver}>
            Volver al portal
          </button>
        </div>

        {resultadoPago &&
          (resultadoPago.aprobado ? (
            <p role="status" tabIndex={-1} ref={resultadoPagoRef}>
              Pago aprobado: la tasa «{resultadoPago.tasa.concepto}» quedó
              registrada como pagada
              {resultadoPago.tasa.fechaPago
                ? ` el ${FECHA.format(new Date(resultadoPago.tasa.fechaPago))}`
                : ''}
              .
            </p>
          ) : (
            <p tabIndex={-1} ref={resultadoPagoRef}>
              El pago de la tasa «{resultadoPago.tasa.concepto}» se
              rechazó. La tasa sigue pendiente: se puede intentar pagarla
              de nuevo cuando se quiera.
            </p>
          ))}

        {busqueda.estado === 'cargando' && <p role="status">Buscando tasas…</p>}

        {busqueda.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? busqueda.moduloDelError)}
          </p>
        )}

        {busqueda.estado === 'error' && <p role="alert">{busqueda.mensaje}</p>}

        {busqueda.estado === 'listo' && busqueda.tasas.length === 0 && (
          <p role="status">No encontramos tasas para ese número de cuenta.</p>
        )}

        {busqueda.estado === 'listo' && busqueda.tasas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>Tasas municipales de la cuenta {numeroCuentaBuscado}.</caption>
              <thead>
                <tr>
                  <th scope="col">Concepto</th>
                  <th scope="col">Período</th>
                  <th scope="col">Monto</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha de pago</th>
                  <th scope="col">Acción</th>
                </tr>
              </thead>
              <tbody>
                {busqueda.tasas.map((tasa) => (
                  <tr key={tasa.id}>
                    <th scope="row">{tasa.concepto}</th>
                    <td>{tasa.periodo}</td>
                    <td>{MONEDA.format(tasa.monto)}</td>
                    <td>{ETIQUETA_ESTADO[tasa.estado]}</td>
                    <td>{tasa.fechaPago ? FECHA.format(new Date(tasa.fechaPago)) : '—'}</td>
                    <td>
                      {tasa.estado === 'PENDIENTE' ? (
                        <button type="button" className="boton" onClick={() => void iniciarPago(tasa)}>
                          Pagar
                        </button>
                      ) : (
                        'Ya está pagada'
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </main>
    )
  }

  // --- Vista: búsqueda (default) + publicar tasa (con permiso) ---

  return (
    <main id="contenido" className="contenido">
      <h1 ref={tituloBusqueda} tabIndex={-1}>
        {modulo?.nombre ?? 'Tasas municipales'}
      </h1>
      <p className="contenido__bajada">
        Consultá las tasas municipales de tu cuenta y pagalas online. No
        hace falta tener cuenta ni iniciar sesión para buscar ni para
        pagar.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-buscar-tasas">
        <h2 id="titulo-buscar-tasas">Buscar tasas por número de cuenta</h2>

        <form className="formulario" onSubmit={(evento) => void buscar(evento)}>
          <div className="campo">
            <label htmlFor="tasas-numero-cuenta">Número de cuenta</label>
            <input
              id="tasas-numero-cuenta"
              required
              value={numeroCuenta}
              onChange={(evento) => setNumeroCuenta(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>
      </section>

      {puedePublicar && (
        <section aria-labelledby="titulo-publicar-tasa">
          <h2 id="titulo-publicar-tasa">Publicar una tasa</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button
                type="button"
                className="boton"
                ref={botonPublicar}
                onClick={abrirFormularioPublicacion}
              >
                Publicar tasa
              </button>
              {tasaPublicada && (
                <p role="status" tabIndex={-1} ref={confirmacionPublicacionRef}>
                  Se publicó la tasa «{tasaPublicada.concepto}» para la
                  cuenta {tasaPublicada.numeroCuenta}.
                </p>
              )}
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarTasa(evento)}>
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
                <label htmlFor="tasas-publicar-numero-cuenta">Número de cuenta</label>
                <input
                  id="tasas-publicar-numero-cuenta"
                  ref={primerCampoPublicacion}
                  required
                  value={publicacion.numeroCuenta}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, numeroCuenta: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="tasas-publicar-concepto">Concepto</label>
                <input
                  id="tasas-publicar-concepto"
                  required
                  value={publicacion.concepto}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, concepto: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="tasas-publicar-periodo">Período</label>
                <input
                  id="tasas-publicar-periodo"
                  required
                  placeholder="Ej: 2026-08, 3er trimestre 2026"
                  value={publicacion.periodo}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, periodo: evento.target.value }))
                  }
                  aria-invalid={publicacion.error ? true : undefined}
                  aria-describedby={publicacion.error ? idDelErrorPublicacion : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="tasas-publicar-monto">Monto</label>
                <input
                  id="tasas-publicar-monto"
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={publicacion.monto}
                  onChange={(evento) =>
                    setPublicacion((actual) => ({ ...actual, monto: evento.target.value }))
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
                  onClick={cerrarFormularioPublicacion}
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
