import { useEffect, useRef, useState } from 'react'

import { Login } from './acceso/Login'
import { PanelDeAdministracion } from './acceso/PanelDeAdministracion'
import { useSesion } from './acceso/useSesion'
import { CatalogoDeModulos } from './modulos/CatalogoDeModulos'
import { Navegacion } from './modulos/Navegacion'
import { registroDePantallasDeModulo } from './modulos/registro'
import { useModulos } from './modulos/useModulos'
import { useContacto } from './municipio/useContacto'
import { useTenant } from './tenant/useTenant'
import type { Vista } from './vista'

export default function App() {
  const estado = useTenant()
  const contacto = useContacto()
  const sesion = useSesion()
  const modulos = useModulos()
  const [vista, setVista] = useState<Vista>({ tipo: 'portal' })

  // El título del portal recibe el foco al volver a él desde otra vista,
  // pero no en la primera carga: sin el chequeo contra la vista anterior,
  // la página robaría el foco apenas termina de montar.
  const tituloPortal = useRef<HTMLHeadingElement>(null)
  const vistaAnterior = useRef<Vista['tipo']>(vista.tipo)

  useEffect(() => {
    if (vista.tipo === 'portal' && vistaAnterior.current !== 'portal') {
      tituloPortal.current?.focus()
    }
    vistaAnterior.current = vista.tipo
  }, [vista])

  // Si se navegó a un módulo cuyo código no tiene pantalla registrada (por
  // ejemplo, uno que se apagó y ya no está en el catálogo), no hay nada
  // roto que mostrar: se vuelve al portal.
  useEffect(() => {
    if (vista.tipo === 'modulo' && !(vista.codigo in registroDePantallasDeModulo)) {
      setVista({ tipo: 'portal' })
    }
  }, [vista])

  const usuario = sesion.estado.estado === 'autenticado' ? sesion.estado.usuario : null

  // Volver al portal es consecuencia de haber entrado, así que se hace acá
  // y no sincronizando estado con un efecto: si el ingreso falla, la
  // pantalla tiene que quedarse donde está para mostrar el error.
  async function iniciarYVolverAlPortal(email: string, password: string) {
    await sesion.iniciar(email, password)
    setVista({ tipo: 'portal' })
  }

  // Si cierra sesión estando en la administración, no tiene sentido dejarlo
  // ahí: ya no hay usuario cuyo permiso justifique mostrarla.
  async function cerrarYVolverAlPortal() {
    await sesion.cerrar()
    setVista({ tipo: 'portal' })
  }

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
  const puede = (permiso: string) => usuario?.permisos.includes(permiso) ?? false
  const veAdministracion =
    puede('usuarios.ver') ||
    puede('usuarios.administrar') ||
    puede('roles.ver') ||
    puede('roles.administrar')

  // Mientras el catálogo está cargando, la navegación arranca con la lista
  // vacía (queda solo "Inicio" y, si corresponde, "Administración") en vez
  // de mostrar un estado intermedio que después empuje los ítems.
  const modulosParaNavegacion = modulos.estado === 'listo' ? modulos.modulos : []

  const ComponenteDeModulo = vista.tipo === 'modulo' ? registroDePantallasDeModulo[vista.codigo] : undefined
  const moduloActual =
    vista.tipo === 'modulo' && modulos.estado === 'listo'
      ? modulos.modulos.find((modulo) => modulo.codigo === vista.codigo)
      : undefined

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

          {/* Mientras no se sabe si hay sesión no se muestra nada: mostrar
              "Iniciar sesión" y cambiarlo un instante después mueve los
              controles debajo del puntero y del foco. */}
          {sesion.estado.estado !== 'cargando' && (
            <div className="encabezado__sesion">
              {usuario ? (
                <>
                  <p className="encabezado__usuario">{usuario.nombre}</p>
                  {veAdministracion && (
                    <button
                      type="button"
                      className="boton boton--sobre-primario"
                      onClick={() => setVista({ tipo: 'administracion' })}
                    >
                      Administración
                    </button>
                  )}
                  <button
                    type="button"
                    className="boton boton--sobre-primario"
                    onClick={() => void cerrarYVolverAlPortal()}
                  >
                    Cerrar sesión
                  </button>
                </>
              ) : (
                <button
                  type="button"
                  className="boton boton--sobre-primario"
                  onClick={() => setVista({ tipo: 'ingreso' })}
                >
                  Iniciar sesión
                </button>
              )}
            </div>
          )}
        </div>

        <Navegacion
          modulos={modulosParaNavegacion}
          vista={vista}
          veAdministracion={veAdministracion}
          onIrAPortal={() => setVista({ tipo: 'portal' })}
          onIrAModulo={(codigo) => setVista({ tipo: 'modulo', codigo })}
          onIrAAdministracion={() => setVista({ tipo: 'administracion' })}
        />
      </header>

      {vista.tipo === 'ingreso' ? (
        <Login
          nombreMunicipio={tenant.nombreMunicipio}
          onIniciar={iniciarYVolverAlPortal}
          onVolver={() => setVista({ tipo: 'portal' })}
        />
      ) : vista.tipo === 'administracion' && usuario ? (
        <PanelDeAdministracion usuario={usuario} onVolver={() => setVista({ tipo: 'portal' })} />
      ) : vista.tipo === 'modulo' && ComponenteDeModulo ? (
        <ComponenteDeModulo
          modulo={moduloActual}
          onVolver={() => setVista({ tipo: 'portal' })}
        />
      ) : (
        <main id="contenido" className="contenido">
          <h1 ref={tituloPortal} tabIndex={-1}>
            Portal de {tenant.nombreMunicipio}
          </h1>
          <p className="contenido__bajada">
            Este es el portal digital del municipio. Los trámites, reclamos y
            servicios se van a ir incorporando por etapas.
          </p>

          {usuario && (
            <section aria-labelledby="titulo-escritorio">
              <h2 id="titulo-escritorio">Tu acceso</h2>
              <p className="contenido__nota">
                Entraste como {usuario.nombre} ({usuario.email}) en el
                municipio de {tenant.nombreMunicipio}. Esta sesión no vale en
                el portal de ningún otro municipio.
              </p>
              <dl className="ficha">
                <div className="ficha__fila">
                  <dt>Permisos</dt>
                  <dd>
                    {usuario.permisos.length > 0
                      ? usuario.permisos.join(', ')
                      : 'Sin permisos asignados'}
                  </dd>
                </div>
              </dl>
            </section>
          )}

          {usuario && (
            <CatalogoDeModulos
              estado={modulos}
              onAbrirModulo={(codigo) => setVista({ tipo: 'modulo', codigo })}
            />
          )}

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
                <dt>Portal</dt>
                <dd>
                  <code>{window.location.host}</code>
                </dd>
              </div>
            </dl>
          </section>

          {contacto.estado === 'listo' && (
            <section aria-labelledby="titulo-contacto">
              <h2 id="titulo-contacto">Contacto</h2>
              <p className="contenido__nota">
                Estos datos se leen de la base de datos propia del municipio,
                separada de la de cualquier otro.
              </p>
              <dl className="ficha">
                <div className="ficha__fila">
                  <dt>Dirección</dt>
                  <dd>{contacto.contacto.direccion}</dd>
                </div>
                <div className="ficha__fila">
                  <dt>Teléfono</dt>
                  <dd>
                    <a href={`tel:${contacto.contacto.telefono}`}>
                      {contacto.contacto.telefono}
                    </a>
                  </dd>
                </div>
                <div className="ficha__fila">
                  <dt>Correo electrónico</dt>
                  <dd>
                    <a href={`mailto:${contacto.contacto.email}`}>
                      {contacto.contacto.email}
                    </a>
                  </dd>
                </div>
              </dl>
            </section>
          )}
        </main>
      )}

      <footer className="pie">
        <p>
          Ciudad Digital — plataforma de gestión municipal. Rebanada R4: un
          módulo se prende y se apaga.
        </p>
      </footer>
    </>
  )
}
