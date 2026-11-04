import { defineConfig, devices } from '@playwright/test'

const PUERTO = 8099
export const TOKEN = 'token-e2e'

// Pruebas de punta a punta contra el jar real (perfil local, SQLite en una carpeta temporal y acceso con token).
export default defineConfig({
  testDir: './pruebas',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  use: {
    baseURL: `http://127.0.0.1:${PUERTO}`,
    locale: 'es-CL',
    timezoneId: 'America/Santiago',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: [
      'rm -rf build/datos &&',
      'java -jar ../app/build/libs/tratohecho-servidor.jar',
      '--spring.profiles.active=local',
      `--server.port=${PUERTO}`,
      `--tratohecho.acceso-local.token=${TOKEN}`,
      '--tratohecho.datos.directorio=build/datos',
      '--tratohecho.datos.respaldos-automaticos=0',
    ].join(' '),
    url: `http://127.0.0.1:${PUERTO}/actuator/health`,
    timeout: 120_000,
    reuseExistingServer: false,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
