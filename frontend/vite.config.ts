import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: true,
    // Cada municipio se sirve en su propio subdominio (ADR 0004), así que
    // en desarrollo hay que dejar entrar a cualquier *.localhost.
    allowedHosts: ['.localhost'],
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        // Clave: sin changeOrigin el backend recibe el Host original
        // (sanmartin.localhost) y puede resolver el municipio. Si se
        // reescribiera, todos los requests llegarían como "localhost" y
        // ningún tenant resolvería.
        changeOrigin: false,
      },
    },
  },
})
