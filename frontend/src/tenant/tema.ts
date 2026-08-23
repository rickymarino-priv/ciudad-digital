/**
 * Identidad visual del municipio, servida por el backend según el
 * subdominio del request (ADR 0006).
 */
export type Tema = {
  colorPrimario: string
  colorPrimarioContraste: string
  colorAcento: string
  colorFondo: string
  colorSuperficie: string
  colorTexto: string
  colorTextoTenue: string
  tipografia: string
  logoUrl: string
}

export type TenantTema = {
  slug: string
  nombreMunicipio: string
  tema: Tema | null
}

/**
 * Mapeo explícito de cada campo del tema a su custom property de CSS.
 *
 * Se listan uno por uno a propósito: así el conjunto de tokens que la hoja
 * de estilos puede usar está escrito en un solo lugar, y agregar un color
 * nuevo obliga a decidir su nombre en vez de que aparezca solo.
 */
const TOKENS: Record<keyof Tema, string> = {
  colorPrimario: '--color-primario',
  colorPrimarioContraste: '--color-primario-contraste',
  colorAcento: '--color-acento',
  colorFondo: '--color-fondo',
  colorSuperficie: '--color-superficie',
  colorTexto: '--color-texto',
  colorTextoTenue: '--color-texto-tenue',
  tipografia: '--tipografia',
  logoUrl: '--logo-url',
}

/** Campos que son colores o tipografía; el logo no va como token CSS. */
const APLICABLES = (Object.keys(TOKENS) as (keyof Tema)[]).filter(
  (campo) => campo !== 'logoUrl',
)

export function aplicarTema(tema: Tema): void {
  const raiz = document.documentElement
  for (const campo of APLICABLES) {
    const valor = tema[campo]
    if (valor) {
      raiz.style.setProperty(TOKENS[campo], valor)
    }
  }
}
