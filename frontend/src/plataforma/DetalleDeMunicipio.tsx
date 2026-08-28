import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react'

import { enviar, pedir } from '../acceso/api'
import {
  ESTADOS_DE_FACTURACION,
  TEXTO_ESTADO_DE_FACTURACION,
  TEXTO_ESTADO_DE_SOLICITUD,
  TEXTO_TIPO_DE_SOLICITUD,
  TEXTO_TRAMO_POBLACIONAL,
  TRAMOS_POBLACIONALES,
  type ModulosDeMunicipioResponse,
  type MunicipioResponse,
  type SolicitudDeModuloResponse,
} from './tipos'

type EstadoCatalogo =
  | { estado: 'cargando' }
  | { estado: 'listo'; modulos: ModulosDeMunicipioResponse['modulos'] }
  | { estado: 'error'; mensaje: string }

type EstadoSolicitudes =
  | { estado: 'cargando' }
  | { estado: 'listo'; solicitudes: SolicitudDeModuloResponse[] }
  | { estado: 'error'; mensaje: string }

const FECHA_SOLICITUD = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

type Props = {
  slug: string
  /**
   * Datos del municipio, tal como ya los tiene la lista: evita un segundo
   * `GET /api/admin/municipios` completo solo para leer una fila (el
   * backend no tiene un endpoint de "un solo municipio").
   */
  municipioInicial: MunicipioResponse
  onVolver: () => void
}

/**
 * Detalle de un municipio en la consola del proveedor (ADR 0019): módulos
 * contratados e información comercial. No muestra, ni puede mostrar, nada
 * de lo que pasa dentro de la base del municipio (usuarios, reclamos,
 * tasas): solo el contrato.
 */
export function DetalleDeMunicipio({ slug, municipioInicial, onVolver }: Props) {
  const [municipio, setMunicipio] = useState(municipioInicial)

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

  // --- Módulos contratados ---

  const [catalogo, setCatalogo] = useState<EstadoCatalogo>({ estado: 'cargando' })
  const [seleccionados, setSeleccionados] = useState<Set<string>>(new Set())
  const [enviandoModulos, setEnviandoModulos] = useState(false)
  const [errorModulos, setErrorModulos] = useState<string | null>(null)
  const [exitoModulos, setExitoModulos] = useState<string | null>(null)

  const errorModulosRef = useRef<HTMLParagraphElement>(null)
  const exitoModulosRef = useRef<HTMLParagraphElement>(null)

  // Aplica una respuesta del catálogo al estado, tanto la primera carga
  // (efecto de montaje) como el refresco después de guardar: en ningún caso
  // se sincroniza aparte, es la misma transformación.
  const aplicarCatalogo = useCallback((respuesta: ModulosDeMunicipioResponse) => {
    setCatalogo({ estado: 'listo', modulos: respuesta.modulos })
    setSeleccionados(
      new Set(respuesta.modulos.filter((modulo) => modulo.habilitado).map((modulo) => modulo.codigo)),
    )
  }, [])

  // Carga inicial del catálogo. Va inline en el efecto (mismo patrón que
  // useSesion) en vez de a través de una función con nombre invocada desde
  // acá: el refresco posterior a guardar vive en `guardarModulos`, que no
  // corre dentro de un efecto.
  useEffect(() => {
    let vigente = true

    pedir<ModulosDeMunicipioResponse>(
      `/api/admin/municipios/${slug}/modulos`,
      'No se pudo cargar el catálogo de módulos.',
    )
      .then((respuesta) => {
        if (vigente) {
          aplicarCatalogo(respuesta)
        }
      })
      .catch((fallo: unknown) => {
        if (vigente) {
          setCatalogo({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigente = false
    }
  }, [slug, aplicarCatalogo])

  useEffect(() => {
    if (errorModulos) {
      errorModulosRef.current?.focus()
    }
  }, [errorModulos])

  useEffect(() => {
    if (exitoModulos) {
      exitoModulosRef.current?.focus()
    }
  }, [exitoModulos])

  function alternarModulo(codigo: string) {
    setSeleccionados((actual) => {
      const copia = new Set(actual)
      if (copia.has(codigo)) {
        copia.delete(codigo)
      } else {
        copia.add(codigo)
      }
      return copia
    })
  }

  async function guardarModulos(evento: FormEvent) {
    evento.preventDefault()
    setErrorModulos(null)
    setExitoModulos(null)
    setEnviandoModulos(true)
    try {
      // El PUT ya devuelve el catálogo actualizado (mismo contrato que el
      // GET), así que aplica esa respuesta directamente en vez de pedirlo
      // de nuevo.
      const respuesta = await enviar<ModulosDeMunicipioResponse>(
        `/api/admin/municipios/${slug}/modulos`,
        'PUT',
        { modulos: Array.from(seleccionados) },
        'No se pudieron guardar los módulos.',
      )
      if (vigente.current) {
        if (respuesta) {
          aplicarCatalogo(respuesta)
        }
        setExitoModulos('Se guardaron los módulos contratados.')
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorModulos(fallo instanceof Error ? fallo.message : 'No se pudieron guardar los módulos.')
      }
    } finally {
      if (vigente.current) {
        setEnviandoModulos(false)
      }
    }
  }

  // --- Información comercial ---

  const [tramoPoblacional, setTramoPoblacional] = useState(municipioInicial.tramoPoblacional)
  const [estadoFacturacion, setEstadoFacturacion] = useState(municipioInicial.estadoFacturacion)
  const [notaFacturacion, setNotaFacturacion] = useState(municipioInicial.notaFacturacion ?? '')
  const [enviandoComercial, setEnviandoComercial] = useState(false)
  const [errorComercial, setErrorComercial] = useState<string | null>(null)
  const [exitoComercial, setExitoComercial] = useState<string | null>(null)

  const errorComercialRef = useRef<HTMLParagraphElement>(null)
  const exitoComercialRef = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    if (errorComercial) {
      errorComercialRef.current?.focus()
    }
  }, [errorComercial])

  useEffect(() => {
    if (exitoComercial) {
      exitoComercialRef.current?.focus()
    }
  }, [exitoComercial])

  async function guardarComercial(evento: FormEvent) {
    evento.preventDefault()
    setErrorComercial(null)
    setExitoComercial(null)
    setEnviandoComercial(true)
    try {
      const actualizado = await enviar<MunicipioResponse>(
        `/api/admin/municipios/${slug}/comercial`,
        'PATCH',
        {
          tramoPoblacional,
          estadoFacturacion,
          notaFacturacion: notaFacturacion.trim() === '' ? null : notaFacturacion,
        },
        'No se pudo guardar la información comercial.',
      )
      if (vigente.current) {
        if (actualizado) {
          setMunicipio(actualizado)
          setNotaFacturacion(actualizado.notaFacturacion ?? '')
        }
        setExitoComercial('Se guardó la información comercial.')
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorComercial(
          fallo instanceof Error ? fallo.message : 'No se pudo guardar la información comercial.',
        )
      }
    } finally {
      if (vigente.current) {
        setEnviandoComercial(false)
      }
    }
  }

  // --- Solicitudes de alta/baja de módulo ---

  const [solicitudes, setSolicitudes] = useState<EstadoSolicitudes>({ estado: 'cargando' })
  const [atendiendoId, setAtendiendoId] = useState<number | null>(null)
  const [errorAtender, setErrorAtender] = useState<string | null>(null)

  const errorAtenderRef = useRef<HTMLParagraphElement>(null)

  const cargarSolicitudes = useCallback(async () => {
    try {
      const respuesta = await pedir<SolicitudDeModuloResponse[]>(
        `/api/admin/municipios/${slug}/solicitudes-de-modulo`,
        'No se pudo cargar las solicitudes de alta/baja de módulo.',
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
  }, [slug])

  useEffect(() => {
    // Carga inicial de datos remotos (mismo patrón que PanelDeAuditoria): el
    // setState está protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarSolicitudes()
  }, [cargarSolicitudes])

  useEffect(() => {
    if (errorAtender) {
      errorAtenderRef.current?.focus()
    }
  }, [errorAtender])

  async function atenderSolicitud(id: number) {
    setErrorAtender(null)
    setAtendiendoId(id)
    try {
      const actualizada = await enviar<SolicitudDeModuloResponse>(
        `/api/admin/municipios/${slug}/solicitudes-de-modulo/${id}/atender`,
        'PATCH',
        undefined,
        'No se pudo marcar la solicitud como atendida.',
      )
      if (vigente.current && actualizada) {
        setSolicitudes((actual) =>
          actual.estado === 'listo'
            ? {
                estado: 'listo',
                solicitudes: actual.solicitudes.map((solicitud) =>
                  solicitud.id === actualizada.id ? actualizada : solicitud,
                ),
              }
            : actual,
        )
        setMunicipio((actual) => ({
          ...actual,
          cantidadDeSolicitudesPendientes: Math.max(0, actual.cantidadDeSolicitudesPendientes - 1),
        }))
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setErrorAtender(
          fallo instanceof Error ? fallo.message : 'No se pudo marcar la solicitud como atendida.',
        )
      }
    } finally {
      if (vigente.current) {
        setAtendiendoId(null)
      }
    }
  }

  const idErrorModulos = 'error-modulos'
  const idExitoModulos = 'exito-modulos'
  const idErrorComercial = 'error-comercial'
  const idExitoComercial = 'exito-comercial'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Municipio de {municipio.nombreMunicipio}
      </h1>
      <p className="contenido__bajada">
        <code>{municipio.slug}</code> — estado de aprovisionamiento: {municipio.estado}.
      </p>

      <div className="formulario__acciones">
        <button type="button" className="boton boton--secundario" onClick={onVolver}>
          Volver a la lista
        </button>
      </div>

      <section aria-labelledby="titulo-modulos-municipio">
        <h2 id="titulo-modulos-municipio">Módulos contratados</h2>

        {catalogo.estado === 'cargando' && <p role="status">Cargando el catálogo de módulos…</p>}
        {catalogo.estado === 'error' && <p role="alert">{catalogo.mensaje}</p>}

        {catalogo.estado === 'listo' && (
          <form className="formulario" onSubmit={(evento) => void guardarModulos(evento)}>
            {errorModulos && (
              <p
                className="formulario__error"
                id={idErrorModulos}
                role="alert"
                tabIndex={-1}
                ref={errorModulosRef}
              >
                {errorModulos}
              </p>
            )}
            {exitoModulos && (
              <p id={idExitoModulos} role="status" tabIndex={-1} ref={exitoModulosRef}>
                {exitoModulos}
              </p>
            )}

            <fieldset className="grupo-checkboxes">
              <legend>Módulos del catálogo</legend>
              {catalogo.modulos.map((modulo) => (
                <label key={modulo.codigo} className="grupo-checkboxes__opcion">
                  <input
                    type="checkbox"
                    checked={seleccionados.has(modulo.codigo)}
                    onChange={() => alternarModulo(modulo.codigo)}
                  />
                  {modulo.nombre}
                  {modulo.descripcion && (
                    <span className="campo__ayuda"> — {modulo.descripcion}</span>
                  )}
                </label>
              ))}
            </fieldset>

            <div className="formulario__acciones">
              <button
                type="submit"
                className="boton"
                disabled={enviandoModulos}
                aria-busy={enviandoModulos}
              >
                {enviandoModulos ? 'Guardando…' : 'Guardar módulos'}
              </button>
            </div>
          </form>
        )}
      </section>

      <section aria-labelledby="titulo-comercial">
        <h2 id="titulo-comercial">Información comercial</h2>

        <form className="formulario" onSubmit={(evento) => void guardarComercial(evento)}>
          {errorComercial && (
            <p
              className="formulario__error"
              id={idErrorComercial}
              role="alert"
              tabIndex={-1}
              ref={errorComercialRef}
            >
              {errorComercial}
            </p>
          )}
          {exitoComercial && (
            <p id={idExitoComercial} role="status" tabIndex={-1} ref={exitoComercialRef}>
              {exitoComercial}
            </p>
          )}

          <div className="campo">
            <label htmlFor="tramo-poblacional">Tramo poblacional</label>
            <select
              id="tramo-poblacional"
              value={tramoPoblacional}
              onChange={(evento) => setTramoPoblacional(evento.target.value)}
            >
              {TRAMOS_POBLACIONALES.map((tramo) => (
                <option key={tramo} value={tramo}>
                  {TEXTO_TRAMO_POBLACIONAL[tramo]}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="estado-facturacion">Estado de facturación</label>
            <select
              id="estado-facturacion"
              value={estadoFacturacion}
              onChange={(evento) => setEstadoFacturacion(evento.target.value)}
            >
              {ESTADOS_DE_FACTURACION.map((valor) => (
                <option key={valor} value={valor}>
                  {TEXTO_ESTADO_DE_FACTURACION[valor]}
                </option>
              ))}
            </select>
          </div>

          <div className="campo">
            <label htmlFor="nota-facturacion">Nota de facturación</label>
            <textarea
              id="nota-facturacion"
              aria-describedby="nota-facturacion-ayuda"
              value={notaFacturacion}
              onChange={(evento) => setNotaFacturacion(evento.target.value)}
            />
            <p className="campo__ayuda" id="nota-facturacion-ayuda">
              Opcional. Contexto para otras personas de la plataforma, por
              ejemplo "Tesorería avisó demora, contactado el 20/08".
            </p>
          </div>

          <div className="formulario__acciones">
            <button
              type="submit"
              className="boton"
              disabled={enviandoComercial}
              aria-busy={enviandoComercial}
            >
              {enviandoComercial ? 'Guardando…' : 'Guardar información comercial'}
            </button>
          </div>
        </form>
      </section>

      <section aria-labelledby="titulo-solicitudes-modulo">
        <h2 id="titulo-solicitudes-modulo">Solicitudes de alta/baja de módulo</h2>

        {errorAtender && (
          <p className="formulario__error" role="alert" tabIndex={-1} ref={errorAtenderRef}>
            {errorAtender}
          </p>
        )}

        {solicitudes.estado === 'cargando' && (
          <p role="status">Cargando las solicitudes de alta/baja de módulo…</p>
        )}
        {solicitudes.estado === 'error' && <p role="alert">{solicitudes.mensaje}</p>}

        {solicitudes.estado === 'listo' &&
          (solicitudes.solicitudes.length === 0 ? (
            <p>Este municipio todavía no hizo ninguna solicitud de alta o baja de módulo.</p>
          ) : (
            <div className="tabla-contenedor">
              <table className="tabla">
                <caption>
                  Solicitudes de alta o baja de módulo hechas por este municipio, de la más
                  reciente a la más antigua.
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Módulo</th>
                    <th scope="col">Tipo</th>
                    <th scope="col">Justificación</th>
                    <th scope="col">Estado</th>
                    <th scope="col">Fecha</th>
                    <th scope="col">Acción</th>
                  </tr>
                </thead>
                <tbody>
                  {solicitudes.solicitudes.map((solicitud) => (
                    <tr key={solicitud.id}>
                      <th scope="row">{solicitud.moduloCodigo}</th>
                      <td>{TEXTO_TIPO_DE_SOLICITUD[solicitud.tipo] ?? solicitud.tipo}</td>
                      <td>{solicitud.justificacion}</td>
                      <td>{TEXTO_ESTADO_DE_SOLICITUD[solicitud.estado] ?? solicitud.estado}</td>
                      <td>{FECHA_SOLICITUD.format(new Date(solicitud.creadaEn))}</td>
                      <td>
                        {solicitud.estado === 'PENDIENTE' ? (
                          <button
                            type="button"
                            className="boton boton--secundario"
                            disabled={atendiendoId === solicitud.id}
                            aria-busy={atendiendoId === solicitud.id}
                            onClick={() => void atenderSolicitud(solicitud.id)}
                          >
                            {atendiendoId === solicitud.id ? 'Marcando…' : 'Marcar atendida'}
                          </button>
                        ) : (
                          'Sin acciones'
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ))}
      </section>
    </main>
  )
}
