import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { api } from '../api/cliente'
import type { Cotizacion, Estado, Pagina, ResumenEstado } from '../api/tipos'
import { Cargando, EstadoBadge, ETIQUETA_ESTADO, MensajeError, Vacio } from '../componentes/Comunes'
import { clp, fecha } from '../lib/formato'

const ORDEN: Estado[] = ['BORRADOR', 'ENVIADA', 'ACEPTADA', 'RECHAZADA', 'VENCIDA']

export function Panel() {
  const resumen = useQuery({ queryKey: ['resumen'], queryFn: () => api.get<ResumenEstado[]>('/api/cotizaciones/resumen') })
  const recientes = useQuery({
    queryKey: ['cotizaciones', 'recientes'],
    queryFn: () => api.get<Pagina<Cotizacion>>('/api/cotizaciones?tamano=8'),
  })

  const porEstado = new Map(resumen.data?.map((r) => [r.estado, r]))
  const enviadas = porEstado.get('ENVIADA')
  const aceptadas = porEstado.get('ACEPTADA')
  const rechazadas = porEstado.get('RECHAZADA')
  const decididas = (aceptadas?.cantidad ?? 0) + (rechazadas?.cantidad ?? 0)
  const tasa = decididas ? Math.round(((aceptadas?.cantidad ?? 0) / decididas) * 100) : null

  return (
    <section>
      <header className="cabecera-pagina">
        <h1>Panel</h1>
        <Link className="boton" to="/cotizaciones/nueva">
          Nueva cotización
        </Link>
      </header>
      <MensajeError error={resumen.error || recientes.error} />
      <div className="tarjetas">
        <div className="tarjeta">
          <span className="tarjeta-etiqueta">Por responder</span>
          <strong>{clp(enviadas?.total ?? 0)}</strong>
          <span>{enviadas?.cantidad ?? 0} cotizaciones enviadas</span>
        </div>
        <div className="tarjeta tarjeta-exito">
          <span className="tarjeta-etiqueta">Cerradas</span>
          <strong>{clp(aceptadas?.total ?? 0)}</strong>
          <span>{aceptadas?.cantidad ?? 0} aceptadas</span>
        </div>
        <div className="tarjeta">
          <span className="tarjeta-etiqueta">Tasa de cierre</span>
          <strong>{tasa === null ? '—' : `${tasa} %`}</strong>
          <span>aceptadas / (aceptadas + rechazadas)</span>
        </div>
      </div>
      <div className="chips">
        {ORDEN.map((e) => (
          <Link key={e} className="chip" to={`/cotizaciones?estado=${e}`}>
            {ETIQUETA_ESTADO[e]} <b>{porEstado.get(e)?.cantidad ?? 0}</b>
          </Link>
        ))}
      </div>

      <h2>Últimas cotizaciones</h2>
      {recientes.isLoading ? (
        <Cargando />
      ) : recientes.data?.items.length ? (
        <table className="tabla">
          <thead>
            <tr>
              <th>N°</th>
              <th>Cliente</th>
              <th>Emisión</th>
              <th className="num">Total</th>
              <th>Estado</th>
            </tr>
          </thead>
          <tbody>
            {recientes.data.items.map((c) => (
              <tr key={c.id}>
                <td>
                  <Link to={`/cotizaciones/${c.id}`}>{c.numero}</Link>
                </td>
                <td>{c.cliente.razonSocial}</td>
                <td>{fecha(c.fechaEmision)}</td>
                <td className="num">{clp(c.totales.total)}</td>
                <td>
                  <EstadoBadge estado={c.estado} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <Vacio>
          Aún no tienes cotizaciones. Empieza configurando los datos de tu empresa en <Link to="/configuracion">Configuración</Link> y
          luego crea tu <Link to="/cotizaciones/nueva">primera cotización</Link>.
        </Vacio>
      )}
    </section>
  )
}
