import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type EstadoDeMulta = 'NOTIFICADA' | 'EN_DESCARGO' | 'CONFIRMADA' | 'ANULADA' | 'PAGADA'

type Multa = {
  id: number
  patente: string
  dni: string | null
  descripcionInfraccion: string
  montoOriginal: number
  /** Ya con el descuento por pago temprano aplicado si corresponde (ADR 0021 §2): es el valor a mostrar y cobrar. */
  montoAPagar: number
  estado: EstadoDeMulta
  notificadaEn: string
  fechaPago: string | null
  descargoTexto: string | null
  descargoContacto: string | null
  descargoPresentadoEn: string | null
  resolucionComentario: string | null
  resueltoPorNombre: string | null
  resueltoPorEmail: string | null
  resueltoEn: string | null
  labradaPorNombre: string
  labradaPorEmail: string
}

type IniciarPagoResponse = {
  referenciaExterna: string
  urlDePago: string | null
}

const ETIQUETA_ESTADO: Record<EstadoDeMulta, string> = {
  NOTIFICADA: 'Notificada',
  EN_DESCARGO: 'En descargo (en revisión)',
  CONFIRMADA: 'Confirmada',
  ANULADA: 'Anulada',
  PAGADA: 'Pagada',
}

const MONEDA = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })
const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en `PantallaDeTasas` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/** Mismo criterio de validación que `publicarTasa` en `PantallaDeTasas`. */
function montoIngresadoValido(valor: string): boolean {
  const numero = Number(valor)
  return valor.trim() !== '' && Number.isFinite(numero) && numero > 0
}

type Vista = 'busqueda' | 'resultados' | 'descargo' | 'pago'

type EstadoBusqueda =
  | { estado: 'cargando' }
  | { estado: 'listo'; multas: Multa[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoSimulador =
  | { fase: 'iniciando' }
  | { fase: 'listo'; referenciaExterna: string }
  | { fase: 'confirmando'; referenciaExterna: string; aprobado: boolean }
  | { fase: 'error'; mensaje: string }

type ResultadoDePago = { aprobado: boolean; multa: Multa }
type ResultadoDeDescargo = { multa: Multa }

type EstadoLabrado = {
  patente: string
  dni: string
  descripcionInfraccion: string
  monto: string
  enviando: boolean
  error: string | null
}

const LABRADO_INICIAL: EstadoLabrado = {
  patente: '',
  dni: '',
  descripcionInfraccion: '',
  monto: '',
  enviando: false,
  error: null,
}

type EstadoGestion =
  | { estado: 'cargando' }
  | { estado: 'listo'; multas: Multa[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EdicionDescargoGestion = {
  id: number
  accion: 'confirmar' | 'anular'
  comentario: string
  enviando: boolean
  error: string | null
}

/**
 * Pantalla del módulo `multas`: búsqueda pública de multas por patente o
 * DNI, presentación de un descargo, pago simulado in-app con el descuento
 * por pago temprano ya calculado por el backend (ADR 0021 §2) y, visibles
 * solo con el permiso correspondiente (ADR 0011: se esconden por
 * comodidad, el backend vuelve a exigir el permiso), el alta de una multa
 * nueva y la resolución de descargos pendientes. Sin router de URLs en
 * este frontend (ADR 0008): cada paso es un estado local más, mismo
 * patrón que `PantallaDeTasas`.
 */
export function PantallaDeMultas({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeLabrar = usuario?.permisos.includes('multas.labrar') ?? false
  const puedeResolverDescargo = usuario?.permisos.includes('multas.resolverDescargo') ?? false

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

  // --- Búsqueda pública por patente o DNI ---

  const [patente, setPatente] = useState('')
  const [dni, setDni] = useState('')
  const [patenteBuscada, setPatenteBuscada] = useState('')
  const [dniBuscado, setDniBuscado] = useState('')
  const [busqueda, setBusqueda] = useState<EstadoBusqueda>({ estado: 'listo', multas: [] })
  const [resultadoPago, setResultadoPago] = useState<ResultadoDePago | null>(null)
  const [resultadoDescargo, setResultadoDescargo] = useState<ResultadoDeDescargo | null>(null)

  // Se carga uno u otro, no los dos, no ninguno: deshabilita "Buscar"
  // mientras la combinación no sea válida. Igual se deja pasar el pedido a
  // la API si de todos modos llega a dispararse en un estado inválido (por
  // ejemplo por un envío implícito del formulario), para que el mensaje de
  // error 400 del backend se muestre igual con el mismo manejo de errores
  // que cualquier otra búsqueda.
  const combinacionValida = (patente.trim() !== '') !== (dni.trim() !== '')

  const tituloBusqueda = useRef<HTMLHeadingElement>(null)
  const tituloResultados = useRef<HTMLHeadingElement>(null)
  const tituloDescargo = useRef<HTMLHeadingElement>(null)
  const tituloPago = useRef<HTMLHeadingElement>(null)
  const resultadoPagoRef = useRef<HTMLParagraphElement>(null)
  const resultadoDescargoRef = useRef<HTMLParagraphElement>(null)
  const errorPagoRef = useRef<HTMLParagraphElement>(null)
  const errorDescargoRef = useRef<HTMLParagraphElement>(null)

  // --- Simulador de pago (ADR 0018 §3): estado declarado acá arriba
  // porque el efecto de foco de más abajo lo necesita.
  const [multaEnPago, setMultaEnPago] = useState<Multa | null>(null)
  const [simulador, setSimulador] = useState<EstadoSimulador>({ fase: 'iniciando' })

  // --- Presentar descargo: estado declarado acá arriba por la misma razón.
  const [multaEnDescargo, setMultaEnDescargo] = useState<Multa | null>(null)
  const [descargoTexto, setDescargoTexto] = useState('')
  const [descargoContacto, setDescargoContacto] = useState('')
  const [enviandoDescargo, setEnviandoDescargo] = useState(false)
  const [errorDescargo, setErrorDescargo] = useState<string | null>(null)

  // Foco al cambiar de vista: en 'resultados', si se viene de confirmar un
  // pago o de presentar un descargo el foco va al resultado (con su propio
  // anuncio), no al título.
  useEffect(() => {
    if (vista === 'busqueda') {
      tituloBusqueda.current?.focus()
    } else if (vista === 'resultados') {
      if (resultadoPago) {
        resultadoPagoRef.current?.focus()
      } else if (resultadoDescargo) {
        resultadoDescargoRef.current?.focus()
      } else {
        tituloResultados.current?.focus()
      }
    } else if (vista === 'descargo') {
      tituloDescargo.current?.focus()
    } else if (vista === 'pago') {
      tituloPago.current?.focus()
    }
  }, [vista, resultadoPago, resultadoDescargo])

  useEffect(() => {
    if (simulador.fase === 'error') {
      errorPagoRef.current?.focus()
    }
  }, [simulador])

  useEffect(() => {
    if (errorDescargo) {
      errorDescargoRef.current?.focus()
    }
  }, [errorDescargo])

  async function buscar(evento: FormEvent) {
    evento.preventDefault()
    const patenteTrim = patente.trim()
    const dniTrim = dni.trim()
    if (patenteTrim === '' && dniTrim === '') {
      return
    }
    const parametros = new URLSearchParams()
    if (patenteTrim !== '') {
      parametros.set('patente', patenteTrim)
    }
    if (dniTrim !== '') {
      parametros.set('dni', dniTrim)
    }
    setPatenteBuscada(patenteTrim)
    setDniBuscado(dniTrim)
    setResultadoPago(null)
    setResultadoDescargo(null)
    setVista('resultados')
    setBusqueda({ estado: 'cargando' })
    try {
      const multas = await pedir<Multa[]>(
        `/api/multas?${parametros.toString()}`,
        'No se pudieron buscar las multas.',
      )
      if (vigente.current) {
        setBusqueda({ estado: 'listo', multas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setBusqueda({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'multas' })
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
    setResultadoDescargo(null)
  }

  const criterioBuscado = patenteBuscada !== '' ? `la patente ${patenteBuscada}` : `el DNI ${dniBuscado}`

  // --- Pago (simulador) ---

  async function iniciarPago(multa: Multa) {
    setMultaEnPago(multa)
    setResultadoPago(null)
    setResultadoDescargo(null)
    setVista('pago')
    setSimulador({ fase: 'iniciando' })
    try {
      const resultado = await enviar<IniciarPagoResponse>(
        `/api/multas/${multa.id}/pagos`,
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
          mensaje: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'multas'),
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
      const multaActualizada = await enviar<Multa>(
        '/api/multas/pagos/confirmar',
        'POST',
        { referenciaExterna, aprobado },
        'No se pudo confirmar el pago.',
      )
      if (!vigente.current) {
        return
      }
      if (multaActualizada) {
        setBusqueda((actual) =>
          actual.estado === 'listo'
            ? {
                estado: 'listo',
                multas: actual.multas.map((m) => (m.id === multaActualizada.id ? multaActualizada : m)),
              }
            : actual,
        )
        setMultaEnPago(null)
        setResultadoPago({ aprobado, multa: multaActualizada })
        setVista('resultados')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setSimulador({
          fase: 'error',
          mensaje: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'multas'),
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
    setMultaEnPago(null)
    setVista('resultados')
  }

  // --- Presentar descargo ---

  function abrirDescargo(multa: Multa) {
    setResultadoPago(null)
    setResultadoDescargo(null)
    setMultaEnDescargo(multa)
    setDescargoTexto('')
    setDescargoContacto('')
    setErrorDescargo(null)
    setVista('descargo')
  }

  function volverSinDescargo() {
    setMultaEnDescargo(null)
    setVista('resultados')
  }

  async function presentarDescargo(evento: FormEvent) {
    evento.preventDefault()
    if (!multaEnDescargo) {
      return
    }
    if (descargoTexto.trim() === '') {
      setErrorDescargo('Ingresá el texto del descargo.')
      return
    }
    setErrorDescargo(null)
    setEnviandoDescargo(true)
    try {
      const actualizada = await enviar<Multa>(
        `/api/multas/${multaEnDescargo.id}/descargo`,
        'POST',
        {
          texto: descargoTexto,
          contacto: descargoContacto.trim() === '' ? null : descargoContacto,
        },
        'No se pudo presentar el descargo.',
      )
      if (!vigente.current) {
        return
      }
      if (actualizada) {
        setBusqueda((actual) =>
          actual.estado === 'listo'
            ? {
                estado: 'listo',
                multas: actual.multas.map((m) => (m.id === actualizada.id ? actualizada : m)),
              }
            : actual,
        )
        setMultaEnDescargo(null)
        setResultadoDescargo({ multa: actualizada })
        setVista('resultados')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setErrorDescargo(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'multas'))
      } else {
        setErrorDescargo(fallo instanceof Error ? fallo.message : 'No se pudo presentar el descargo.')
      }
    } finally {
      if (vigente.current) {
        setEnviandoDescargo(false)
      }
    }
  }

  // --- Labrar multa (solo con multas.labrar, ADR 0011) ---

  const [formularioLabrarAbierto, setFormularioLabrarAbierto] = useState(false)
  const [labrado, setLabrado] = useState<EstadoLabrado>(LABRADO_INICIAL)
  const [multaLabrada, setMultaLabrada] = useState<{ patente: string } | null>(null)

  const botonLabrar = useRef<HTMLButtonElement>(null)
  const primerCampoLabrado = useRef<HTMLInputElement>(null)
  const errorLabradoRef = useRef<HTMLParagraphElement>(null)
  const confirmacionLabradoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioLabrarAbierto) {
      primerCampoLabrado.current?.focus()
    }
  }, [formularioLabrarAbierto])

  useEffect(() => {
    if (labrado.error) {
      errorLabradoRef.current?.focus()
    }
  }, [labrado.error])

  useEffect(() => {
    if (multaLabrada) {
      confirmacionLabradoRef.current?.focus()
    }
  }, [multaLabrada])

  function abrirFormularioLabrar() {
    setLabrado(LABRADO_INICIAL)
    setMultaLabrada(null)
    setFormularioLabrarAbierto(true)
  }

  function cerrarFormularioLabrar() {
    setFormularioLabrarAbierto(false)
    botonLabrar.current?.focus()
  }

  const idDelErrorLabrado = 'error-de-labrado-multa'

  async function labrarMulta(evento: FormEvent) {
    evento.preventDefault()
    if (!montoIngresadoValido(labrado.monto)) {
      setLabrado((actual) => ({
        ...actual,
        error: 'Ingresá un monto numérico mayor a cero.',
      }))
      return
    }
    setLabrado((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/multas',
        'POST',
        {
          patente: labrado.patente,
          dni: labrado.dni.trim() === '' ? null : labrado.dni,
          descripcionInfraccion: labrado.descripcionInfraccion,
          monto: Number(labrado.monto),
        },
        'No se pudo labrar la multa.',
      )
      if (!vigente.current) {
        return
      }
      setMultaLabrada({ patente: labrado.patente })
      setFormularioLabrarAbierto(false)
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setLabrado((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'multas'),
        }))
      } else {
        setLabrado((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo labrar la multa.',
        }))
      }
    }
  }

  // --- Gestión de descargos (solo con multas.resolverDescargo) ---

  const [gestion, setGestion] = useState<EstadoGestion>({ estado: 'cargando' })

  const cargarGestion = useCallback(async () => {
    try {
      const multas = await pedir<Multa[]>(
        '/api/multas/gestion',
        'No se pudo cargar la lista de multas para gestión.',
      )
      if (vigente.current) {
        setGestion({ estado: 'listo', multas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setGestion({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'multas' })
      } else {
        setGestion({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  // El backend expone `GET /api/multas/gestion` a quien tenga
  // `multas.labrar` o `multas.resolverDescargo`, pero la sección de
  // gestión de descargos solo tiene sentido -y solo se muestra- a quien
  // puede resolverlos.
  useEffect(() => {
    if (puedeResolverDescargo) {
      void cargarGestion()
    }
  }, [puedeResolverDescargo, cargarGestion])

  // Filtro client-side por estado, tal como habilita la spec (Tarea 5,
  // punto 6) a criterio de implementación: el backend de `/gestion`
  // devuelve todas las multas del municipio, acá solo se muestran las que
  // tienen un descargo esperando resolución.
  const multasEnDescargo = gestion.estado === 'listo' ? gestion.multas.filter((m) => m.estado === 'EN_DESCARGO') : []

  const [edicionGestion, setEdicionGestion] = useState<EdicionDescargoGestion | null>(null)
  const botonesGestion = useRef<Map<string, HTMLButtonElement>>(new Map())
  const primerCampoGestion = useRef<HTMLTextAreaElement>(null)
  const errorGestionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicionGestion) {
      primerCampoGestion.current?.focus()
    }
    // Solo al abrir una edición nueva: si solo cambió el comentario o el
    // error no hay que robarle el foco a lo que esté tocando.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicionGestion?.id, edicionGestion?.accion])

  useEffect(() => {
    if (edicionGestion?.error) {
      errorGestionRef.current?.focus()
    }
  }, [edicionGestion?.error])

  function claveBoton(id: number, accion: 'confirmar' | 'anular'): string {
    return `${id}:${accion}`
  }

  function abrirEdicionGestion(multa: Multa, accion: 'confirmar' | 'anular') {
    setEdicionGestion({ id: multa.id, accion, comentario: '', enviando: false, error: null })
  }

  function cerrarEdicionGestion(id: number, accion: 'confirmar' | 'anular') {
    setEdicionGestion(null)
    botonesGestion.current.get(claveBoton(id, accion))?.focus()
  }

  async function guardarEdicionGestion() {
    if (!edicionGestion) {
      return
    }
    if (edicionGestion.comentario.trim() === '') {
      setEdicionGestion({ ...edicionGestion, error: 'Ingresá un comentario para resolver el descargo.' })
      return
    }
    setEdicionGestion({ ...edicionGestion, enviando: true, error: null })
    try {
      await enviar(
        `/api/multas/${edicionGestion.id}/resolver-descargo`,
        'POST',
        {
          comentario: edicionGestion.comentario,
          confirmar: edicionGestion.accion === 'confirmar',
        },
        'No se pudo resolver el descargo.',
      )
      await cargarGestion()
      if (vigente.current) {
        cerrarEdicionGestion(edicionGestion.id, edicionGestion.accion)
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEdicionGestion((actual) =>
          actual
            ? {
                ...actual,
                enviando: false,
                error: fallo instanceof Error ? fallo.message : 'No se pudo resolver el descargo.',
              }
            : actual,
        )
      }
    }
  }

  // --- Vista: simulador de pago ---

  if (vista === 'pago' && multaEnPago) {
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
            <dt>Patente</dt>
            <dd>{multaEnPago.patente}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Infracción</dt>
            <dd>{multaEnPago.descripcionInfraccion}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Monto a pagar</dt>
            <dd>{MONEDA.format(multaEnPago.montoAPagar)}</dd>
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

  // --- Vista: presentar descargo ---

  if (vista === 'descargo' && multaEnDescargo) {
    const idDelErrorDescargo = 'error-de-descargo-multa'
    return (
      <main id="contenido" className="contenido">
        <h1 ref={tituloDescargo} tabIndex={-1}>
          Presentar un descargo
        </h1>
        <p className="contenido__bajada">
          Multa a la patente {multaEnDescargo.patente} por «
          {multaEnDescargo.descripcionInfraccion}». Al presentar el
          descargo, la multa queda en revisión y no se puede pagar hasta
          que el municipio lo resuelva.
        </p>

        <form className="formulario" onSubmit={(evento) => void presentarDescargo(evento)}>
          {errorDescargo && (
            <p
              className="formulario__error"
              id={idDelErrorDescargo}
              role="alert"
              tabIndex={-1}
              ref={errorDescargoRef}
            >
              {errorDescargo}
            </p>
          )}

          <div className="campo">
            <label htmlFor="multas-descargo-texto">Texto del descargo</label>
            <textarea
              id="multas-descargo-texto"
              required
              value={descargoTexto}
              onChange={(evento) => setDescargoTexto(evento.target.value)}
              aria-invalid={errorDescargo ? true : undefined}
              aria-describedby={errorDescargo ? idDelErrorDescargo : undefined}
            />
          </div>

          <div className="campo">
            <label htmlFor="multas-descargo-contacto">Contacto (opcional)</label>
            <input
              id="multas-descargo-contacto"
              value={descargoContacto}
              onChange={(evento) => setDescargoContacto(evento.target.value)}
              aria-describedby="multas-descargo-contacto-ayuda"
            />
            <p className="campo__ayuda" id="multas-descargo-contacto-ayuda">
              Es para que el municipio pueda contactarte si hace falta más
              información sobre el descargo.
            </p>
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton" disabled={enviandoDescargo} aria-busy={enviandoDescargo}>
              {enviandoDescargo ? 'Enviando…' : 'Presentar descargo'}
            </button>
            <button
              type="button"
              className="boton boton--secundario"
              onClick={volverSinDescargo}
              disabled={enviandoDescargo}
            >
              Cancelar
            </button>
          </div>
        </form>
      </main>
    )
  }

  // --- Vista: resultados de la búsqueda ---

  if (vista === 'resultados') {
    return (
      <main id="contenido" className="contenido">
        <h1 ref={tituloResultados} tabIndex={-1}>
          Multas de {criterioBuscado}
        </h1>

        <div className="formulario__acciones">
          <button type="button" className="boton boton--secundario" onClick={volverABuscar}>
            Buscar otra patente o DNI
          </button>
          <button type="button" className="boton boton--secundario" onClick={onVolver}>
            Volver al portal
          </button>
        </div>

        {resultadoPago &&
          (resultadoPago.aprobado ? (
            <p role="status" tabIndex={-1} ref={resultadoPagoRef}>
              Pago aprobado: la multa a la patente {resultadoPago.multa.patente} quedó
              registrada como pagada
              {resultadoPago.multa.fechaPago
                ? ` el ${FECHA.format(new Date(resultadoPago.multa.fechaPago))}`
                : ''}
              .
            </p>
          ) : (
            <p tabIndex={-1} ref={resultadoPagoRef}>
              El pago de la multa a la patente {resultadoPago.multa.patente} se
              rechazó. Sigue en su estado anterior: se puede intentar pagarla
              de nuevo cuando se quiera.
            </p>
          ))}

        {resultadoDescargo && (
          <p role="status" tabIndex={-1} ref={resultadoDescargoRef}>
            El descargo de la multa a la patente {resultadoDescargo.multa.patente} quedó
            registrado. Va a quedar en revisión hasta que el municipio lo
            confirme o le haga lugar: podés volver a buscar la multa más
            adelante para ver el resultado.
          </p>
        )}

        {busqueda.estado === 'cargando' && <p role="status">Buscando multas…</p>}

        {busqueda.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? busqueda.moduloDelError)}
          </p>
        )}

        {busqueda.estado === 'error' && <p role="alert">{busqueda.mensaje}</p>}

        {busqueda.estado === 'listo' && busqueda.multas.length === 0 && (
          <p role="status">No encontramos multas para {criterioBuscado}.</p>
        )}

        {busqueda.estado === 'listo' && busqueda.multas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>Multas de tránsito de {criterioBuscado}.</caption>
              <thead>
                <tr>
                  <th scope="col">Patente</th>
                  <th scope="col">Infracción</th>
                  <th scope="col">Monto a pagar</th>
                  <th scope="col">Estado</th>
                  <th scope="col">Fecha de notificación</th>
                  <th scope="col">Acción</th>
                </tr>
              </thead>
              <tbody>
                {busqueda.multas.map((multa) => (
                  <tr key={multa.id}>
                    <th scope="row">{multa.patente}</th>
                    <td>{multa.descripcionInfraccion}</td>
                    <td>{MONEDA.format(multa.montoAPagar)}</td>
                    <td>{ETIQUETA_ESTADO[multa.estado]}</td>
                    <td>{FECHA.format(new Date(multa.notificadaEn))}</td>
                    <td>
                      {multa.estado === 'EN_DESCARGO' ? (
                        'Descargo presentado, en revisión'
                      ) : multa.estado === 'PAGADA' ? (
                        `Pagada${
                          multa.fechaPago ? ` el ${FECHA.format(new Date(multa.fechaPago))}` : ''
                        }`
                      ) : multa.estado === 'ANULADA' ? (
                        `Anulada${
                          multa.resueltoEn ? ` el ${FECHA.format(new Date(multa.resueltoEn))}` : ''
                        }`
                      ) : (
                        <div className="formulario__acciones formulario__acciones--compacto">
                          <button type="button" className="boton" onClick={() => void iniciarPago(multa)}>
                            Pagar
                          </button>
                          {multa.estado === 'NOTIFICADA' && (
                            <button
                              type="button"
                              className="boton boton--secundario"
                              onClick={() => abrirDescargo(multa)}
                            >
                              Presentar descargo
                            </button>
                          )}
                        </div>
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

  // --- Vista: búsqueda (default) + labrar multa + gestión de descargos ---

  return (
    <main id="contenido" className="contenido">
      <h1 ref={tituloBusqueda} tabIndex={-1}>
        {modulo?.nombre ?? 'Multas de tránsito'}
      </h1>
      <p className="contenido__bajada">
        Consultá las multas de tránsito por patente o por DNI, presentá un
        descargo o pagalas online. No hace falta tener cuenta ni iniciar
        sesión para buscar, presentar un descargo ni para pagar.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-buscar-multas">
        <h2 id="titulo-buscar-multas">Buscar multas por patente o DNI</h2>

        <form className="formulario" onSubmit={(evento) => void buscar(evento)}>
          <div className="campo">
            <label htmlFor="multas-patente">Patente</label>
            <input
              id="multas-patente"
              value={patente}
              onChange={(evento) => setPatente(evento.target.value)}
              aria-describedby="multas-busqueda-ayuda"
            />
          </div>

          <div className="campo">
            <label htmlFor="multas-dni">DNI</label>
            <input
              id="multas-dni"
              value={dni}
              onChange={(evento) => setDni(evento.target.value)}
              aria-describedby="multas-busqueda-ayuda"
            />
          </div>

          <p className="campo__ayuda" id="multas-busqueda-ayuda">
            Completá la patente o el DNI, no hace falta cargar los dos.
          </p>

          <div className="formulario__acciones">
            <button type="submit" className="boton" disabled={!combinacionValida}>
              Buscar
            </button>
          </div>
        </form>
      </section>

      {puedeLabrar && (
        <section aria-labelledby="titulo-labrar-multa">
          <h2 id="titulo-labrar-multa">Labrar una multa</h2>

          {!formularioLabrarAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonLabrar} onClick={abrirFormularioLabrar}>
                Labrar multa
              </button>
              {multaLabrada && (
                <p role="status" tabIndex={-1} ref={confirmacionLabradoRef}>
                  Se labró la multa a la patente {multaLabrada.patente}.
                </p>
              )}
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void labrarMulta(evento)}>
              {labrado.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorLabrado}
                  role="alert"
                  tabIndex={-1}
                  ref={errorLabradoRef}
                >
                  {labrado.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="multas-labrar-patente">Patente</label>
                <input
                  id="multas-labrar-patente"
                  ref={primerCampoLabrado}
                  required
                  value={labrado.patente}
                  onChange={(evento) => setLabrado((actual) => ({ ...actual, patente: evento.target.value }))}
                  aria-invalid={labrado.error ? true : undefined}
                  aria-describedby={labrado.error ? idDelErrorLabrado : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="multas-labrar-dni">DNI (opcional)</label>
                <input
                  id="multas-labrar-dni"
                  value={labrado.dni}
                  onChange={(evento) => setLabrado((actual) => ({ ...actual, dni: evento.target.value }))}
                />
              </div>

              <div className="campo">
                <label htmlFor="multas-labrar-descripcion">Descripción de la infracción</label>
                <textarea
                  id="multas-labrar-descripcion"
                  required
                  value={labrado.descripcionInfraccion}
                  onChange={(evento) =>
                    setLabrado((actual) => ({ ...actual, descripcionInfraccion: evento.target.value }))
                  }
                  aria-invalid={labrado.error ? true : undefined}
                  aria-describedby={labrado.error ? idDelErrorLabrado : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="multas-labrar-monto">Monto</label>
                <input
                  id="multas-labrar-monto"
                  type="number"
                  min="0.01"
                  step="0.01"
                  required
                  value={labrado.monto}
                  onChange={(evento) => setLabrado((actual) => ({ ...actual, monto: evento.target.value }))}
                  aria-invalid={labrado.error ? true : undefined}
                  aria-describedby={labrado.error ? idDelErrorLabrado : undefined}
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={labrado.enviando}
                  aria-busy={labrado.enviando}
                >
                  {labrado.enviando ? 'Labrando…' : 'Labrar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormularioLabrar}
                  disabled={labrado.enviando}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      {puedeResolverDescargo && (
        <section aria-labelledby="titulo-gestion-descargos">
          <h2 id="titulo-gestion-descargos">Descargos pendientes de resolución</h2>

          {gestion.estado === 'cargando' && <p role="status">Cargando los descargos pendientes…</p>}

          {gestion.estado === 'no-contratado' && (
            <p className="formulario__error" role="alert">
              {mensajeModuloNoContratado(modulo?.nombre ?? gestion.moduloDelError)}
            </p>
          )}

          {gestion.estado === 'error' && <p role="alert">{gestion.mensaje}</p>}

          {gestion.estado === 'listo' && multasEnDescargo.length === 0 && (
            <p role="status">No hay descargos esperando resolución en este momento.</p>
          )}

          {gestion.estado === 'listo' && multasEnDescargo.length > 0 && (
            <div className="tabla-contenedor">
              <table className="tabla">
                <caption>
                  Multas con un descargo presentado, esperando que se
                  confirme la multa o se haga lugar al descargo (anulación).
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Patente</th>
                    <th scope="col">Descargo</th>
                    <th scope="col">Contacto</th>
                    <th scope="col">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {multasEnDescargo.map((multa) => {
                    const enEdicion = edicionGestion && edicionGestion.id === multa.id ? edicionGestion : null

                    return (
                      <tr key={multa.id}>
                        <th scope="row">{multa.patente}</th>
                        <td>{multa.descargoTexto}</td>
                        <td>{multa.descargoContacto ?? 'Sin datos de contacto'}</td>
                        <td>
                          {enEdicion ? (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <div className="campo">
                                <label htmlFor={`multa-gestion-${multa.id}-comentario`}>
                                  Comentario para{' '}
                                  {enEdicion.accion === 'confirmar' ? 'confirmar la multa' : 'hacer lugar al descargo'}
                                </label>
                                <textarea
                                  id={`multa-gestion-${multa.id}-comentario`}
                                  ref={primerCampoGestion}
                                  required
                                  value={enEdicion.comentario}
                                  onChange={(evento) =>
                                    setEdicionGestion((actual) =>
                                      actual ? { ...actual, comentario: evento.target.value } : actual,
                                    )
                                  }
                                  aria-invalid={enEdicion.error ? true : undefined}
                                  aria-describedby={
                                    enEdicion.error ? `multa-gestion-${multa.id}-error` : undefined
                                  }
                                />
                              </div>

                              <button
                                type="button"
                                className="boton"
                                disabled={enEdicion.enviando}
                                aria-busy={enEdicion.enviando}
                                onClick={() => void guardarEdicionGestion()}
                              >
                                {enEdicion.enviando
                                  ? 'Guardando…'
                                  : enEdicion.accion === 'confirmar'
                                    ? 'Confirmar multa'
                                    : 'Hacer lugar (anular)'}
                              </button>
                              <button
                                type="button"
                                className="boton boton--secundario"
                                disabled={enEdicion.enviando}
                                onClick={() => cerrarEdicionGestion(multa.id, enEdicion.accion)}
                              >
                                Cancelar
                              </button>
                              {enEdicion.error && (
                                <p
                                  className="formulario__error"
                                  id={`multa-gestion-${multa.id}-error`}
                                  role="alert"
                                  tabIndex={-1}
                                  ref={errorGestionRef}
                                >
                                  {enEdicion.error}
                                </p>
                              )}
                            </div>
                          ) : (
                            <div className="formulario__acciones formulario__acciones--compacto">
                              <button
                                type="button"
                                className="boton"
                                ref={(elemento) => {
                                  const clave = claveBoton(multa.id, 'confirmar')
                                  if (elemento) {
                                    botonesGestion.current.set(clave, elemento)
                                  } else {
                                    botonesGestion.current.delete(clave)
                                  }
                                }}
                                onClick={() => abrirEdicionGestion(multa, 'confirmar')}
                              >
                                Confirmar multa
                              </button>
                              <button
                                type="button"
                                className="boton boton--secundario"
                                ref={(elemento) => {
                                  const clave = claveBoton(multa.id, 'anular')
                                  if (elemento) {
                                    botonesGestion.current.set(clave, elemento)
                                  } else {
                                    botonesGestion.current.delete(clave)
                                  }
                                }}
                                onClick={() => abrirEdicionGestion(multa, 'anular')}
                              >
                                Hacer lugar (anular)
                              </button>
                            </div>
                          )}
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </div>
          )}
        </section>
      )}
    </main>
  )
}
