import { useEffect, useRef, useState, type FormEvent } from 'react'

type Props = {
  onIniciar: (email: string, password: string) => Promise<void>
}

/**
 * Pantalla de ingreso a la consola del proveedor.
 *
 * Mismo patrón que {@link ../acceso/Login}, sin `nombreMunicipio` (no hay
 * municipio acá) y sin botón "Volver": no hay ningún portal público al que
 * volver desde la consola.
 */
export function LoginDePlataforma({ onIniciar }: Props) {
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [enviando, setEnviando] = useState(false)

  const titulo = useRef<HTMLHeadingElement>(null)
  const avisoDeError = useRef<HTMLParagraphElement>(null)

  useEffect(() => {
    titulo.current?.focus()
  }, [])

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

  const idDelError = 'error-de-ingreso-plataforma'

  return (
    <main id="contenido" className="contenido">
      <h1 ref={titulo} tabIndex={-1}>
        Consola del proveedor
      </h1>
      <p className="contenido__bajada">
        Ingresá con tu usuario de plataforma para operar la consola del
        proveedor.
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
          <label htmlFor="email-plataforma">Correo electrónico</label>
          <input
            id="email-plataforma"
            name="email"
            type="email"
            autoComplete="username"
            required
            value={email}
            onChange={(evento) => setEmail(evento.target.value)}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? idDelError : undefined}
          />
        </div>

        <div className="campo">
          <label htmlFor="password-plataforma">Contraseña</label>
          <input
            id="password-plataforma"
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
        </div>
      </form>
    </main>
  )
}
