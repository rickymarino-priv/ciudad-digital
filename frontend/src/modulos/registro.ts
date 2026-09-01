import type { ComponentType } from 'react'

import { PantallaDeArbolado } from './arbolado/PantallaDeArbolado'
import { PantallaDeBoletin } from './boletin/PantallaDeBoletin'
import { PantallaDeCementerio } from './cementerio/PantallaDeCementerio'
import { PantallaDeDesarrolloSocial } from './desarrollosocial/PantallaDeDesarrolloSocial'
import { PantallaDeEducacion } from './educacion/PantallaDeEducacion'
import { PantallaDeEjemplo } from './ejemplo/PantallaDeEjemplo'
import { PantallaDeEspaciosVerdes } from './espaciosverdes/PantallaDeEspaciosVerdes'
import { PantallaDeEventos } from './eventos/PantallaDeEventos'
import { PantallaDeMesaDeEntradas } from './mesaentradas/PantallaDeMesaDeEntradas'
import { PantallaDeMultas } from './multas/PantallaDeMultas'
import { PantallaDeObras } from './obras/PantallaDeObras'
import { PantallaDePrensa } from './prensa/PantallaDePrensa'
import { PantallaDeProveedores } from './proveedores/PantallaDeProveedores'
import { PantallaDeReclamos } from './reclamos/PantallaDeReclamos'
import { PantallaDeTasas } from './tasas/PantallaDeTasas'
import { PantallaDeTransparencia } from './transparencia/PantallaDeTransparencia'
import { PantallaDeTurnos } from './turnos/PantallaDeTurnos'
import type { Modulo } from './useModulos'
import type { Usuario } from '../acceso/useSesion'

export type PropsDePantallaDeModulo = {
  modulo?: Modulo
  /** Usuario con sesión iniciada en este municipio, o `null` si es anónimo. */
  usuario: Usuario | null
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
  reclamos: PantallaDeReclamos,
  boletin: PantallaDeBoletin,
  cementerio: PantallaDeCementerio,
  mesaentradas: PantallaDeMesaDeEntradas,
  transparencia: PantallaDeTransparencia,
  tasas: PantallaDeTasas,
  proveedores: PantallaDeProveedores,
  multas: PantallaDeMultas,
  obras: PantallaDeObras,
  arbolado: PantallaDeArbolado,
  espaciosverdes: PantallaDeEspaciosVerdes,
  eventos: PantallaDeEventos,
  desarrollosocial: PantallaDeDesarrolloSocial,
  turnos: PantallaDeTurnos,
  prensa: PantallaDePrensa,
  educacion: PantallaDeEducacion,
}
