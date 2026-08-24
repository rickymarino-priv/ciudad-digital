import { registroDePantallasDeModulo } from './registro'
import type { Modulo } from './useModulos'
import type { Vista } from '../vista'

type Props = {
  /** Catálogo completo de módulos; se filtra acá a los habilitados. */
  modulos: Modulo[]
  vista: Vista
  veAdministracion: boolean
  onIrAPortal: () => void
  onIrAModulo: (codigo: string) => void
  onIrAAdministracion: () => void
}

/**
 * Navegación principal del portal.
 *
 * Se arma a partir del catálogo de módulos habilitados cruzado con el
 * registro local de pantallas (ADR 0012 §7): un módulo apagado en este
 * municipio no aparece, y uno que se prende por la API de administración
 * aparece al recargar. No hay router (ADR 0008): los ítems son botones
 * que cambian el estado `vista` de App.tsx.
 */
export function Navegacion({
  modulos,
  vista,
  veAdministracion,
  onIrAPortal,
  onIrAModulo,
  onIrAAdministracion,
}: Props) {
  const modulosConPantalla = modulos.filter(
    (modulo) => modulo.habilitado && modulo.codigo in registroDePantallasDeModulo,
  )

  return (
    <nav className="navegacion" aria-label="Principal">
      <ul className="navegacion__lista">
        <li>
          <button
            type="button"
            className="navegacion__item"
            aria-current={vista.tipo === 'portal' ? 'page' : undefined}
            onClick={onIrAPortal}
          >
            Inicio
          </button>
        </li>

        {modulosConPantalla.map((modulo) => (
          <li key={modulo.codigo}>
            <button
              type="button"
              className="navegacion__item"
              aria-current={
                vista.tipo === 'modulo' && vista.codigo === modulo.codigo ? 'page' : undefined
              }
              onClick={() => onIrAModulo(modulo.codigo)}
            >
              {modulo.nombre}
            </button>
          </li>
        ))}

        {veAdministracion && (
          <li>
            <button
              type="button"
              className="navegacion__item"
              aria-current={vista.tipo === 'administracion' ? 'page' : undefined}
              onClick={onIrAAdministracion}
            >
              Administración
            </button>
          </li>
        )}
      </ul>
    </nav>
  )
}
