import { useEffect, useState } from 'react'

/** Devuelve `valor` después de `ms` sin cambios (para no llamar a la API en cada tecla). */
export function useDiferido<T>(valor: T, ms = 300): T {
  const [diferido, setDiferido] = useState(valor)
  useEffect(() => {
    const t = setTimeout(() => setDiferido(valor), ms)
    return () => clearTimeout(t)
  }, [valor, ms])
  return diferido
}
