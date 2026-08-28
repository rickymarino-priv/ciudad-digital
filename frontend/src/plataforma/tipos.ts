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
  cantidadDeSolicitudesPendientes: number
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

/** Pedido de un municipio de alta o baja de un módulo (ADR 0022), tal como lo ve la plataforma. */
export type SolicitudDeModuloResponse = {
  id: number
  moduloCodigo: string
  tipo: string
  justificacion: string
  estado: string
  creadaEn: string
  atendidaEn: string | null
}

export const TEXTO_TIPO_DE_SOLICITUD: Record<string, string> = {
  ALTA: 'Alta',
  BAJA: 'Baja',
}

export const TEXTO_ESTADO_DE_SOLICITUD: Record<string, string> = {
  PENDIENTE: 'Pendiente',
  ATENDIDA: 'Atendida',
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
