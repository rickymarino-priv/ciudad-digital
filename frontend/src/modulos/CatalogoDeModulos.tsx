import { registroDePantallasDeModulo } from './registro'
import type { EstadoModulos } from './useModulos'

type Props = {
  estado: EstadoModulos
  onAbrirModulo: (codigo: string) => void
}

/**
 * Catálogo completo de módulos del producto, con su estado en este
 * municipio, para el usuario autenticado.
 *
 * Esta es la sección que hace visible que ocultar módulos en la
 * navegación es solo comodidad de experiencia, no enforcement (ADR 0009):
 * muestra "Contratado" / "No contratado" en texto —nunca solo color— y,
 * en los módulos no contratados que tienen pantalla registrada, un botón
 * que igual navega ahí. La pantalla llama a la API y recibe el 403
 * `MODULO_NO_CONTRATADO`: el rechazo real vive en el backend.
 */
export function CatalogoDeModulos({ estado, onAbrirModulo }: Props) {
  return (
    <section aria-labelledby="titulo-modulos">
      <h2 id="titulo-modulos">Módulos</h2>
      <p className="contenido__nota">
        Catálogo completo de módulos del producto y su estado en este
        municipio. Un módulo no contratado no aparece en la navegación,
        pero eso es comodidad: la pantalla se puede abrir igual, y la API
        rechaza el pedido.
      </p>

      {estado.estado === 'cargando' && <p role="status">Cargando el catálogo de módulos…</p>}
      {estado.estado === 'error' && <p role="alert">{estado.mensaje}</p>}

      {estado.estado === 'listo' && (
        <ul className="lista-modulos">
          {estado.modulos.map((modulo) => {
            const tienePantalla = modulo.codigo in registroDePantallasDeModulo

            return (
              <li key={modulo.codigo} className="tarjeta-modulo">
                <div className="tarjeta-modulo__encabezado">
                  <h3>{modulo.nombre}</h3>
                  <span className={modulo.habilitado ? 'badge' : 'badge badge--atencion'}>
                    {modulo.habilitado ? 'Contratado' : 'No contratado'}
                  </span>
                </div>

                {modulo.descripcion && <p className="contenido__nota">{modulo.descripcion}</p>}

                {!modulo.habilitado && tienePantalla && (
                  <button
                    type="button"
                    className="boton boton--secundario"
                    onClick={() => onAbrirModulo(modulo.codigo)}
                  >
                    Abrir de todos modos
                  </button>
                )}
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
