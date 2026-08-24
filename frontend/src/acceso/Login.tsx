import { useEffect, useRef, useState, type FormEvent } from 'react'

type Props = {
  nombreMunicipio: string
  onIniciar: (email: string, password: string) => Promise<void>
  onVolver: () => void
}

/**
 * Pantalla de ingreso al portal del municipio.
 *
 * El manejo del foco es explícito porque no hay router que lo mueva: sin
 * esto, quien navega con teclado o con lector de pantalla se queda parado
 * donde estaba y no se entera de que cambió la pantalla.
 */
export function Login({ nombreMunicipio, onIniciar, onVolver }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const titulo = useRef<HTMLHeadingElement>(null)
  const avisoDeError = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

  // El error se anuncia con role=alert, y además recibe el foco: así queda
  // al alcance de quien navega con teclado sin tener que buscarlo.
  useEffect(() => {
    if (error) {
      avisoDeError.current?.focus()
    }
  }, [error])

  async function entrar(evento: FormEvent) {
    evento.preventDefault()
    setError(null)
    setEnviando(true)
    try {
      await onIniciar(email, password)
    } catch (fallo: unknown) {
      setError(fallo instanceof Error ? fallo.message : 'No se pudo iniciar sesión.')
    } finally {
      setEnviando(false)
    }
  }

  const idDelError = 'error-de-ingreso'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Iniciar sesión
      </h1>
      <p className="contenido__bajada">
        Ingresá con el usuario que te dio el municipio de {nombreMunicipio}.
      </p>

      <form className="formulario" onSubmit={entrar}>
        {error && (
          <p
            className="formulario__error"
            id={idDelError}
            role="alert"
            tabIndex={-1}
            ref={avisoDeError}
          >
            {error}
          </p>
        )}

        <div className="campo">
          <label htmlFor="email">Correo electrónico</label>
          <input
            id="email"
            name="email"
            type="email"
            // Le dice al gestor de contraseñas del navegador qué es cada
            // campo, que es lo que hace que autocompletar funcione.
            autoComplete="username"
            required
            value={email}
            onChange={(evento) => setEmail(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="password">Contraseña</label>
          <input
            id="password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(evento) => setPassword(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="formulario__acciones">
          <button type="submit" className="boton" disabled={enviando} aria-busy={enviando}>
            {enviando ? 'Entrando…' : 'Entrar'}
          </button>
          <button type="button" className="boton boton--secundario" onClick={onVolver}>
            Volver al portal
          </button>
        </div>
      </form>
    </main>
  )
}
