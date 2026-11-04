import { describe, expect, it } from 'vitest'
import { brutoDesdeNeto, clp, dvRut, formatearRut, netoDesdeBruto, rutValido } from './formato'

describe('RUT', () => {
  it('calcula el dígito verificador igual que el backend', () => {
    expect(dvRut(12345678)).toBe('5')
    expect(dvRut(6)).toBe('K')
    expect(dvRut(1)).toBe('9')
  })
  it('valida y rechaza', () => {
    expect(rutValido('12.345.678-5')).toBe(true)
    expect(rutValido('76086428-5')).toBe(true)
    expect(rutValido('6-k')).toBe(true)
    expect(rutValido('12.345.678-9')).toBe(false)
    expect(rutValido('')).toBe(false)
  })
  it('formatea mientras se escribe', () => {
    expect(formatearRut('123456785')).toBe('12.345.678-5')
    expect(formatearRut('6k')).toBe('6-K')
  })
})

describe('montos', () => {
  it('formatea pesos chilenos', () => {
    expect(clp(1234567).replace(/\s/g, '')).toBe('$1.234.567')
  })
  it('convierte bruto ↔ neto como el backend', () => {
    expect(netoDesdeBruto(19990)).toBe(16798)
    expect(brutoDesdeNeto(16798)).toBe(19990)
  })
})
