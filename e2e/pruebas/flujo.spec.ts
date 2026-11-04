import { expect, test } from '@playwright/test'
import { TOKEN } from '../playwright.config'

const pesos = (n: number) => new RegExp(`\\$\\s?${n.toLocaleString('es-CL').replace(/\./g, '\\.')}`)

test.describe.configure({ mode: 'serial' })

test('sin el token del lanzador la app no da acceso', async ({ page }) => {
  await page.goto('/')
  await expect(page.getByText('Sesión no iniciada')).toBeVisible()
})

test('de la configuración a una cotización aceptada', async ({ page }) => {
  await page.goto(`/?t=${TOKEN}`)
  await expect(page.getByRole('heading', { name: 'Panel' })).toBeVisible()
  await expect(page).toHaveURL(/\/$/) // el token desaparece de la URL

  // 1. Datos de la empresa
  await page.getByRole('link', { name: 'Configuración' }).click()
  await page.getByLabel('Razón social').fill('Climatización Austral SpA')
  await page.getByRole('textbox', { name: /^RUT/ }).fill('771234569')
  await expect(page.getByRole('textbox', { name: /^RUT/ })).toHaveValue('77.123.456-9')
  await page.getByRole('button', { name: 'Guardar cambios' }).click()
  await expect(page.getByText('Guardado ✓')).toBeVisible()

  // 2. Cliente: el RUT se valida con módulo 11
  await page.getByRole('link', { name: 'Clientes' }).click()
  await page.getByRole('button', { name: 'Nuevo cliente' }).click()
  await page.getByLabel('Nombre o razón social').fill('Panadería Las Delicias Ltda.')
  await page.getByRole('textbox', { name: /^RUT/ }).fill('12.345.678-9')
  await page.getByRole('button', { name: 'Guardar cliente' }).click()
  await expect(page.getByText('RUT inválido')).toBeVisible()
  await page.getByRole('textbox', { name: /^RUT/ }).fill('12.345.678-5')
  await page.getByRole('button', { name: 'Guardar cliente' }).click()
  await expect(page.getByRole('button', { name: 'Panadería Las Delicias Ltda.' })).toBeVisible()

  // 3. Catálogo: precio ingresado con IVA → se guarda el neto
  await page.getByRole('link', { name: 'Catálogo' }).click()
  await page.getByRole('button', { name: 'Nuevo producto o servicio' }).click()
  await page.getByLabel('Nombre del producto o servicio').fill('Equipo split 12.000 BTU')
  await page.getByLabel('Código (opcional)').fill('SPL-12')
  await page.getByLabel('Estoy ingresando el precio con IVA').check()
  await page.getByRole('spinbutton', { name: /Precio con IVA/ }).fill('119000')
  await expect(page.getByText(/Neto \$\s?100\.000/)).toBeVisible()
  await page.getByRole('button', { name: 'Guardar' }).click()
  await expect(page.getByRole('cell', { name: pesos(100000) })).toBeVisible()

  // 4. Cotización: catálogo + línea exenta + descuento global 10 %
  await page.getByRole('link', { name: 'Cotizaciones' }).click()
  await page.getByRole('link', { name: 'Nueva cotización' }).click()
  await page.getByLabel('Buscar cliente').click()
  await page.getByLabel('Buscar cliente').fill('Panader')
  await page.getByRole('option', { name: /Panadería Las Delicias/ }).click()
  await page.getByLabel('Buscar en el catálogo').click()
  await page.getByLabel('Buscar en el catálogo').fill('split')
  await page.locator('.selector-catalogo li[role=option]', { hasText: 'split' }).click()
  await page.getByLabel('Cantidad', { exact: true }).first().fill('2')
  await page.getByRole('button', { name: '+ Agregar línea libre' }).click()
  await page.getByLabel('Descripción línea 2').fill('Capacitación de uso')
  await page.getByLabel('Precio', { exact: true }).nth(1).fill('50000')
  await page.getByLabel('Exento de IVA').nth(1).check()
  await page.locator('select').filter({ hasText: 'Sin descuento' }).selectOption('PORCENTAJE')
  await page.getByLabel('Porcentaje', { exact: true }).fill('10')
  // Afecto 200.000 −10 % = 180.000; exento 50.000 −10 % = 45.000; IVA 34.200; total 259.200
  const totales = page.locator('table.totales')
  await expect(totales.getByRole('row', { name: /Neto/ })).toContainText(pesos(180000))
  await expect(totales.getByRole('row', { name: /Exento/ })).toContainText(pesos(45000))
  await expect(totales.getByRole('row', { name: /IVA/ })).toContainText(pesos(34200))
  await expect(totales.getByRole('row', { name: /Total/ })).toContainText(pesos(259200))
  await page.getByRole('button', { name: 'Guardar borrador' }).click()

  // 5. Enviar, PDF y aceptación
  await expect(page.getByRole('heading', { name: /Cotización N° 1/ })).toBeVisible()
  await expect(page.locator('h1 .badge')).toHaveText('Borrador')
  const pdf = await page.request.get(page.url().replace(/\/cotizaciones\/(\d+)$/, '/api/cotizaciones/$1/pdf'))
  expect(pdf.status()).toBe(200)
  expect(pdf.headers()['content-type']).toContain('application/pdf')
  await page.getByRole('button', { name: 'Marcar como enviada' }).click()
  await expect(page.locator('h1 .badge')).toHaveText('Enviada')
  await page.getByRole('button', { name: '¡Trato hecho!' }).click()
  await page.getByLabel('Nombre de quien aceptó').fill('Rosa Muñoz')
  await page.getByRole('button', { name: 'Registrar aceptación' }).click()
  await expect(page.getByText(/Aceptada el .* por Rosa Muñoz/)).toBeVisible()

  // 6. El panel refleja el cierre
  await page.getByRole('link', { name: 'Panel' }).click()
  await expect(page.locator('.tarjeta-exito')).toContainText(pesos(259200))
})
