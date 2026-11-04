import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { api, consulta } from '../api/cliente'
import type { Cotizacion, Estado, Pagina } from '../api/tipos'
import { Cargando, EstadoBadge, ETIQUETA_ESTADO, MensajeError, Paginador, Vacio } from '../componentes/Comunes'
import { clp, fecha } from '../lib/formato'

export function Cotizaciones() {
  const [params, setParams] = useSearchParams()
  const q = params.get('q') ?? ''
  const estado = (params.get('estado') ?? '') as Estado | ''
  const pagina = Number(params.get('pagina') ?? 0)
  const tamano = 20

  const { data, isLoading, error } = useQuery({
    queryKey: ['cotizaciones', q, estado, pagina],
    queryFn: () => api.get<Pagina<Cotizacion>>(`/api/cotizaciones${consulta({ q, estado, pagina, tamano })}`),
    placeholderData: keepPreviousData,
  })

  const cambiar = (clave: string, valor: string) => {
    const nuevos = new URLSearchParams(params)
    if (valor) nuevos.set(clave, valor)
    else nuevos.delete(clave)
    if (clave !== 'pagina') nuevos.delete('pagina')
    setParams(nuevos, { replace: true })
  }

  return (
    <section>
      <header className="cabecera-pagina">
        <h1>Cotizaciones</h1>
        <Link className="boton" to="/cotizaciones/nueva">
          Nueva cotización
        </Link>
      </header>
      <div className="filtros">
        <input
          type="search"
          placeholder="Buscar por N°, cliente o vendedor"
          value={q}
          onChange={(e) => cambiar('q', e.target.value)}
          aria-label="Buscar cotizaciones"
        />
        <select value={estado} onChange={(e) => cambiar('estado', e.target.value)} aria-label="Filtrar por estado">
          <option value="">Todos los estados</option>
          {Object.entries(ETIQUETA_ESTADO).map(([valor, texto]) => (
            <option key={valor} value={valor}>
              {texto}
            </option>
          ))}
        </select>
      </div>
      <MensajeError error={error} />
      {isLoading ? (
        <Cargando />
      ) : data?.items.length ? (
        <>
          <table className="tabla">
            <thead>
              <tr>
                <th>N°</th>
                <th>Cliente</th>
                <th>Vendedor</th>
                <th>Emisión</th>
                <th>Válida hasta</th>
                <th className="num">Total</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  <td>
                    <Link to={`/cotizaciones/${c.id}`}>{c.numero}</Link>
                  </td>
                  <td>{c.cliente.razonSocial}</td>
                  <td>{c.vendedor ?? '—'}</td>
                  <td>{fecha(c.fechaEmision)}</td>
                  <td>{fecha(c.fechaValidez)}</td>
                  <td className="num">{clp(c.totales.total)}</td>
                  <td>
                    <EstadoBadge estado={c.estado} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginador pagina={pagina} tamano={tamano} total={data.total} onCambiar={(p) => cambiar('pagina', String(p))} />
        </>
      ) : (
        <Vacio>No hay cotizaciones que coincidan con la búsqueda.</Vacio>
      )}
    </section>
  )
}
