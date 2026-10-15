import { defineConfig, type Plugin } from 'vite'
import react from '@vitejs/plugin-react'

/** En développement, les routes /station/* servent station.html (comme le SpaController en production). */
const stationRoutes: Plugin = {
  name: 'station-routes',
  configureServer(server) {
    server.middlewares.use((req: { url?: string }, _res: unknown, next: () => void) => {
      if (req.url && /^\/station(\/|$|\?)/.test(req.url) && !req.url.includes('.')) {
        req.url = '/station.html'
      }
      next()
    })
  },
}

// En développement, /api est relayé vers le backend Spring Boot.
export default defineConfig({
  plugins: [react(), stationRoutes],
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8080',
      '/img': 'http://localhost:8080',
    },
  },
  build: {
    // Le build est servi directement par Spring Boot (src/main/resources/static)
    outDir: '../backend/src/main/resources/static',
    emptyOutDir: true,
    // Deux applications : le Center (index.html) et le logiciel Station (station.html)
    rollupOptions: {
      input: { main: 'index.html', station: 'station.html' },
    },
  },
})
