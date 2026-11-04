import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// En desarrollo, la API corre en Spring Boot (puerto 8080) con el perfil "dev" (sin token de acceso local).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: { '/api': 'http://127.0.0.1:8080' },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        manualChunks: {
          react: ['react', 'react-dom', 'react-router'],
          formularios: ['@tanstack/react-query', 'react-hook-form', 'zod', '@hookform/resolvers'],
        },
      },
    },
  },
  test: { environment: 'node' },
})
