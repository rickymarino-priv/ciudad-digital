/**
 * Vista actual del portal.
 *
 * No hay router todavía (ADR 0008): la vista es estado local en App.tsx.
 * Vive en su propio archivo, y no dentro de App.tsx, para que la
 * navegación y las pantallas de módulo puedan tipar contra ella sin
 * depender de App.tsx (que a su vez las usa a ellas).
 */
export type Vista =
  | { tipo: 'portal' }
  | { tipo: 'ingreso' }
  | { tipo: 'administracion' }
  | { tipo: 'modulo'; codigo: string }
