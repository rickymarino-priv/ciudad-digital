import { useCallback, useEffect, useRef, useState } from 'react'

import { pedir } from './api'

type PuntoDeMetrica = { etiqueta: string; cantidad: number }
type SerieDeMetricas = { nombre: string; puntos: PuntoDeMetrica[] }
type FuenteDeMetricas = { moduloCodigo: string; moduloNombre: string; series: SerieDeMetricas[] }

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; fuentes: FuenteDeMetricas[] }
  | { estado: 'error'; mensaje: string }

/**
 * Tablero de indicadores agregados de los módulos operativos del municipio.
 *
 * Se muestra a quien tiene {@code reportes.ver}, pero eso es comodidad: el
 * backend lo verifica igual con `@PreAuthorize` (ADR 0011). Cada fuente que
 * devuelve el backend corresponde a un módulo contratado por este municipio
 * (ADR 0033 §4): no hay nada que filtrar acá, solo mostrar.
 */
export function PanelDeReportes() {
  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })

  // Igual que en PanelDeAuditoria: evita pisar estado de un componente que
  // ya no está montado si el pedido tarda en volver.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarTablero = useCallback(async () => {
    try {
      const fuentes = await pedir<FuenteDeMetricas[]>(
        '/api/reportes/tablero',
        'No se pudo cargar el tablero de reportes.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', fuentes })
      }
    } catch (fallo: unknown) {
      if (vigente.current) {
        setEstado({
          estado: 'error',
          mensaje: fallo instanceof Error ? fallo.message : 'Error inesperado.',
        })
      }
    }
  }, [])

  useEffect(() => {
    // Carga inicial de datos remotos (mismo patrón que PanelDeAuditoria): el
    // setState está protegido por `vigente`, no dispara un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarTablero()
  }, [cargarTablero])

  return (
    <section aria-labelledby="titulo-reportes">
      <h2 id="titulo-reportes">Reportes</h2>

      {estado.estado === 'cargando' && (
        <p role="status">Cargando el tablero de reportes…</p>
      )}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && estado.fuentes.length === 0 && (
        <p role="status">
          No hay indicadores disponibles: el municipio no tiene contratado ningún módulo con datos
          para mostrar.
        </p>
      )}

      {estado.estado === 'listo' &&
        estado.fuentes.map((fuente) => (
          <div key={fuente.moduloCodigo}>
            <h3>{fuente.moduloNombre}</h3>
            {fuente.series.map((serie) => (
              <div className="tabla-contenedor" key={`${fuente.moduloCodigo}-${serie.nombre}`}>
                <table className="tabla">
                  <caption>{serie.nombre}</caption>
                  <thead>
                    <tr>
                      <th scope="col">Categoría</th>
                      <th scope="col">Cantidad</th>
                    </tr>
                  </thead>
                  <tbody>
                    {serie.puntos.map((punto) => (
                      <tr key={`${fuente.moduloCodigo}-${serie.nombre}-${punto.etiqueta}`}>
                        <th scope="row">{punto.etiqueta}</th>
                        <td>{punto.cantidad}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ))}
          </div>
        ))}
    </section>
  )
}
