import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useParams } from 'react-router'
import { z } from 'zod'
import { api } from '../api/cliente'
import type { Cotizacion, Evento, Medio } from '../api/tipos'
import { Campo, Cargando, EstadoBadge, ETIQUETA_ESTADO, MensajeError, Modal } from '../componentes/Comunes'
import { clp, fecha, fechaHora, hoyIso, numero } from '../lib/formato'
import { TablaTotales } from './CotizacionEditor'

const MEDIOS: Record<Medio, string> = { CORREO: 'Correo electrónico', FIRMA: 'Firma del documento', VERBAL: 'Verbal / teléfono', OTRO: 'Otro' }

export function CotizacionDetalle() {
  const { id } = useParams()
  const navegar = useNavigate()
  const consultas = useQueryClient()
  const [modal, setModal] = useState<'aceptar' | 'rechazar' | null>(null)
  const cotizacion = useQuery({ queryKey: ['cotizacion', id], queryFn: () => api.get<Cotizacion>(`/api/cotizaciones/${id}`) })
  const historial = useQuery({ queryKey: ['cotizacion', id, 'historial'], queryFn: () => api.get<Evento[]>(`/api/cotizaciones/${id}/historial`) })
  const versiones = useQuery({ queryKey: ['cotizacion', id, 'versiones'], queryFn: () => api.get<Cotizacion[]>(`/api/cotizaciones/${id}/versiones`) })

  const refrescar = (c?: Cotizacion) => {
    consultas.invalidateQueries({ queryKey: ['cotizaciones'] })
    consultas.invalidateQueries({ queryKey: ['resumen'] })
    consultas.invalidateQueries({ queryKey: ['cotizacion', id] })
    consultas.invalidateQueries({ queryKey: ['sistema'] })
    if (c && String(c.id) !== id) navegar(`/cotizaciones/${c.id}`)
  }
  const accion = useMutation({
    mutationFn: ({ ruta, cuerpo }: { ruta: string; cuerpo?: unknown }) => api.post<Cotizacion>(`/api/cotizaciones/${id}/${ruta}`, cuerpo),
    onSuccess: (c) => {
      setModal(null)
      refrescar(c)
    },
  })
  const eliminar = useMutation({
    mutationFn: () => api.delete<void>(`/api/cotizaciones/${id}`),
    onSuccess: () => {
      consultas.invalidateQueries({ queryKey: ['cotizaciones'] })
      navegar('/cotizaciones')
    },
  })

  if (cotizacion.isLoading) return <Cargando />
  if (!cotizacion.data) return <MensajeError error={cotizacion.error} />
  const c = cotizacion.data
  const pdf = `/api/cotizaciones/${c.id}/pdf`

  return (
    <section>
      <header className="cabecera-pagina">
        <div>
          <h1>
            Cotización {c.numero} <EstadoBadge estado={c.estado} />
          </h1>
          <p className="subtitulo">
            {c.cliente.razonSocial} · emitida el {fecha(c.fechaEmision)} · válida hasta el {fecha(c.fechaValidez)}
          </p>
        </div>
        <div className="acciones">
          <a className="boton boton-secundario" href={pdf} target="_blank" rel="noreferrer">
            Ver PDF
          </a>
          <a className="boton boton-secundario" href={`${pdf}?descargar=true`}>
            Descargar
          </a>
          {c.estado === 'BORRADOR' && (
            <>
              <Link className="boton boton-secundario" to={`/cotizaciones/${c.id}/editar`}>
                Editar
              </Link>
              <button className="boton" onClick={() => accion.mutate({ ruta: 'enviar' })} disabled={accion.isPending}>
                Marcar como enviada
              </button>
            </>
          )}
          {c.estado === 'ENVIADA' && (
            <>
              <button className="boton boton-secundario" onClick={() => accion.mutate({ ruta: 'nueva-version' })}>
                Modificar (nueva versión)
              </button>
              <button className="boton boton-secundario" onClick={() => setModal('rechazar')}>
                Rechazada
              </button>
              <button className="boton boton-exito" onClick={() => setModal('aceptar')}>
                ¡Trato hecho!
              </button>
            </>
          )}
          {c.estado === 'VENCIDA' && (
            <button className="boton" onClick={() => accion.mutate({ ruta: 'nueva-version' })}>
              Renovar (nueva versión)
            </button>
          )}
          <button className="boton boton-secundario" onClick={() => accion.mutate({ ruta: 'duplicar' })}>
            Duplicar
          </button>
          {c.estado === 'BORRADOR' && c.version === 1 && (
            <button
              className="boton boton-peligro"
              onClick={() => confirm('¿Eliminar este borrador? No se puede deshacer.') && eliminar.mutate()}
            >
              Eliminar
            </button>
          )}
        </div>
      </header>
      <MensajeError error={accion.error || eliminar.error} />
      {c.estado === 'BORRADOR' && (
        <div className="aviso">
          Revisa el PDF y envíalo a tu cliente; luego márcala como enviada. Una vez enviada, la cotización queda fija y cualquier cambio
          se registra como una nueva versión.
        </div>
      )}
      {c.aceptacion && (
        <div className="aviso aviso-exito">
          Aceptada el {fecha(c.aceptacion.fecha)} por <strong>{c.aceptacion.nombre}</strong> ({MEDIOS[c.aceptacion.medio].toLowerCase()})
          {c.aceptacion.detalle ? ` — ${c.aceptacion.detalle}` : ''}.
        </div>
      )}
      {c.motivoRechazo && <div className="aviso">Motivo del rechazo: {c.motivoRechazo}</div>}

      <div className="columnas">
        <div className="panel">
          <h2>Cliente</h2>
          <dl className="datos">
            <dt>Razón social</dt>
            <dd>{c.cliente.razonSocial}</dd>
            <dt>RUT</dt>
            <dd>{c.cliente.rut ?? '—'}</dd>
            <dt>Contacto</dt>
            <dd>{[c.cliente.contacto, c.cliente.telefono, c.cliente.email].filter(Boolean).join(' · ') || '—'}</dd>
            <dt>Dirección</dt>
            <dd>{c.cliente.direccion ?? '—'}</dd>
            <dt>Vendedor</dt>
            <dd>{c.vendedor ?? '—'}</dd>
          </dl>
        </div>
        <div className="panel">
          <h2>Condiciones</h2>
          <dl className="datos">
            <dt>Forma de pago</dt>
            <dd>{c.formaPago ?? '—'}</dd>
            <dt>Plazo de entrega</dt>
            <dd>{c.plazoEntrega ?? '—'}</dd>
            <dt>Garantía</dt>
            <dd>{c.garantia ?? '—'}</dd>
            <dt>Observaciones</dt>
            <dd className="texto">{c.observaciones ?? '—'}</dd>
          </dl>
        </div>
      </div>

      <div className="panel">
        <table className="tabla">
          <thead>
            <tr>
              <th>Descripción</th>
              <th className="num">Cantidad</th>
              <th className="num">Precio neto</th>
              <th className="num">Dcto.</th>
              <th className="num">Total</th>
            </tr>
          </thead>
          <tbody>
            {c.lineas.map((l, i) => (
              <tr key={i}>
                <td>
                  {l.codigo && <span className="codigo">{l.codigo} · </span>}
                  {l.descripcion}
                  {!l.afectaIva && <span className="etiqueta-suave"> exento</span>}
                </td>
                <td className="num">
                  {numero(l.cantidad)} {l.unidad}
                </td>
                <td className="num">{clp(l.precioUnitario)}</td>
                <td className="num">{l.descuento ? `${numero(l.descuento)} %` : ''}</td>
                <td className="num">{clp(c.totales.lineas[i]?.subtotal ?? 0)}</td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="pie-totales">
          <div />
          <TablaTotales totales={c.totales} />
        </div>
      </div>

      <div className="columnas">
        <div className="panel">
          <h2>Historial</h2>
          <ol className="linea-tiempo">
            {historial.data?.map((e, i) => (
              <li key={i}>
                <span className="cuando">{fechaHora(e.ocurridoEn)}</span> <b>{ETIQUETA_ESTADO[e.hacia]}</b>
                {e.detalle ? ` — ${e.detalle}` : ''}
              </li>
            ))}
          </ol>
        </div>
        {versiones.data && versiones.data.length > 1 && (
          <div className="panel">
            <h2>Versiones</h2>
            <ul className="lista-simple">
              {versiones.data.map((v) => (
                <li key={v.id}>
                  {String(v.id) === id ? <b>{v.numero}</b> : <Link to={`/cotizaciones/${v.id}`}>{v.numero}</Link>} ·{' '}
                  <EstadoBadge estado={v.estado} /> · {clp(v.totales.total)}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {modal === 'aceptar' && (
        <Modal titulo="Registrar aceptación" onCerrar={() => setModal(null)}>
          <FormularioAceptacion enviando={accion.isPending} error={accion.error} onAceptar={(cuerpo) => accion.mutate({ ruta: 'aceptar', cuerpo })} />
        </Modal>
      )}
      {modal === 'rechazar' && (
        <Modal titulo="Registrar rechazo" onCerrar={() => setModal(null)}>
          <FormularioRechazo enviando={accion.isPending} onRechazar={(motivo) => accion.mutate({ ruta: 'rechazar', cuerpo: { motivo } })} />
        </Modal>
      )}
    </section>
  )
}

const esquemaAceptacion = z.object({
  nombre: z.string().trim().min(1, 'Indica quién aceptó').max(200),
  medio: z.enum(['CORREO', 'FIRMA', 'VERBAL', 'OTRO']),
  fecha: z.string().min(10),
  detalle: z.string().max(500).optional(),
})

function FormularioAceptacion({
  onAceptar,
  enviando,
  error,
}: {
  onAceptar: (d: z.infer<typeof esquemaAceptacion>) => void
  enviando: boolean
  error: unknown
}) {
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(esquemaAceptacion), defaultValues: { nombre: '', medio: 'CORREO' as Medio, fecha: hoyIso(), detalle: '' } })
  return (
    <form onSubmit={handleSubmit(onAceptar)} className="grilla">
      <p className="ancho-completo campo-ayuda">
        La aceptación debe ser sin modificaciones. Si el cliente pide cambios, crea una nueva versión (es una contraoferta).
      </p>
      <MensajeError error={error} />
      <Campo etiqueta="Nombre de quien aceptó" error={errors.nombre?.message} ancho="completo">
        <input {...register('nombre')} autoFocus />
      </Campo>
      <Campo etiqueta="Medio">
        <select {...register('medio')}>
          {Object.entries(MEDIOS).map(([v, t]) => (
            <option key={v} value={v}>
              {t}
            </option>
          ))}
        </select>
      </Campo>
      <Campo etiqueta="Fecha">
        <input type="date" {...register('fecha')} />
      </Campo>
      <Campo etiqueta="Detalle (opcional)" ancho="completo" ayuda="Ej.: asunto del correo o N° de orden de compra">
        <input {...register('detalle')} />
      </Campo>
      <div className="ancho-completo acciones">
        <button className="boton boton-exito" disabled={enviando}>
          Registrar aceptación
        </button>
      </div>
    </form>
  )
}

function FormularioRechazo({ onRechazar, enviando }: { onRechazar: (motivo: string) => void; enviando: boolean }) {
  const [motivo, setMotivo] = useState('')
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault()
        onRechazar(motivo)
      }}
      className="grilla"
    >
      <Campo etiqueta="Motivo (opcional)" ancho="completo" ayuda="Te ayuda a entender por qué no cierras: precio, plazo, competencia…">
        <textarea rows={3} value={motivo} onChange={(e) => setMotivo(e.target.value)} maxLength={500} autoFocus />
      </Campo>
      <div className="ancho-completo acciones">
        <button className="boton boton-peligro" disabled={enviando}>
          Registrar rechazo
        </button>
      </div>
    </form>
  )
}
