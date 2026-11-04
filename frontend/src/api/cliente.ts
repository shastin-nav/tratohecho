/** Error de la API en formato Problem Details (RFC 9457) con un `codigo` estable. */
export class ErrorApi extends Error {
  constructor(
    public readonly estado: number,
    public readonly codigo: string,
    mensaje: string,
    public readonly errores: { campo: string; mensaje: string }[] = [],
  ) {
    super(mensaje)
  }
}

async function solicitar<T>(metodo: string, url: string, cuerpo?: unknown): Promise<T> {
  const esFormulario = cuerpo instanceof FormData
  const respuesta = await fetch(url, {
    method: metodo,
    credentials: 'same-origin',
    headers: cuerpo === undefined || esFormulario ? undefined : { 'Content-Type': 'application/json' },
    body: cuerpo === undefined ? undefined : esFormulario ? cuerpo : JSON.stringify(cuerpo),
  })
  if (!respuesta.ok) {
    let problema: { codigo?: string; detail?: string; errores?: { campo: string; mensaje: string }[] } = {}
    try {
      problema = await respuesta.json()
    } catch {
      /* respuesta sin cuerpo JSON */
    }
    const errores = problema.errores ?? []
    const detalle = errores.length ? errores.map((e) => `${e.campo}: ${e.mensaje}`).join(' · ') : problema.detail
    throw new ErrorApi(respuesta.status, problema.codigo ?? 'error', detalle ?? `Error ${respuesta.status}`, errores)
  }
  if (respuesta.status === 204) return undefined as T
  return (await respuesta.json()) as T
}

export const api = {
  get: <T>(url: string) => solicitar<T>('GET', url),
  post: <T>(url: string, cuerpo?: unknown) => solicitar<T>('POST', url, cuerpo ?? {}),
  put: <T>(url: string, cuerpo: unknown) => solicitar<T>('PUT', url, cuerpo),
  delete: <T>(url: string) => solicitar<T>('DELETE', url),
}

export function consulta(parametros: Record<string, string | number | boolean | undefined | null>): string {
  const p = new URLSearchParams()
  Object.entries(parametros).forEach(([k, v]) => {
    if (v !== undefined && v !== null && v !== '') p.set(k, String(v))
  })
  const texto = p.toString()
  return texto ? `?${texto}` : ''
}
