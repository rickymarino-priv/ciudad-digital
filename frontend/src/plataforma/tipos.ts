/**
 * Formas de respuesta de `/api/admin/municipios/**` (ADR 0019), compartidas
 * entre {@link ./ListaDeMunicipios} y {@link ./DetalleDeMunicipio} para no
 * duplicarlas.
 */
export type MunicipioResponse = {
  slug: string
  nombreMunicipio: string
  subdominio: string
  estado: string
  nombreBaseDatos: string
  versionDeEsquema: string | null
  tramoPoblacional: string
  estadoFacturacion: string
  notaFacturacion: string | null
  cantidadDeModulosContratados: number
}

export type ModuloDeMunicipio = {
  codigo: string
  nombre: string
  descripcion: string | null
  habilitado: boolean
}

export type ModulosDeMunicipioResponse = {
  slug: string
  modulos: ModuloDeMunicipio[]
}

/** Tramos poblacionales válidos (ADR 0019 §1), en el orden en que se listan. */
export const TRAMOS_POBLACIONALES = ['CHICO', 'MEDIANO', 'GRANDE'] as const

export const TEXTO_TRAMO_POBLACIONAL: Record<string, string> = {
  CHICO: 'Chico',
  MEDIANO: 'Mediano',
  GRANDE: 'Grande',
}

/** Estados de facturación válidos (ADR 0019 §1), en el orden en que se listan. */
export const ESTADOS_DE_FACTURACION = ['AL_DIA', 'ATRASADO'] as const

export const TEXTO_ESTADO_DE_FACTURACION: Record<string, string> = {
  AL_DIA: 'Al día',
  ATRASADO: 'Atrasado',
}
