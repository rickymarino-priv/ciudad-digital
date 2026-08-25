import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, ErrorDeApi, pedir } from '../../acceso/api'
import type { PropsDePantallaDeModulo } from '../registro'

type PartidaPresupuestaria = {
  id: number
  anio: number
  area: string
  numeroPartida: string
  concepto: string
  montoAsignado: number
  montoEjecutado: number | null
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
}

type EscalaSalarial = {
  id: number
  anio: number
  area: string
  cargo: string
  cantidadCargos: number
  montoBrutoMensual: number
  publicadoPorNombre: string
  publicadoPorEmail: string
  creadoEn: string
}

const MONEDA = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' })

function formatearMonto(monto: number | null): string {
  return monto === null ? '—' : MONEDA.format(monto)
}

/** Mismo texto que en `PantallaDeBoletin`/`PantallaDeCementerio` para el aviso de "no contratado". */
function mensajeModuloNoContratado(nombreModulo: string): string {
  return `Este municipio no tiene contratado el módulo ${nombreModulo}. La API rechaza el pedido aunque se abra la pantalla.`
}

type FiltrosDeBusqueda = { anio: string; q: string }

const FILTROS_INICIALES: FiltrosDeBusqueda = { anio: '', q: '' }

function parametrosDe(filtros: FiltrosDeBusqueda): string {
  const parametros = new URLSearchParams()
  if (filtros.anio.trim() !== '') {
    parametros.set('anio', filtros.anio.trim())
  }
  if (filtros.q.trim() !== '') {
    parametros.set('q', filtros.q.trim())
  }
  const query = parametros.toString()
  return query ? `?${query}` : ''
}

type EstadoListado<T> =
  | { estado: 'cargando' }
  | { estado: 'listo'; items: T[] }
  | { estado: 'no-contratado'; moduloDelError: string }
  | { estado: 'error'; mensaje: string }

type EstadoPublicacionPartida = {
  anio: string
  area: string
  numeroPartida: string
  concepto: string
  montoAsignado: string
  montoEjecutado: string
  enviando: boolean
  error: string | null
}

const PUBLICACION_PARTIDA_INICIAL: EstadoPublicacionPartida = {
  anio: '',
  area: '',
  numeroPartida: '',
  concepto: '',
  montoAsignado: '',
  montoEjecutado: '',
  enviando: false,
  error: null,
}

type EstadoPublicacionCargo = {
  anio: string
  area: string
  cargo: string
  cantidadCargos: string
  montoBrutoMensual: string
  enviando: boolean
  error: string | null
}

const PUBLICACION_CARGO_INICIAL: EstadoPublicacionCargo = {
  anio: '',
  area: '',
  cargo: '',
  cantidadCargos: '',
  montoBrutoMensual: '',
  enviando: false,
  error: null,
}

/**
 * Pantalla del módulo `transparencia`: dos secciones independientes de
 * búsqueda pública (sin sesión) — presupuesto y escala salarial — cada
 * una con, dentro de la misma vista, la acción de publicar un registro
 * nuevo, visible solo para quien tiene `transparencia.publicar` (ADR
 * 0011: se esconde por comodidad, el backend vuelve a exigir el
 * permiso). Mismo patrón que `PantallaDeBoletin`/`PantallaDeCementerio`,
 * duplicado dentro del mismo componente para dos dominios de datos que no
 * comparten estado entre sí.
 */
export function PantallaDeTransparencia({ modulo, usuario, onVolver }: PropsDePantallaDeModulo) {
  const puedePublicar = usuario?.permisos.includes('transparencia.publicar') ?? false

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

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // --- Presupuesto: búsqueda ---

  const [anioFiltroPresupuesto, setAnioFiltroPresupuesto] = useState('')
  const [qFiltroPresupuesto, setQFiltroPresupuesto] = useState('')
  const [filtrosPresupuestoAplicados, setFiltrosPresupuestoAplicados] =
    useState<FiltrosDeBusqueda>(FILTROS_INICIALES)

  const [estadoPresupuesto, setEstadoPresupuesto] = useState<EstadoListado<PartidaPresupuestaria>>({
    estado: 'cargando',
  })

  const cargarPartidas = useCallback(async (filtros: FiltrosDeBusqueda) => {
    try {
      const partidas = await pedir<PartidaPresupuestaria[]>(
        `/api/transparencia/presupuesto${parametrosDe(filtros)}`,
        'No se pudo cargar el presupuesto.',
      )
      if (vigente.current) {
        setEstadoPresupuesto({ estado: 'listo', items: partidas })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstadoPresupuesto({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'transparencia' })
      } else {
        setEstadoPresupuesto({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    // Carga inicial y recarga al cambiar los filtros aplicados (mismo
    // patrón que PantallaDeBoletin/PantallaDeCementerio): el setState está
    // protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarPartidas(filtrosPresupuestoAplicados)
  }, [cargarPartidas, filtrosPresupuestoAplicados])

  function buscarPartidas(evento: FormEvent) {
    evento.preventDefault()
    setEstadoPresupuesto({ estado: 'cargando' })
    setFiltrosPresupuestoAplicados({ anio: anioFiltroPresupuesto, q: qFiltroPresupuesto })
  }

  // --- Presupuesto: publicación ---

  const [formularioPartidaAbierto, setFormularioPartidaAbierto] = useState(false)
  const [publicacionPartida, setPublicacionPartida] = useState<EstadoPublicacionPartida>(
    PUBLICACION_PARTIDA_INICIAL,
  )

  const botonPublicarPartida = useRef<HTMLButtonElement>(null)
  const primerCampoPartida = useRef<HTMLInputElement>(null)
  const errorPartidaRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioPartidaAbierto) {
      primerCampoPartida.current?.focus()
    }
  }, [formularioPartidaAbierto])

  useEffect(() => {
    if (publicacionPartida.error) {
      errorPartidaRef.current?.focus()
    }
  }, [publicacionPartida.error])

  function abrirFormularioPartida() {
    setPublicacionPartida(PUBLICACION_PARTIDA_INICIAL)
    setFormularioPartidaAbierto(true)
  }

  function cerrarFormularioPartida() {
    setFormularioPartidaAbierto(false)
    botonPublicarPartida.current?.focus()
  }

  async function publicarPartida(evento: FormEvent) {
    evento.preventDefault()
    setPublicacionPartida((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/transparencia/presupuesto',
        'POST',
        {
          anio: Number(publicacionPartida.anio),
          area: publicacionPartida.area,
          numeroPartida: publicacionPartida.numeroPartida,
          concepto: publicacionPartida.concepto,
          montoAsignado: Number(publicacionPartida.montoAsignado),
          montoEjecutado:
            publicacionPartida.montoEjecutado.trim() === '' ? null : Number(publicacionPartida.montoEjecutado),
        },
        'No se pudo publicar la partida presupuestaria.',
      )
      if (!vigente.current) {
        return
      }
      await cargarPartidas(filtrosPresupuestoAplicados)
      if (vigente.current) {
        setFormularioPartidaAbierto(false)
        botonPublicarPartida.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setPublicacionPartida((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'transparencia'),
        }))
      } else {
        setPublicacionPartida((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la partida presupuestaria.',
        }))
      }
    }
  }

  const idDelErrorPartida = 'error-de-publicacion-partida'

  // --- Sueldos: búsqueda ---

  const [anioFiltroSueldos, setAnioFiltroSueldos] = useState('')
  const [qFiltroSueldos, setQFiltroSueldos] = useState('')
  const [filtrosSueldosAplicados, setFiltrosSueldosAplicados] = useState<FiltrosDeBusqueda>(FILTROS_INICIALES)

  const [estadoSueldos, setEstadoSueldos] = useState<EstadoListado<EscalaSalarial>>({ estado: 'cargando' })

  const cargarCargos = useCallback(async (filtros: FiltrosDeBusqueda) => {
    try {
      const cargos = await pedir<EscalaSalarial[]>(
        `/api/transparencia/sueldos${parametrosDe(filtros)}`,
        'No se pudo cargar la escala salarial.',
      )
      if (vigente.current) {
        setEstadoSueldos({ estado: 'listo', items: cargos })
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setEstadoSueldos({ estado: 'no-contratado', moduloDelError: fallo.modulo ?? 'transparencia' })
      } else {
        setEstadoSueldos({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    // Carga inicial y recarga al cambiar los filtros aplicados: el setState
    // está protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarCargos(filtrosSueldosAplicados)
  }, [cargarCargos, filtrosSueldosAplicados])

  function buscarCargos(evento: FormEvent) {
    evento.preventDefault()
    setEstadoSueldos({ estado: 'cargando' })
    setFiltrosSueldosAplicados({ anio: anioFiltroSueldos, q: qFiltroSueldos })
  }

  // --- Sueldos: publicación ---

  const [formularioCargoAbierto, setFormularioCargoAbierto] = useState(false)
  const [publicacionCargo, setPublicacionCargo] = useState<EstadoPublicacionCargo>(PUBLICACION_CARGO_INICIAL)

  const botonPublicarCargo = useRef<HTMLButtonElement>(null)
  const primerCampoCargo = useRef<HTMLInputElement>(null)
  const errorCargoRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (formularioCargoAbierto) {
      primerCampoCargo.current?.focus()
    }
  }, [formularioCargoAbierto])

  useEffect(() => {
    if (publicacionCargo.error) {
      errorCargoRef.current?.focus()
    }
  }, [publicacionCargo.error])

  function abrirFormularioCargo() {
    setPublicacionCargo(PUBLICACION_CARGO_INICIAL)
    setFormularioCargoAbierto(true)
  }

  function cerrarFormularioCargo() {
    setFormularioCargoAbierto(false)
    botonPublicarCargo.current?.focus()
  }

  async function publicarCargo(evento: FormEvent) {
    evento.preventDefault()
    setPublicacionCargo((actual) => ({ ...actual, enviando: true, error: null }))
    try {
      await enviar(
        '/api/transparencia/sueldos',
        'POST',
        {
          anio: Number(publicacionCargo.anio),
          area: publicacionCargo.area,
          cargo: publicacionCargo.cargo,
          cantidadCargos:
            publicacionCargo.cantidadCargos.trim() === '' ? null : Number(publicacionCargo.cantidadCargos),
          montoBrutoMensual: Number(publicacionCargo.montoBrutoMensual),
        },
        'No se pudo publicar la entrada de escala salarial.',
      )
      if (!vigente.current) {
        return
      }
      await cargarCargos(filtrosSueldosAplicados)
      if (vigente.current) {
        setFormularioCargoAbierto(false)
        botonPublicarCargo.current?.focus()
      }
    } catch (fallo: unknown) {
      if (!vigente.current) {
        return
      }
      if (fallo instanceof ErrorDeApi && fallo.codigo === 'MODULO_NO_CONTRATADO') {
        setPublicacionCargo((actual) => ({
          ...actual,
          enviando: false,
          error: mensajeModuloNoContratado(modulo?.nombre ?? fallo.modulo ?? 'transparencia'),
        }))
      } else {
        setPublicacionCargo((actual) => ({
          ...actual,
          enviando: false,
          error: fallo instanceof Error ? fallo.message : 'No se pudo publicar la entrada de escala salarial.',
        }))
      }
    }
  }

  const idDelErrorCargo = 'error-de-publicacion-cargo'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        {modulo?.nombre ?? 'Transparencia Activa'}
      </h1>
      <p className="contenido__bajada">
        Presupuesto y escala salarial del municipio. No hace falta tener
        cuenta ni iniciar sesión para consultarlos.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver al portal
        </button>
      </div>

      <section aria-labelledby="titulo-busqueda-presupuesto">
        <h2 id="titulo-busqueda-presupuesto">Presupuesto</h2>

        <form className="formulario" onSubmit={buscarPartidas}>
          <div className="campo">
            <label htmlFor="transparencia-presupuesto-filtro-anio">Año</label>
            <input
              id="transparencia-presupuesto-filtro-anio"
              type="number"
              value={anioFiltroPresupuesto}
              onChange={(evento) => setAnioFiltroPresupuesto(evento.target.value)}
            />
          </div>

          <div className="campo">
            <label htmlFor="transparencia-presupuesto-filtro-q">Buscar por área o concepto</label>
            <input
              id="transparencia-presupuesto-filtro-q"
              value={qFiltroPresupuesto}
              onChange={(evento) => setQFiltroPresupuesto(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estadoPresupuesto.estado === 'cargando' && <p role="status">Buscando partidas presupuestarias…</p>}

        {estadoPresupuesto.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estadoPresupuesto.moduloDelError)}
          </p>
        )}

        {estadoPresupuesto.estado === 'error' && <p role="alert">{estadoPresupuesto.mensaje}</p>}

        {estadoPresupuesto.estado === 'listo' && estadoPresupuesto.items.length === 0 && (
          <p>No se encontraron partidas presupuestarias.</p>
        )}

        {estadoPresupuesto.estado === 'listo' && estadoPresupuesto.items.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Partidas presupuestarias publicadas por este municipio en
                Transparencia Activa. Se puede filtrar por año y por texto
                en el área o el concepto.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Año</th>
                  <th scope="col">Área</th>
                  <th scope="col">Número de partida</th>
                  <th scope="col">Concepto</th>
                  <th scope="col">Monto asignado</th>
                  <th scope="col">Monto ejecutado</th>
                </tr>
              </thead>
              <tbody>
                {estadoPresupuesto.items.map((partida) => (
                  <tr key={partida.id}>
                    <th scope="row">{partida.anio}</th>
                    <td>{partida.area}</td>
                    <td>{partida.numeroPartida}</td>
                    <td>{partida.concepto}</td>
                    <td>{formatearMonto(partida.montoAsignado)}</td>
                    <td>{formatearMonto(partida.montoEjecutado)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {puedePublicar && (
        <section aria-labelledby="titulo-publicar-partida">
          <h2 id="titulo-publicar-partida">Publicar una partida presupuestaria</h2>

          {!formularioPartidaAbierto ? (
            <div className="administracion__barra">
              <button
                type="button"
                className="boton"
                ref={botonPublicarPartida}
                onClick={abrirFormularioPartida}
              >
                Publicar partida
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarPartida(evento)}>
              {publicacionPartida.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorPartida}
                  role="alert"
                  tabIndex={-1}
                  ref={errorPartidaRef}
                >
                  {publicacionPartida.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="transparencia-partida-anio">Año</label>
                <input
                  id="transparencia-partida-anio"
                  type="number"
                  ref={primerCampoPartida}
                  required
                  value={publicacionPartida.anio}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, anio: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-partida-area">Área</label>
                <input
                  id="transparencia-partida-area"
                  required
                  value={publicacionPartida.area}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, area: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-partida-numero">Número de partida</label>
                <input
                  id="transparencia-partida-numero"
                  required
                  value={publicacionPartida.numeroPartida}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, numeroPartida: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-partida-concepto">Concepto</label>
                <input
                  id="transparencia-partida-concepto"
                  required
                  value={publicacionPartida.concepto}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, concepto: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-partida-monto-asignado">Monto asignado</label>
                <input
                  id="transparencia-partida-monto-asignado"
                  type="number"
                  required
                  value={publicacionPartida.montoAsignado}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, montoAsignado: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-partida-monto-ejecutado">Monto ejecutado (opcional)</label>
                <input
                  id="transparencia-partida-monto-ejecutado"
                  type="number"
                  value={publicacionPartida.montoEjecutado}
                  onChange={(evento) =>
                    setPublicacionPartida((actual) => ({ ...actual, montoEjecutado: evento.target.value }))
                  }
                  aria-invalid={publicacionPartida.error ? true : undefined}
                  aria-describedby={publicacionPartida.error ? idDelErrorPartida : undefined}
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={publicacionPartida.enviando}
                  aria-busy={publicacionPartida.enviando}
                >
                  {publicacionPartida.enviando ? 'Publicando…' : 'Publicar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormularioPartida}
                  disabled={publicacionPartida.enviando}
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </section>
      )}

      <section aria-labelledby="titulo-busqueda-sueldos">
        <h2 id="titulo-busqueda-sueldos">Sueldos (escala salarial)</h2>

        <form className="formulario" onSubmit={buscarCargos}>
          <div className="campo">
            <label htmlFor="transparencia-sueldos-filtro-anio">Año</label>
            <input
              id="transparencia-sueldos-filtro-anio"
              type="number"
              value={anioFiltroSueldos}
              onChange={(evento) => setAnioFiltroSueldos(evento.target.value)}
            />
          </div>

          <div className="campo">
            <label htmlFor="transparencia-sueldos-filtro-q">Buscar por área o cargo</label>
            <input
              id="transparencia-sueldos-filtro-q"
              value={qFiltroSueldos}
              onChange={(evento) => setQFiltroSueldos(evento.target.value)}
            />
          </div>

          <div className="formulario__acciones">
            <button type="submit" className="boton">
              Buscar
            </button>
          </div>
        </form>

        {estadoSueldos.estado === 'cargando' && <p role="status">Buscando la escala salarial…</p>}

        {estadoSueldos.estado === 'no-contratado' && (
          <p className="formulario__error" role="alert">
            {mensajeModuloNoContratado(modulo?.nombre ?? estadoSueldos.moduloDelError)}
          </p>
        )}

        {estadoSueldos.estado === 'error' && <p role="alert">{estadoSueldos.mensaje}</p>}

        {estadoSueldos.estado === 'listo' && estadoSueldos.items.length === 0 && (
          <p>No se encontraron entradas de escala salarial.</p>
        )}

        {estadoSueldos.estado === 'listo' && estadoSueldos.items.length > 0 && (
          <div className="tabla-contenedor">
            <table className="tabla">
              <caption>
                Escala salarial por cargo o función de este municipio,
                publicada en Transparencia Activa. Los montos son brutos
                mensuales <strong>por cargo</strong>, no por persona: no
                identifican a ningún funcionario ni empleado en particular.
                Se puede filtrar por año y por texto en el área o el
                cargo.
              </caption>
              <thead>
                <tr>
                  <th scope="col">Año</th>
                  <th scope="col">Área</th>
                  <th scope="col">Cargo</th>
                  <th scope="col">Cantidad de cargos</th>
                  <th scope="col">Monto bruto mensual</th>
                </tr>
              </thead>
              <tbody>
                {estadoSueldos.items.map((cargo) => (
                  <tr key={cargo.id}>
                    <th scope="row">{cargo.anio}</th>
                    <td>{cargo.area}</td>
                    <td>{cargo.cargo}</td>
                    <td>{cargo.cantidadCargos}</td>
                    <td>{formatearMonto(cargo.montoBrutoMensual)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {puedePublicar && (
        <section aria-labelledby="titulo-publicar-cargo">
          <h2 id="titulo-publicar-cargo">Publicar una entrada de escala salarial</h2>

          {!formularioCargoAbierto ? (
            <div className="administracion__barra">
              <button type="button" className="boton" ref={botonPublicarCargo} onClick={abrirFormularioCargo}>
                Publicar cargo
              </button>
            </div>
          ) : (
            <form className="formulario" onSubmit={(evento) => void publicarCargo(evento)}>
              {publicacionCargo.error && (
                <p
                  className="formulario__error"
                  id={idDelErrorCargo}
                  role="alert"
                  tabIndex={-1}
                  ref={errorCargoRef}
                >
                  {publicacionCargo.error}
                </p>
              )}

              <div className="campo">
                <label htmlFor="transparencia-cargo-anio">Año</label>
                <input
                  id="transparencia-cargo-anio"
                  type="number"
                  ref={primerCampoCargo}
                  required
                  value={publicacionCargo.anio}
                  onChange={(evento) =>
                    setPublicacionCargo((actual) => ({ ...actual, anio: evento.target.value }))
                  }
                  aria-invalid={publicacionCargo.error ? true : undefined}
                  aria-describedby={publicacionCargo.error ? idDelErrorCargo : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-cargo-area">Área</label>
                <input
                  id="transparencia-cargo-area"
                  required
                  value={publicacionCargo.area}
                  onChange={(evento) =>
                    setPublicacionCargo((actual) => ({ ...actual, area: evento.target.value }))
                  }
                  aria-invalid={publicacionCargo.error ? true : undefined}
                  aria-describedby={publicacionCargo.error ? idDelErrorCargo : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-cargo-cargo">Cargo</label>
                <input
                  id="transparencia-cargo-cargo"
                  required
                  value={publicacionCargo.cargo}
                  onChange={(evento) =>
                    setPublicacionCargo((actual) => ({ ...actual, cargo: evento.target.value }))
                  }
                  aria-invalid={publicacionCargo.error ? true : undefined}
                  aria-describedby={publicacionCargo.error ? idDelErrorCargo : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-cargo-cantidad">Cantidad de cargos (opcional)</label>
                <input
                  id="transparencia-cargo-cantidad"
                  type="number"
                  placeholder="1"
                  value={publicacionCargo.cantidadCargos}
                  onChange={(evento) =>
                    setPublicacionCargo((actual) => ({ ...actual, cantidadCargos: evento.target.value }))
                  }
                  aria-invalid={publicacionCargo.error ? true : undefined}
                  aria-describedby={publicacionCargo.error ? idDelErrorCargo : undefined}
                />
              </div>

              <div className="campo">
                <label htmlFor="transparencia-cargo-monto">Monto bruto mensual por cargo</label>
                <input
                  id="transparencia-cargo-monto"
                  type="number"
                  required
                  value={publicacionCargo.montoBrutoMensual}
                  onChange={(evento) =>
                    setPublicacionCargo((actual) => ({ ...actual, montoBrutoMensual: evento.target.value }))
                  }
                  aria-invalid={publicacionCargo.error ? true : undefined}
                  aria-describedby={publicacionCargo.error ? idDelErrorCargo : undefined}
                />
              </div>

              <div className="formulario__acciones">
                <button
                  type="submit"
                  className="boton"
                  disabled={publicacionCargo.enviando}
                  aria-busy={publicacionCargo.enviando}
                >
                  {publicacionCargo.enviando ? 'Publicando…' : 'Publicar'}
                </button>
                <button
                  type="button"
                  className="boton boton--secundario"
                  onClick={cerrarFormularioCargo}
                  disabled={publicacionCargo.enviando}
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
