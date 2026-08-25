import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type TipoDeParcela = 'NICHO' | 'PANTEON' | 'PARCELA' | 'BOVEDA'

type Sepultura = {
  id: number
  tipoParcela: TipoDeParcela
  sector: string
  fila: string | null
  numero: string
  nombreDifunto: string
  fechaFallecimiento: string
  fechaInhumacion: string
  creadoEn: string
}

const TIPOS: { valor: TipoDeParcela; etiqueta: string }[] = [
  { valor: 'NICHO', etiqueta: 'Nicho' },
  { valor: 'PANTEON', etiqueta: 'Panteón' },
  { valor: 'PARCELA', etiqueta: 'Parcela' },
  { valor: 'BOVEDA', etiqueta: 'Bóveda' },
]

const ETIQUETA_TIPO: Record<TipoDeParcela, string> = TIPOS.reduce(
  (mapa, tipo) => ({ ...mapa, [tipo.valor]: tipo.etiqueta }),
  {} as Record<TipoDeParcela, string>,
)

const FECHA = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short' })

/** Mismo texto que en `PantallaDeEjemplo`/`PantallaDeReclamos`/`PantallaDeBoletin` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

/**
 * Formatea una fecha `"AAAA-MM-DD"` sin desfasaje de huso horario: pasarle
 * ese string directo a `new Date(...)` lo interpreta en UTC, y en un huso
 * negativo puede mostrar el día anterior. Se arma la fecha a partir de los
 * componentes, en la zona local. Copiada de `PantallaDeBoletin` (mismo
 * criterio, sin extraerla para no tocar ese módulo).
 */
function formatearFecha(fechaIso: string): string {
  const [anio, mes, dia] = fechaIso.split('-').map(Number)
  return FECHA.format(new Date(anio, mes - 1, dia))
}

type EstadoListado =
  | { estado: 'cargando' }
  | { estado: 'listo'; sepulturas: Sepultura[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoRegistro = {
  tipoParcela: TipoDeParcela | ''
  sector: string
  fila: string
  numero: string
  nombreDifunto: string
  fechaFallecimiento: string
  fechaInhumacion: string
  nombreTitular: string
  contactoTitular: string
  observaciones: string
  enviando: boolean
  error: string | null
}

const REGISTRO_INICIAL: EstadoRegistro = {
  tipoParcela: '',
  sector: '',
  fila: '',
  numero: '',
  nombreDifunto: '',
  fechaFallecimiento: '',
  fechaInhumacion: '',
  nombreTitular: '',
  contactoTitular: '',
  observaciones: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `cementerio`: búsqueda pública de sepulturas (sin
 * sesión) y, dentro de la misma vista, la acción de registrar una nueva
 * inhumación, visible solo para quien tiene `cementerio.registrar` (ADR
 * 0011: se esconde por comodidad, el backend vuelve a exigir el permiso).
 * Mismo patrón de una única vista con búsqueda pública + acción condicional
 * que `PantallaDeBoletin`, aplicado a otro dominio de datos.
 */
export function PantallaDeCementerio({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedeRegistrar = usuario?.permisos.includes('cementerio.registrar') ?? false

  const [tipoFiltro, setTipoFiltro] = useState<TipoDeParcela | ''>('')
  const [qFiltro, setQFiltro] = useState('')
  const [filtrosAplicados, setFiltrosAplicados] = useState<{ tipo: TipoDeParcela | ''; q: string }>({
    tipo: '',
    q: '',
  })

  const [estado, setEstado] = useState<EstadoListado>({ estado: 'cargando' })

  // Mismo patrón que PanelDeGestion/PanelDeUsuarios/PantallaDeBoletin: evita
  // pisar estado de un componente que ya no está montado cuando un pedido en
  // vuelo termina después.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarSepulturas = useCallback(async (filtros: { tipo: TipoDeParcela | ''; q: string }) => {
    const parametros = new URLSearchParams()
    if (filtros.tipo !== '') {
      parametros.set('tipoParcela', filtros.tipo)
    }
    if (filtros.q.trim() !== '') {
      parametros.set('q', filtros.q.trim())
    }
    const query = parametros.toString()
    try {
      const sepulturas = await pedir<Sepultura[]>(
        `/api/cementerio${query ? `?${query}` : ''}`,
        'No se pudo cargar el registro del cementerio.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', sepulturas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstado({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'cementerio' })
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
    // patrón que PantallaDeBoletin): el setState está protegido por
    // `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarSepulturas(filtrosAplicados)
  }, [cargarSepulturas, filtrosAplicados])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  function buscar(evento: FormEvent) {
    evento.preventDefault()
    setEstado({ estado: 'cargando' })
    setFiltrosAplicados({ tipo: tipoFiltro, q: qFiltro })
  }

  // --- Registro de sepultura ---

  const [formularioAbierto, setFormularioAbierto] = useState(false)
  const [registro, setRegistro] = useState<EstadoRegistro>(REGISTRO_INICIAL)

  const botonRegistrar = useRef<HTMLButtonElement>(null)
  const primerCampoRegistro = useRef<HTMLSelectElement>(null)
  const errorRegistroRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioAbierto) {
      primerCampoRegistro.current?.focus()
    }
  }, [formularioAbierto])

  useEffect(() => {
    if (registro.error) {
      errorRegistroRef.current?.focus()
    }
  }, [registro.error])

  function abrirFormulario() {
    setRegistro(REGISTRO_INICIAL)
    setFormularioAbierto(true)
  }

  function cerrarFormulario() {
    setFormularioAbierto(false)
    botonRegistrar.current?.focus()
  }

  async function registrarSepultura(evento: FormEvent) {
    evento.preventDefault()
    setRegistro((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/cementerio',
        'POST',
        {
          tipoParcela: registro.tipoParcela,
          sector: registro.sector,
          fila: registro.fila.trim() === '' ? null : registro.fila,
          numero: registro.numero,
          nombreDifunto: registro.nombreDifunto,
          fechaFallecimiento: registro.fechaFallecimiento,
          fechaInhumacion: registro.fechaInhumacion,
          nombreTitular: registro.nombreTitular.trim() === '' ? null : registro.nombreTitular,
          contactoTitular: registro.contactoTitular.trim() === '' ? null : registro.contactoTitular,
          observaciones: registro.observaciones.trim() === '' ? null : registro.observaciones,
        },
        'No se pudo registrar la sepultura.',
      )
      if (!vigente.current) {
        return
      }
      await cargarSepulturas(filtrosAplicados)
      if (vigente.current) {
        setFormularioAbierto(false)
        botonRegistrar.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'cementerio'),
        }))
      } else {
        setRegistro((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo registrar la sepultura.',
        }))
      }
    }
  }

  const idDelErrorRegistro = 'error-de-registro-sepultura'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Cementerio'}
      </h1>
      <p className="contenido__bajada">
        Registro de sepulturas del cementerio municipal: parcelas, nichos,
        panteones y bóvedas. No hace falta tener cuenta ni iniciar sesión
        para buscar dónde está sepultado un familiar.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda">
        <h2 id="titulo-busqueda">Buscar sepulturas</h2>

        <form className="formulario" onSubmit={buscar}>
          <div className="campo">
            <label htmlFor="cementerio-filtro-tipo">Tipo de parcela</label>
            <select
              id="cementerio-filtro-tipo"
              value={tipoFiltro}
              onChange={(evento) => setTipoFiltro(evento.target.value as TipoDeParcela | '')}
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
            <label htmlFor="cementerio-filtro-q">Buscar por nombre del difunto</label>
            <input
              id="cementerio-filtro-q"
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

        {estado.estado === 'cargando' && <p role="status">Buscando sepulturas…</p>}

        {estado.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estado.moduloDelError)}
          </p>
        )}

        {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

        {estado.estado === 'listo' && estado.sepulturas.length === 0 && (
          <p>No se encontraron sepulturas.</p>
        )}

        {estado.estado === 'listo' && estado.sepulturas.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Sepulturas registradas en el cementerio municipal de este
                municipio. Se puede filtrar por tipo de parcela y por el
                nombre del difunto.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Tipo de parcela</th>
                  <th scope="col">Sector</th>
                  <th scope="col">Fila</th>
                  <th scope="col">Número</th>
                  <th scope="col">Nombre del difunto</th>
                  <th scope="col">Fecha de fallecimiento</th>
                  <th scope="col">Fecha de inhumación</th>
                </tr>
              </thead>
              <tbody>
                {estado.sepulturas.map((sepultura) => (
                  <tr key={sepultura.id}>
                    <th scope="row">{ETIQUETA_TIPO[sepultura.tipoParcela]}</th>
                    <td>{sepultura.sector}</td>
                    <td>{sepultura.fila ?? '—'}</td>
                    <td>{sepultura.numero}</td>
                    <td>{sepultura.nombreDifunto}</td>
                    <td>{formatearFecha(sepultura.fechaFallecimiento)}</td>
                    <td>{formatearFecha(sepultura.fechaInhumacion)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {puedeRegistrar && (
        <section aria-labelledby="titulo-registrar">
          <h2 id="titulo-registrar">Registrar sepultura</h2>

          {!formularioAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonRegistrar} onClick={abrirFormulario}>
                Registrar sepultura
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void registrarSepultura(evento)}>
              {registro.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorRegistro}
                  role="alert"
                  tabIndex={-1}
                  ref={errorRegistroRef}
                >
                  {registro.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="cementerio-tipo-parcela">Tipo de parcela</label>
                <select
                  id="cementerio-tipo-parcela"
                  ref={primerCampoRegistro}
                  required
                  value={registro.tipoParcela}
                  onChange={(evento) =>
                    setRegistro((actual) => ({
                      ...actual,
                      tipoParcela: evento.target.value as TipoDeParcela,
                    }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
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
                <label htmlFor="cementerio-sector">Sector</label>
                <input
                  id="cementerio-sector"
                  required
                  value={registro.sector}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, sector: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-fila">Fila (opcional)</label>
                <input
                  id="cementerio-fila"
                  value={registro.fila}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fila: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-numero">Número</label>
                <input
                  id="cementerio-numero"
                  required
                  value={registro.numero}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, numero: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-nombre-difunto">Nombre del difunto</label>
                <input
                  id="cementerio-nombre-difunto"
                  required
                  value={registro.nombreDifunto}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, nombreDifunto: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-fecha-fallecimiento">Fecha de fallecimiento</label>
                <input
                  id="cementerio-fecha-fallecimiento"
                  type="date"
                  required
                  value={registro.fechaFallecimiento}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fechaFallecimiento: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-fecha-inhumacion">Fecha de inhumación</label>
                <input
                  id="cementerio-fecha-inhumacion"
                  type="date"
                  required
                  value={registro.fechaInhumacion}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, fechaInhumacion: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-nombre-titular">Nombre del titular (opcional)</label>
                <input
                  id="cementerio-nombre-titular"
                  value={registro.nombreTitular}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, nombreTitular: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-contacto-titular">Contacto del titular (opcional)</label>
                <input
                  id="cementerio-contacto-titular"
                  value={registro.contactoTitular}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, contactoTitular: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="cementerio-observaciones">Observaciones (opcional)</label>
                <textarea
                  id="cementerio-observaciones"
                  value={registro.observaciones}
                  onChange={(evento) =>
                    setRegistro((actual) => ({ ...actual, observaciones: evento.target.value }))
                  }
                  aria-invalid={registro.error ? true : undefined}
                  aria-describedby={registro.error ? idDelErrorRegistro : undefined}
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={registro.enviando}
                  aria-busy={registro.enviando}
                >
                  {registro.enviando ? 'Registrando…' : 'Registrar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormulario}
                  disabled={registro.enviando}
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
