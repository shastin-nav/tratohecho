import { useEffect, useRef, type ReactNode } from 'react'
import type { Estado } from '../api/tipos'
import { ErrorApi } from '../api/cliente'

export const ETIQUETA_ESTADO: Record<Estado, string> = {
  BORRADOR: 'Borrador',
  ENVIADA: 'Enviada',
  ACEPTADA: 'Aceptada',
  RECHAZADA: 'Rechazada',
  VENCIDA: 'Vencida',
  REEMPLAZADA: 'Reemplazada',
}

export function EstadoBadge({ estado }: { estado: Estado }) {
  return <span className={`badge badge-${estado.toLowerCase()}`}>{ETIQUETA_ESTADO[estado]}</span>
}

export function Cargando({ texto = 'Cargando…' }: { texto?: string }) {
  return (
    <div className="cargando" role="status">
      {texto}
    </div>
  )
}

export function MensajeError({ error }: { error: unknown }) {
  if (!error) return null
  const texto = error instanceof ErrorApi || error instanceof Error ? error.message : 'Ocurrió un error inesperado'
  return (
    <div className="aviso aviso-error" role="alert">
      {texto}
    </div>
  )
}

export function Vacio({ children }: { children: ReactNode }) {
  return <div className="vacio">{children}</div>
}

export function Paginador({
  pagina,
  tamano,
  total,
  onCambiar,
}: {
  pagina: number
  tamano: number
  total: number
  onCambiar: (p: number) => void
}) {
  const paginas = Math.max(1, Math.ceil(total / tamano))
  if (paginas <= 1) return null
  return (
    <nav className="paginador" aria-label="Paginación">
      <button className="boton boton-secundario" disabled={pagina === 0} onClick={() => onCambiar(pagina - 1)}>
        Anterior
      </button>
      <span>
        Página {pagina + 1} de {paginas}
      </span>
      <button className="boton boton-secundario" disabled={pagina + 1 >= paginas} onClick={() => onCambiar(pagina + 1)}>
        Siguiente
      </button>
    </nav>
  )
}

export function Modal({ titulo, onCerrar, children }: { titulo: string; onCerrar: () => void; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null)
  useEffect(() => {
    const dialogo = ref.current
    dialogo?.showModal()
    return () => dialogo?.close()
  }, [])
  return (
    <dialog ref={ref} className="modal" onCancel={onCerrar} aria-label={titulo}>
      <header className="modal-cabecera">
        <h2>{titulo}</h2>
        <button className="boton-icono" onClick={onCerrar} aria-label="Cerrar">
          ×
        </button>
      </header>
      <div className="modal-cuerpo">{children}</div>
    </dialog>
  )
}

export function Campo({
  etiqueta,
  error,
  ayuda,
  children,
  ancho,
}: {
  etiqueta: string
  error?: string
  ayuda?: string
  children: ReactNode
  ancho?: 'completo' | 'medio' | 'tercio'
}) {
  return (
    <label className={`campo ${ancho ? `campo-${ancho}` : ''}`}>
      <span className="campo-etiqueta">{etiqueta}</span>
      {children}
      {error ? <span className="campo-error">{error}</span> : ayuda ? <span className="campo-ayuda">{ayuda}</span> : null}
    </label>
  )
}
