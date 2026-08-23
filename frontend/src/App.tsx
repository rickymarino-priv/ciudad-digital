import { useTenant } from './tenant/useTenant'

export default function App() {
  const estado = useTenant()

  if (estado.estado === 'cargando') {
    return (
      <main className="centrado">
        {/* role=status hace que el lector de pantalla anuncie la espera
            sin robar el foco. */}
        <p role="status">Cargando el portal…</p>
      </main>
    )
  }

  if (estado.estado === 'error') {
    return (
      <main className="centrado">
        <h1>No se pudo abrir el portal</h1>
        {/* role=alert interrumpe para avisar del error, que es lo
            apropiado cuando la página no puede continuar. */}
        <p role="alert">{estado.mensaje}</p>
      </main>
    )
  }

  const { tenant } = estado

  return (
    <>
      <a className="salto-al-contenido" href="#contenido">
        Saltar al contenido principal
      </a>

      <header className="encabezado">
        <div className="encabezado__marca">
          {tenant.tema?.logoUrl && (
            // alt vacío a propósito: el nombre del municipio está al lado
            // como texto, y repetirlo obligaría al lector de pantalla a
            // escuchar lo mismo dos veces.
            <img className="encabezado__logo" src={tenant.tema.logoUrl} alt="" />
          )}
          <div>
            <p className="encabezado__sobretitulo">Municipio de</p>
            <p className="encabezado__nombre">{tenant.nombreMunicipio}</p>
          </div>
        </div>
      </header>

      <main id="contenido" className="contenido">
        <h1>Portal de {tenant.nombreMunicipio}</h1>
        <p className="contenido__bajada">
          Este es el portal digital del municipio. Los trámites, reclamos y
          servicios se van a ir incorporando por etapas.
        </p>

        <section aria-labelledby="titulo-estado">
          <h2 id="titulo-estado">Estado de la plataforma</h2>
          <dl className="ficha">
            <div className="ficha__fila">
              <dt>Municipio</dt>
              <dd>{tenant.nombreMunicipio}</dd>
            </div>
            <div className="ficha__fila">
              <dt>Identificador</dt>
              <dd>
                <code>{tenant.slug}</code>
              </dd>
            </div>
            <div className="ficha__fila">
              <dt>Dirección</dt>
              <dd>
                <code>{window.location.host}</code>
              </dd>
            </div>
          </dl>
        </section>
      </main>

      <footer className="pie">
        <p>
          Ciudad Digital — plataforma de gestión municipal. Rebanada R1: dos
          municipios, dos marcas.
        </p>
      </footer>
    </>
  )
}
