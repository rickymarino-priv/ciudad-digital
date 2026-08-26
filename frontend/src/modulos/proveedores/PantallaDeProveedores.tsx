import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { Usuario } from '../../acceso/useSesion'
import type { PropsDePantallaDeModulo } from '../registro'
import type { Modulo } from '../useModulos'

type Rubro =
  | 'CONSTRUCCION'
  | 'SERVICIOS'
  | 'INSUMOS_Y_SUMINISTROS'
  | 'PROFESIONALES'
  | 'TECNOLOGIA'
  | 'OTRO'

type Estado = 'PENDIENTE' | 'APROBADO' | 'RECHAZADO'

/** Respuesta de `GET /api/proveedores` (shape completo, `proveedores.ver`). */
type Proveedor = {
  id: number
  razonSocial: string
  cuit: string
  rubro: Rubro
  emailContacto: string
  telefonoContacto: string
  domicilio: string
  declaraConstanciaAfip: boolean
  declaraSeguroResponsabilidadCivil: boolean
  declaraCertificadoAntecedentes: boolean
  documentacionAdicional: string | null
  estado: Estado
  comentarioGestion: string | null
  creadoEn: string
  actualizadoEn: string
}

/** Respuesta de `POST /api/proveedores` (ADR 0017 §4). */
type RespuestaAlta = {
  id: number
  razonSocial: string
  cuit: string
  rubro: Rubro
  estado: Estado
  creadoEn: string
  /**
   * Secreto que habilita la consulta pública posterior: el backend lo
   * devuelve en claro una única vez, acá. No se vuelve a poder leer
   * después, así que la pantalla tiene que dejarlo bien visible y copiable.
   */
  tokenDeSeguimiento: string
}

/** Respuesta de `GET /api/proveedores/seguimiento/{token}` (ADR 0017 §5):
 * subconjunto de `Proveedor`, sin datos que la propia empresa ya tiene. */
type SeguimientoDeProveedor = {
  id: number
  razonSocial: string
  cuit: string
  rubro: Rubro
  estado: Estado
  comentarioGestion: string | null
  declaraConstanciaAfip: boolean
  declaraSeguroResponsabilidadCivil: boolean
  declaraCertificadoAntecedentes: boolean
  documentacionAdicional: string | null
  creadoEn: string
  actualizadoEn: string
}

const RUBROS: { valor: Rubro; etiqueta: string }[] = [
  { valor: 'CONSTRUCCION', etiqueta: 'Construcción' },
  { valor: 'SERVICIOS', etiqueta: 'Servicios' },
  { valor: 'INSUMOS_Y_SUMINISTROS', etiqueta: 'Insumos y suministros' },
  { valor: 'PROFESIONALES', etiqueta: 'Servicios profesionales' },
  { valor: 'TECNOLOGIA', etiqueta: 'Tecnología' },
  { valor: 'OTRO', etiqueta: 'Otro' },
]

const ETIQUETA_RUBRO: Record<Rubro, string> = RUBROS.reduce(
  (mapa, rubro) => ({ ...mapa, [rubro.valor]: rubro.etiqueta }),
  {} as Record<Rubro, string>,
)

const ETIQUETA_ESTADO: Record<Estado, string> = {
  PENDIENTE: 'Pendiente',
  APROBADO: 'Aprobado',
  RECHAZADO: 'Rechazado',
}

// Mismo mapa de transiciones válidas que valida el backend (mismo criterio
// que `reclamos`, ADR 0014 §3): acá solo decide qué acciones ofrecer, el
// enforcement real sigue siendo del backend (ADR 0011).
const TRANSICIONES_VALIDAS: Record<Estado, Estado[]> = {
  PENDIENTE: ['APROBADO', 'RECHAZADO'],
  APROBADO: [],
  RECHAZADO: [],
}

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short' })

/** Mismo texto que en el resto de las pantallas de módulo para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/** Cada documento declarado, como texto "Sí"/"No" — nunca solo un ícono (WCAG). */
function textoSiNo(valor: boolean): string {
  return valor ? 'Sí' : 'No'
}

function itemsDeDocumentacion(proveedor: {
  declaraConstanciaAfip: boolean
  declaraSeguroResponsabilidadCivil: boolean
  declaraCertificadoAntecedentes: boolean
}): string[] {
  return [
    `Constancia de AFIP: ${textoSiNo(proveedor.declaraConstanciaAfip)}`,
    `Seguro de responsabilidad civil: ${textoSiNo(proveedor.declaraSeguroResponsabilidadCivil)}`,
    `Certificado de antecedentes: ${textoSiNo(proveedor.declaraCertificadoAntecedentes)}`,
  ]
}

/**
 * Pantalla del módulo `proveedores`: alta pública de una empresa que quiere
 * venderle al municipio, y panel de gestión para el personal municipal (ADR
 * 0014 §1/§3, ADR 0017 tercer consumidor de `seguimientoanonimo`).
 *
 * Qué se muestra no depende de la vista sino del permiso: quien no tiene
 * `proveedores.ver` —incluida una empresa sin sesión— ve el formulario de
 * alta; quien tiene `proveedores.ver` ve el panel de gestión. Mismo
 * criterio de "esconder por comodidad, no por seguridad" del resto del
 * frontend (ADR 0011): el backend vuelve a exigir el permiso en cada ruta.
 */
export function PantallaDeProveedores({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeVer = usuario?.permisos.includes('proveedores.ver') ?? false

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

  const [razonSocial, setRazonSocial] = useState('')
  const [cuit, setCuit] = useState('')
  const [rubro, setRubro] = useState<Rubro | ''>('')
  const [emailContacto, setEmailContacto] = useState('')
  const [telefonoContacto, setTelefonoContacto] = useState('')
  const [domicilio, setDomicilio] = useState('')
  const [declaraConstanciaAfip, setDeclaraConstanciaAfip] = useState(false)
  const [declaraSeguroResponsabilidadCivil, setDeclaraSeguroResponsabilidadCivil] = useState(false)
  const [declaraCertificadoAntecedentes, setDeclaraCertificadoAntecedentes] = useState(false)
  const [documentacionAdicional, setDocumentacionAdicional] = useState('')
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
    // recuperaba el foco y quedaba en `<body>`.
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

  async function enviarAlta(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setConfirmacion(null)
    setEnviando(true)
    try {
      const respuesta = await enviar<RespuestaAlta>(
        '/api/proveedores',
        'POST',
        {
          razonSocial,
          cuit,
          rubro,
          emailContacto,
          telefonoContacto,
          domicilio,
          declaraConstanciaAfip,
          declaraSeguroResponsabilidadCivil,
          declaraCertificadoAntecedentes,
          documentacionAdicional: documentacionAdicional.trim() === '' ? null : documentacionAdicional,
        },
        'No se pudo registrar el proveedor.',
      )
      if (!vigente.current) {
        return
      }
      if (respuesta) {
        setConfirmacion(respuesta)
        setRazonSocial('')
        setCuit('')
        setRubro('')
        setEmailContacto('')
        setTelefonoContacto('')
        setDomicilio('')
        setDeclaraConstanciaAfip(false)
        setDeclaraSeguroResponsabilidadCivil(false)
        setDeclaraCertificadoAntecedentes(false)
        setDocumentacionAdicional('')
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setError(mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'proveedores'))
      } else {
        setError(fallo instanceof Error ? fallo.message : 'No se pudo registrar el proveedor.')
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
   * HTTPS, permiso denegado) no hay nada más que hacer.
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

  const idDelError = 'error-de-alta-proveedor'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Proveedores'}
      </h1>
      <p className="contenido__bajada">
        Registrate como proveedor del municipio: completá tus datos y la
        documentación que declarás tener. No hace falta que tengas cuenta ni
        que inicies sesión.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
        <button type="button" className="boton boton--secundario" onClick={() => setVista('consulta')}>
          ¿Ya te registraste? Consultá el estado
        </button>
      </div>

      <form className="formulario" onSubmit={(evento) => void enviarAlta(evento)}>
        {error && (
          <p className="formulario__error" id={idDelError} role="alert" tabIndex={-1} ref={errorRef}>
            {error}
          </p>
        )}

        {confirmacion && (
          <div role="status" tabIndex={-1} ref={confirmacionRef}>
            <p>
              Tu registro quedó cargado con el número {confirmacion.id}. Vas a
              ver el estado «Pendiente» hasta que el municipio lo revise.
            </p>
            <p>
              <strong>
                Guardá este código de seguimiento: es la única forma de
                volver a consultar el estado de tu registro más adelante.
              </strong>{' '}
              No lo vamos a reenviar por ningún otro medio ni lo vas a poder
              recuperar si lo perdés.
            </p>
            <div className="campo">
              <label htmlFor="proveedor-token-generado">Código de seguimiento</label>
              <div className="formulario__acciones formulario__acciones--compacto">
                <input
                  id="proveedor-token-generado"
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
          <label htmlFor="proveedor-razon-social">Razón social</label>
          <input
            id="proveedor-razon-social"
            required
            value={razonSocial}
            onChange={(evento) => setRazonSocial(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="proveedor-cuit">CUIT</label>
          <input
            id="proveedor-cuit"
            required
            placeholder="20-12345678-1"
            value={cuit}
            onChange={(evento) => setCuit(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? `${idDelError} proveedor-cuit-ayuda` : 'proveedor-cuit-ayuda'}
          />
          <p className="campo__ayuda" id="proveedor-cuit-ayuda">
            Lo podés escribir con o sin guiones.
          </p>
        </div>

        <div className="campo">
          <label htmlFor="proveedor-rubro">Rubro</label>
          <select
            id="proveedor-rubro"
            required
            value={rubro}
            onChange={(evento) => setRubro(evento.target.value as Rubro)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          >
            <option value="" disabled>
              Elegí un rubro
            </option>
            {RUBROS.map((opcion) => (
              <option key={opcion.valor} value={opcion.valor}>
                {opcion.etiqueta}
              </option>
            ))}
          </select>
        </div>

        <div className="campo">
          <label htmlFor="proveedor-email">Email de contacto</label>
          <input
            id="proveedor-email"
            required
            value={emailContacto}
            onChange={(evento) => setEmailContacto(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="proveedor-telefono">Teléfono de contacto</label>
          <input
            id="proveedor-telefono"
            required
            value={telefonoContacto}
            onChange={(evento) => setTelefonoContacto(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="proveedor-domicilio">Domicilio</label>
          <input
            id="proveedor-domicilio"
            required
            value={domicilio}
            onChange={(evento) => setDomicilio(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <fieldset className="grupo-checkboxes">
          <legend>Documentación que declarás tener</legend>
          <label className="grupo-checkboxes__opcion">
            <input
              type="checkbox"
              checked={declaraConstanciaAfip}
              onChange={(evento) => setDeclaraConstanciaAfip(evento.target.checked)}
            />
            Constancia de AFIP
          </label>
          <label className="grupo-checkboxes__opcion">
            <input
              type="checkbox"
              checked={declaraSeguroResponsabilidadCivil}
              onChange={(evento) => setDeclaraSeguroResponsabilidadCivil(evento.target.checked)}
            />
            Seguro de responsabilidad civil
          </label>
          <label className="grupo-checkboxes__opcion">
            <input
              type="checkbox"
              checked={declaraCertificadoAntecedentes}
              onChange={(evento) => setDeclaraCertificadoAntecedentes(evento.target.checked)}
            />
            Certificado de antecedentes
          </label>
          <p className="campo__ayuda">
            En esta etapa no se suben archivos: esto es una declaración. El
            municipio puede pedir que la presentes por otro medio.
          </p>
        </fieldset>

        <div className="campo">
          <label htmlFor="proveedor-documentacion-adicional">Observaciones (opcional)</label>
          <textarea
            id="proveedor-documentacion-adicional"
            value={documentacionAdicional}
            onChange={(evento) => setDocumentacionAdicional(evento.target.value)}
          />
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Enviando…' : 'Registrarme como proveedor'}
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
 * Sub-vista de `FormularioDeAlta`: una empresa sin sesión, con el token que
 * recibió al registrarse, consulta en qué quedó — de solo lectura, sin
 * ninguna acción posible (ADR 0017 §5/§6).
 */
function ConsultaDeSeguimiento({ modulo, onVolver }: PropsConsulta) {
  const [codigo, setCodigo] = useState('')
  const [consultando, setConsultando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [resultado, setResultado] = useState<SeguimientoDeProveedor | null>(null)

  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  const errorRef = useRef<HTMLParagraphElement>(null)
  const resultadoRef = useRef<HTMLDListElement>(null)

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
      const respuesta = await pedir<SeguimientoDeProveedor>(
        `/api/proveedores/seguimiento/${encodeURIComponent(codigo.trim())}`,
        'No encontramos un proveedor con ese código.',
      )
      if (!vigente.current) {
        return
      }
      setResultado(respuesta)
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      setError(fallo instanceof Error ? fallo.message : 'No encontramos un proveedor con ese código.')
    } finally {
      if (vigente.current) {
        setConsultando(false)
      }
    }
  }

  const idDelError = 'error-de-consulta-proveedor'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Consultar el estado de un registro de proveedor
      </h1>
      <p className="contenido__bajada">
        Ingresá el código de seguimiento que recibiste al registrarte en{' '}
        {modulo?.nombre ?? 'Proveedores'}, sin espacios ni caracteres de más.
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
          <label htmlFor="proveedor-codigo-seguimiento">Código de seguimiento</label>
          <input
            id="proveedor-codigo-seguimiento"
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
        <dl className="ficha" role="status" tabIndex={-1} ref={resultadoRef}>
          <div className="ficha__fila">
            <dt>Razón social</dt>
            <dd>{resultado.razonSocial}</dd>
          </div>
          <div className="ficha__fila">
            <dt>CUIT</dt>
            <dd>{resultado.cuit}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Rubro</dt>
            <dd>{ETIQUETA_RUBRO[resultado.rubro]}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Estado</dt>
            <dd>{ETIQUETA_ESTADO[resultado.estado]}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Documentación declarada</dt>
            <dd>
              <ul className="lista-compacta">
                {itemsDeDocumentacion(resultado).map((item) => (
                  <li key={item}>{item}</li>
                ))}
              </ul>
            </dd>
          </div>
          <div className="ficha__fila">
            <dt>Observaciones</dt>
            <dd>{resultado.documentacionAdicional ?? 'Sin observaciones.'}</dd>
          </div>
          <div className="ficha__fila">
            <dt>Comentario del municipio</dt>
            <dd>{resultado.comentarioGestion ?? 'Todavía no hay comentario del municipio.'}</dd>
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
      )}
    </main>
  )
}

// --- Panel de gestión (con proveedores.ver, y proveedores.gestionar si aplica) ---

type PropsGestion = {
  modulo?: Modulo
  usuario: Usuario
  onVolver: () => void
}

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; proveedores: Proveedor[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EdicionProveedor = {
  id: number
  estado: Estado
  comentario: string
  enviando: boolean
  error: string | null
}

/** Email y teléfono de contacto, en un solo texto de columna. */
function textoContacto(proveedor: Proveedor): string {
  return `${proveedor.emailContacto} — ${proveedor.telefonoContacto}`
}

function PanelDeGestion({ modulo, usuario, onVolver }: PropsGestion) {
  const puedeGestionar = usuario.permisos.includes('proveedores.gestionar')

  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })

  // Mismo patrón que PantallaDeReclamos: evita pisar estado de un
  // componente que ya no está montado cuando un pedido en vuelo termina
  // después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarProveedores = useCallback(async () => {
    try {
      const proveedores = await pedir<Proveedor[]>(
        '/api/proveedores',
        'No se pudo cargar la lista de proveedores.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', proveedores })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'proveedores' })
      } else {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    void cargarProveedores()
  }, [cargarProveedores])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // --- Edición de estado por fila: "Aprobar"/"Rechazar" abren una edición
  // inline con el estado ya elegido por el botón que se apretó, y un
  // comentario opcional (mismo patrón de foco y manejo de error que
  // abrirEdicion/guardarEdicion en PantallaDeReclamos, adaptado a que acá
  // el estado nuevo no se elige en un <select> sino con el botón). ---

  const [edicion, setEdicion] = useState<EdicionProveedor | null>(null)
  const botonAbridor = useRef<HTMLButtonElement | null>(null)
  const primerCampoEdicion = useRef<HTMLTextAreaElement>(null)
  const errorEdicionRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (edicion) {
      primerCampoEdicion.current?.focus()
    }
    // Solo al cambiar de fila: si solo cambió el comentario o el error no
    // hay que robarle el foco a lo que esté tocando.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [edicion?.id])

  useEffect(() => {
    if (edicion?.error) {
      errorEdicionRef.current?.focus()
    }
  }, [edicion?.error])

  function abrirEdicion(proveedor: Proveedor, estadoElegido: Estado, boton: HTMLButtonElement) {
    if (!TRANSICIONES_VALIDAS[proveedor.estado].includes(estadoElegido)) {
      return
    }
    botonAbridor.current = boton
    setEdicion({
      id: proveedor.id,
      estado: estadoElegido,
      comentario: '',
      enviando: false,
      error: null,
    })
  }

  function cerrarEdicion() {
    setEdicion(null)
    botonAbridor.current?.focus()
  }

  async function guardarEdicion() {
    if (!edicion) {
      return
    }
    setEdicion({ ...edicion, enviando: true, error: null })
    try {
      await enviar(
        `/api/proveedores/${edicion.id}/estado`,
        'PATCH',
        {
          estado: edicion.estado,
          comentario: edicion.comentario.trim() === '' ? null : edicion.comentario,
        },
        'No se pudo actualizar el estado del proveedor.',
      )
      await cargarProveedores()
      if (vigente.current) {
        cerrarEdicion()
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEdicion((actual) =>
          actual
            ? {
                ...actual,
                enviando: false,
                error:
                  fallo instanceof Error ? fallo.message : 'No se pudo actualizar el estado del proveedor.',
              }
            : actual,
        )
      }
    }
  }

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Proveedores'}
      </h1>
      <p className="contenido__bajada">
        Empresas que se registraron como proveedoras del municipio desde el
        portal público, sin cuenta.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      {estado.estado === 'cargando' && <p role="status">Cargando los proveedores…</p>}

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
              Proveedores registrados del municipio, con la documentación que
              declaran tener y su estado de aprobación.
              {puedeGestionar && ' Se puede aprobar o rechazar a los que todavía están pendientes.'}
            </caption>
            <thead>
              <tr>
                <th scope="col">Razón social</th>
                <th scope="col">CUIT</th>
                <th scope="col">Rubro</th>
                <th scope="col">Contacto</th>
                <th scope="col">Documentación declarada</th>
                <th scope="col">Estado</th>
                <th scope="col">Creado</th>
                {puedeGestionar && <th scope="col">Acción</th>}
              </tr>
            </thead>
            <tbody>
              {estado.proveedores.map((proveedor) => {
                const enEdicion = edicion && edicion.id === proveedor.id ? edicion : null
                const opcionesValidas = TRANSICIONES_VALIDAS[proveedor.estado]

                return (
                  <tr key={proveedor.id}>
                    <th scope="row">{proveedor.razonSocial}</th>
                    <td>{proveedor.cuit}</td>
                    <td>{ETIQUETA_RUBRO[proveedor.rubro]}</td>
                    <td>{textoContacto(proveedor)}</td>
                    <td>
                      <ul className="lista-compacta">
                        {itemsDeDocumentacion(proveedor).map((item) => (
                          <li key={item}>{item}</li>
                        ))}
                      </ul>
                    </td>
                    <td>{ETIQUETA_ESTADO[proveedor.estado]}</td>
                    <td>{FECHA.format(new Date(proveedor.creadoEn))}</td>
                    {puedeGestionar && (
                      <td>
                        {enEdicion ? (
                          <div className="formulario__acciones formulario__acciones--compacto">
                            <p>
                              Vas a marcar este proveedor como «
                              {ETIQUETA_ESTADO[enEdicion.estado]}».
                            </p>
                            <div className="campo">
                              <label htmlFor={`proveedor-${proveedor.id}-comentario`}>
                                Comentario (opcional)
                              </label>
                              <textarea
                                id={`proveedor-${proveedor.id}-comentario`}
                                ref={primerCampoEdicion}
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
                              {enEdicion.enviando ? 'Guardando…' : 'Confirmar'}
                            </button>
                            <button
                              type="button"
                              className="boton boton--secundario"
                              onClick={() => cerrarEdicion()}
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
                          <div className="formulario__acciones formulario__acciones--compacto">
                            <button
                              type="button"
                              className="boton"
                              onClick={(evento) =>
                                abrirEdicion(proveedor, 'APROBADO', evento.currentTarget)
                              }
                            >
                              Aprobar
                            </button>
                            <button
                              type="button"
                              className="boton boton--secundario"
                              onClick={(evento) =>
                                abrirEdicion(proveedor, 'RECHAZADO', evento.currentTarget)
                              }
                            >
                              Rechazar
                            </button>
                          </div>
                        ) : (
                          'Sin cambios disponibles'
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
