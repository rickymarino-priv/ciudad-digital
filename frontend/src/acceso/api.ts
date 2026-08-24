/**
 * Llamadas a la API que pueden cambiar datos.
 *
 * El backend protege la sesión con un token CSRF que deja en una cookie
 * legible desde JavaScript, y espera ese mismo valor en una cabecera. La
 * cookie de sesión, en cambio, es HttpOnly: no se puede leer desde acá, y
 * eso es a propósito (ADR 0010).
 */

const COOKIE_CSRF = 'XSRF-TOKEN'
const CABECERA_CSRF = 'X-XSRF-TOKEN'

function tokenCsrf(): string | null {
  const prefijo = `${COOKIE_CSRF}=`
  const cookie = document.cookie
    .split('; ')
    .find((candidata) => candidata.startsWith(prefijo))

  return cookie ? decodeURIComponent(cookie.slice(prefijo.length)) : null
}

/**
 * Error de una llamada a la API.
 *
 * Además del mensaje, conserva `codigo` y `modulo` cuando el backend los
 * manda (por ejemplo `MODULO_NO_CONTRATADO`, ADR 0012 §5): sin esto, quien
 * llama solo puede mostrar el texto del error, no distinguir por qué pasó
 * para decidir qué mostrar.
 */
export class ErrorDeApi extends Error {
  codigo?: string
  modulo?: string

  constructor(mensaje: string, codigo?: string, modulo?: string) {
    super(mensaje)
    this.name = 'ErrorDeApi'
    this.codigo = codigo
    this.modulo = modulo
  }
}

/** Error que mandó el backend, o uno genérico si no mandó ninguno. */
async function errorDeApi(respuesta: Response, porDefecto: string): Promise<ErrorDeApi> {
  try {
    const cuerpo = (await respuesta.json()) as { error?: string; codigo?: string; modulo?: string }
    return new ErrorDeApi(cuerpo.error ?? porDefecto, cuerpo.codigo, cuerpo.modulo)
  } catch {
    return new ErrorDeApi(porDefecto)
  }
}

export async function pedir<T>(ruta: string, porDefecto: string): Promise<T> {
  const respuesta = await fetch(ruta)
  if (!respuesta.ok) {
    throw await errorDeApi(respuesta, porDefecto)
  }
  return (await respuesta.json()) as T
}

export async function enviar<T>(
  ruta: string,
  metodo: 'POST' | 'PUT' | 'PATCH' | 'DELETE',
  cuerpo: unknown,
  porDefecto: string,
): Promise<T | null> {
  const token = tokenCsrf()

  const respuesta = await fetch(ruta, {
    method: metodo,
    headers: {
      ...(cuerpo === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { [CABECERA_CSRF]: token } : {}),
    },
    body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
  })

  if (!respuesta.ok) {
    throw await errorDeApi(respuesta, porDefecto)
  }
  if (respuesta.status === 204) {
    return null
  }
  return (await respuesta.json()) as T
}
