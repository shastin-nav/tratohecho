import { z } from 'zod'

// La CSP de la app no permite eval: zod usa su validador sin compilación JIT (evita el intento bloqueado de new Function).
// Este módulo se importa primero en main.tsx para que la configuración aplique antes de crear cualquier esquema.
z.config({ jitless: true })
