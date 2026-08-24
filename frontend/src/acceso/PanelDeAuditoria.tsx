import { useCallback, useEffect, useRef, useState } from 'react'

import { pedir } from './api'

type EventoDeAuditoria = {
  id: number
  ocurridoEn: string
  actorNombre: string
  actorEmail: string
  accion: string
  entidadTipo: string
  entidadId: string
  detalle: string
}

type EstadoLista =
  | { estado: 'cargando' }
  | { estado: 'listo'; eventos: EventoDeAuditoria[] }
  | { estado: 'error'; mensaje: string }

const FECHA = new Intl.DateTimeFormat('es-AR', {
  dateStyle: 'short',
  timeStyle: 'short',
})

/**
 * Registro de auditoría del municipio: quién hizo qué y cuándo.
 *
 * Se muestra a quien tiene {@code auditoria.ver}, pero eso es comodidad: el
 * backend lo verifica igual con `@PreAuthorize` (ADR 0011). Es de solo
 * lectura: el registro lo genera el sistema, no se edita a mano.
 */
export function PanelDeAuditoria() {
  const [estado, setEstado] = useState<EstadoLista>({ estado: 'cargando' })

  // Igual que en PanelDeUsuarios/PanelDeRoles: evita pisar estado de un
  // componente que ya no está montado si el pedido tarda en volver.
  const vigente = useRef(true)
  useEffect(() => {
    vigente.current = true
    return () => {
      vigente.current = false
    }
  }, [])

  const cargarEventos = useCallback(async () => {
    try {
      const eventos = await pedir<EventoDeAuditoria[]>(
        '/api/auditoria',
        'No se pudo cargar el registro de auditoría.',
      )
      if (vigente.current) {
        setEstado({ estado: 'listo', eventos })
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
    // Carga inicial de datos remotos (mismo patrón que PanelDeUsuarios/
    // PanelDeRoles): el setState está protegido por `vigente`, no dispara
    // un loop de renders.
    // eslint-disable-next-line react/set-state-in-effect
    void cargarEventos()
  }, [cargarEventos])

  return (
    <section aria-labelledby="titulo-auditoria">
      <h2 id="titulo-auditoria">Registro de auditoría</h2>

      {estado.estado === 'cargando' && <p role="status">Cargando el registro de auditoría…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <div className="tabla-contenedor">
          <table className="tabla">
            <caption>
              Acciones registradas en este municipio, de la más reciente a la más antigua.
            </caption>
            <thead>
              <tr>
                <th scope="col">Cuándo</th>
                <th scope="col">Quién</th>
                <th scope="col">Acción</th>
                <th scope="col">Sobre qué</th>
                <th scope="col">Detalle</th>
              </tr>
            </thead>
            <tbody>
              {estado.eventos.map((evento) => (
                <tr key={evento.id}>
                  <th scope="row">{FECHA.format(new Date(evento.ocurridoEn))}</th>
                  <td>
                    {evento.actorNombre} ({evento.actorEmail})
                  </td>
                  <td>{evento.accion}</td>
                  <td>
                    {evento.entidadTipo} #{evento.entidadId}
                  </td>
                  <td>{evento.detalle}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  )
}
