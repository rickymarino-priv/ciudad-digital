import { useEffect, useRef, useState } from 'react'

import { pedir } from '../acceso/api'
import { TEXTO_ESTADO_DE_FACTURACION, TEXTO_TRAMO_POBLACIONAL, type MunicipioResponse } from './tipos'

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; municipios: MunicipioResponse[] }
  | { estado: 'error'; mensaje: string }

type Props = {
  onVerDetalle: (municipio: MunicipioResponse) => void
}

/**
 * Lista de todos los municipios dados de alta, cross-tenant (ADR 0019).
 *
 * Es la única pantalla del producto pensada para ver todos los municipios
 * a la vez: acá "aislamiento entre tenants" no aplica, el criterio de
 * seguridad es "quién puede llegar a esta vista" (solo sesión de
 * plataforma), no "un municipio no ve los datos de otro".
 */
export function ListaDeMunicipios({ onVerDetalle }: Props) {
  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })

  // Mismo patrón que useSesion: la bandera de "sigo montado" vive en el
  // cierre del propio efecto, no hace falta un ref aparte porque este pedido
  // no se repite desde ningún otro lugar del componente.
  useEffect(() => {
    let vigente = true

    pedir<MunicipioResponse[]>('/api/admin/municipios', 'No se pudo cargar la lista de municipios.')
      .then((municipios) => {
        if (vigente) {
          setEstado({ estado: 'listo', municipios })
        }
      })
      .catch((fallo: unknown) => {
        if (vigente) {
          setEstado({
            estado: 'error',
            mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
          })
        }
      })

    return () => {
      vigente = false
    }
  }, [])

  const titulo = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    titulo.current?.focus()
  }, [])

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Municipios
      </h1>
      <p className="contenido__bajada">
        Todos los municipios dados de alta en la plataforma, con su estado de
        aprovisionamiento y su contrato.
      </p>

      {estado.estado === 'cargando' && <p role="status">Cargando los municipios…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>Municipios dados de alta en la plataforma.</caption>
            <thead>
              <tr>
                <th scope="col">Municipio</th>
                <th scope="col">Estado</th>
                <th scope="col">Tramo poblacional</th>
                <th scope="col">Estado de facturación</th>
                <th scope="col">Módulos contratados</th>
                <th scope="col">Solicitudes pendientes</th>
                <th scope="col">Acción</th>
              </tr>
            </thead>
            <tbody>
              {estado.municipios.map((municipio) => (
                <tr key={municipio.slug}>
                  <th scope="row">
                    {municipio.nombreMunicipio} <code>{municipio.slug}</code>
                  </th>
                  <td>{municipio.estado}</td>
                  <td>
                    {TEXTO_TRAMO_POBLACIONAL[municipio.tramoPoblacional] ??
                      municipio.tramoPoblacional}
                  </td>
                  <td>
                    {/* El texto ya distingue "Atrasado" de "Al día"; el
                        badge de atención es un refuerzo visual, nunca el
                        único canal (WCAG 1.4.1). */}
                    {municipio.estadoFacturacion === 'ATRASADO' ? (
                      <span className="badge badge--atencion">
                        {TEXTO_ESTADO_DE_FACTURACION.ATRASADO}
                      </span>
                    ) : (
                      TEXTO_ESTADO_DE_FACTURACION[municipio.estadoFacturacion] ??
                      municipio.estadoFacturacion
                    )}
                  </td>
                  <td>{municipio.cantidadDeModulosContratados}</td>
                  <td>{municipio.cantidadDeSolicitudesPendientes}</td>
                  <td>
                    <button
                      type="button"
                      className="boton boton--secundario"
                      onClick={() => onVerDetalle(municipio)}
                    >
                      Ver detalle
                    </button>
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
