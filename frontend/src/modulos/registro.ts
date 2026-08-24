import type { ComponentType } from 'react'

import { PantallaDeEjemplo } from './ejemplo/PantallaDeEjemplo'
import type { Modulo } from './useModulos'

export type PropsDePantallaDeModulo = {
  modulo?: Modulo
  onVolver: () => void
}

/**
 * Registro de pantallas de módulo por código, en el frontend.
 *
 * El descriptor de módulo del backend no lleva rutas ni títulos de
 * pantalla (ADR 0012 §7): el backend no conoce la navegación del
 * frontend. Acá se decide qué módulos habilitados tienen una pantalla
 * para mostrar; un módulo habilitado sin entrada acá no aparece en la
 * navegación ni en los botones "Abrir de todos modos" del catálogo.
 */
export const registroDePantallasDeModulo: Record<string, ComponentType<PropsDePantallaDeModulo>> = {
  ejemplo: PantallaDeEjemplo,
}
