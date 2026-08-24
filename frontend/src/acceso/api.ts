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

/** Mensaje de error que mandó el backend, o uno genérico si no mandó ninguno. */
async function mensajeDeError(respuesta: Response, porDefecto: string): Promise<string> {
  try {
    const cuerpo = (await respuesta.json()) as { error?: string }
    return cuerpo.error ?? porDefecto
  } catch {
    return porDefecto
  }
}

export async function pedir<T>(ruta: string, porDefecto: string): Promise<T> {
  const respuesta = await fetch(ruta)
  if (!respuesta.ok) {
    throw new Error(await mensajeDeError(respuesta, porDefecto))
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
    throw new Error(await mensajeDeError(respuesta, porDefecto))
  }
  if (respuesta.status === 204) {
    return null
  }
  return (await respuesta.json()) as T
}
