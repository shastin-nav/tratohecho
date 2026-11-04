// Formatos chilenos. El cálculo de totales NO vive aquí: lo hace el servidor (fuente única de verdad).

const formatoClp = new Intl.NumberFormat('es-CL', { style: 'currency', currency: 'CLP', maximumFractionDigits: 0 })

export const clp = (monto: number): string => formatoClp.format(monto)

export const numero = (valor: number | string): string =>
  new Intl.NumberFormat('es-CL', { maximumFractionDigits: 3 }).format(Number(valor))

export function fecha(iso: string | null | undefined): string {
  if (!iso) return '—'
  const [a, m, d] = iso.slice(0, 10).split('-').map(Number)
  return new Date(a, m - 1, d).toLocaleDateString('es-CL', { day: 'numeric', month: 'short', year: 'numeric' })
}

export const fechaHora = (iso: string): string =>
  new Date(iso).toLocaleString('es-CL', { dateStyle: 'medium', timeStyle: 'short' })

export function hoyIso(): string {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

export function sumarDias(iso: string, dias: number): string {
  const [a, m, d] = iso.split('-').map(Number)
  const r = new Date(a, m - 1, d + dias)
  return `${r.getFullYear()}-${String(r.getMonth() + 1).padStart(2, '0')}-${String(r.getDate()).padStart(2, '0')}`
}

/** Dígito verificador del RUT (módulo 11), igual que el dominio en Kotlin. */
export function dvRut(cuerpo: number): string {
  let suma = 0
  let factor = 2
  for (let resto = cuerpo; resto > 0; resto = Math.floor(resto / 10)) {
    suma += (resto % 10) * factor
    factor = factor === 7 ? 2 : factor + 1
  }
  const dv = 11 - (suma % 11)
  return dv === 11 ? '0' : dv === 10 ? 'K' : String(dv)
}

export function limpiarRut(texto: string): string {
  return texto.replace(/[^0-9kK]/g, '').toUpperCase()
}

export function rutValido(texto: string): boolean {
  const limpio = limpiarRut(texto)
  if (limpio.length < 2) return false
  const cuerpo = limpio.slice(0, -1)
  if (!/^\d+$/.test(cuerpo)) return false
  const n = Number(cuerpo)
  return n >= 1 && n <= 99_999_999 && dvRut(n) === limpio.slice(-1)
}

/** Formatea mientras se escribe: "123456785" → "12.345.678-5". */
export function formatearRut(texto: string): string {
  const limpio = limpiarRut(texto)
  if (limpio.length < 2) return limpio
  const cuerpo = limpio.slice(0, -1).replace(/^0+/, '')
  return `${cuerpo.replace(/\B(?=(\d{3})+(?!\d))/g, '.')}-${limpio.slice(-1)}`
}

/** Precio con IVA → neto, con el mismo redondeo que el backend (HALF_UP). */
export function netoDesdeBruto(bruto: number, tasa = 19): number {
  return Math.round(bruto / (1 + tasa / 100))
}

export function brutoDesdeNeto(neto: number, tasa = 19): number {
  return neto + Math.round((neto * tasa) / 100)
}
