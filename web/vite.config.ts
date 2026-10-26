import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'
import { VitePWA } from 'vite-plugin-pwa'

// PWA: se puede "instalar" en Android, iPhone (Agregar a inicio) y computadora, y abre sin internet.
export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate', // cuando publicamos una versión nueva, se actualiza sola
      includeAssets: ['favicon.png', 'apple-touch-icon.png'],
      manifest: {
        name: 'MiCarrera',
        short_name: 'MiCarrera',
        description: 'Tu pensum y tu horario, en orden.',
        lang: 'es-DO',
        start_url: '/',
        display: 'standalone',
        background_color: '#12132a',
        theme_color: '#12132a',
        icons: [
          { src: 'pwa-192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        importScripts: ['avisos-sw.js'], // avisos de clase y entregas (Web Push), ver src/avisos.ts
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/privacidad/, /^\/terminos/, /^\/descargas\//], // páginas sueltas y el APK de Android
      },
    }),
  ],
})
