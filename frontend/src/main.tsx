import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { ConsolaDelProveedor } from './plataforma/ConsolaDelProveedor.tsx'
import { esHostDeConsola } from './plataforma/esHostDeConsola.ts'

// La consola del proveedor (ADR 0019) es cross-tenant y se sirve en su
// propio host, sin pasar por la resolución de tenant de App: se decide acá,
// antes de montar nada, en vez de adentro de un componente, porque no tiene
// sentido que ambas raíces convivan detrás del mismo árbol.
const Raiz = esHostDeConsola(window.location.hostname) ? ConsolaDelProveedor : App

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Raiz />
  </StrictMode>,
)
