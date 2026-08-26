import { useState } from 'react'

import { DetalleDeMunicipio } from './DetalleDeMunicipio'
import { LoginDePlataforma } from './LoginDePlataforma'
import { ListaDeMunicipios } from './ListaDeMunicipios'
import type { MunicipioResponse } from './tipos'
import { useSesionDePlataforma } from './useSesionDePlataforma'

/**
 * Vista actual de la consola.
 *
 * No hay router (ADR 0008): la navegación lista↔detalle es estado local,
 * mismo criterio que `Vista` en `App.tsx`/`vista.ts`, pero un tipo propio de
 * este módulo — la consola no comparte pantallas con el portal municipal.
 */
type VistaDeConsola = { tipo: 'lista' } | { tipo: 'detalle'; slug: string }

/**
 * Componente raíz de la consola del proveedor (ADR 0019).
 *
 * Análogo a `App.tsx` pero sin tenant: no pasa por `useTenant()`, no hay
 * municipio que resolver y no hay tema que aplicar — el encabezado es fijo,
 * sin logo ni colores de marca.
 */
export function ConsolaDelProveedor() {
  const sesion = useSesionDePlataforma()
  const [vista, setVista] = useState<VistaDeConsola>({ tipo: 'lista' })
  // Datos del municipio que se está viendo en detalle, pasados desde la
  // fila de la lista: evita repetir el `GET /api/admin/municipios` completo
  // solo para leer una fila, ya que el backend no tiene un endpoint de "un
  // solo municipio".
  const [municipioEnDetalle, setMunicipioEnDetalle] = useState<MunicipioResponse | null>(null)

  const usuario = sesion.estado.estado === 'autenticado' ? sesion.estado.usuario : null

  function irADetalle(municipio: MunicipioResponse) {
    setMunicipioEnDetalle(municipio)
    setVista({ tipo: 'detalle', slug: municipio.slug })
  }

  function volverALaLista() {
    setVista({ tipo: 'lista' })
    setMunicipioEnDetalle(null)
  }

  async function cerrarSesion() {
    await sesion.cerrar()
    volverALaLista()
  }

  return (
    <>
      <a className="salto-al-contenido" href="#contenido">
        Saltar al contenido principal
      </a>

      <header className="encabezado">
        <div className="encabezado__marca">
          <div>
            <p className="encabezado__nombre">Consola del proveedor — Ciudad Digital</p>
          </div>

          {sesion.estado.estado !== 'cargando' && usuario && (
            <div className="encabezado__sesion">
              <p className="encabezado__usuario">{usuario.nombre}</p>
              <button
                type="button"
                className="boton boton--sobre-primario"
                onClick={() => void cerrarSesion()}
              >
                Cerrar sesión
              </button>
            </div>
          )}
        </div>
      </header>

      {sesion.estado.estado === 'cargando' && (
        <main className="centrado">
          <p role="status">Cargando…</p>
        </main>
      )}

      {sesion.estado.estado === 'anonimo' && (
        <LoginDePlataforma onIniciar={sesion.iniciar} />
      )}

      {sesion.estado.estado === 'autenticado' && vista.tipo === 'lista' && (
        <ListaDeMunicipios onVerDetalle={irADetalle} />
      )}

      {sesion.estado.estado === 'autenticado' && vista.tipo === 'detalle' && municipioEnDetalle && (
        <DetalleDeMunicipio
          slug={vista.slug}
          municipioInicial={municipioEnDetalle}
          onVolver={volverALaLista}
        />
      )}
    </>
  )
}
