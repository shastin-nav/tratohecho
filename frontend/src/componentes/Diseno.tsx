import { useQuery } from '@tanstack/react-query'
import { NavLink, Outlet } from 'react-router'
import { api, ErrorApi } from '../api/cliente'
import type { Sistema } from '../api/tipos'

export function useSistema() {
  return useQuery({ queryKey: ['sistema'], queryFn: () => api.get<Sistema>('/api/sistema') })
}

const enlaces = [
  { a: '/', texto: 'Panel', fin: true },
  { a: '/cotizaciones', texto: 'Cotizaciones' },
  { a: '/clientes', texto: 'Clientes' },
  { a: '/catalogo', texto: 'Catálogo' },
  { a: '/configuracion', texto: 'Configuración' },
]

export function Diseno() {
  const { data: sistema, error } = useSistema()
  const sinSesion = error instanceof ErrorApi && error.codigo === 'sesion_local'
  return (
    <div className="aplicacion">
      <aside className="barra">
        <div className="marca">
          <img src="/icono.svg" alt="" width={28} height={28} />
          <span>Tratohecho</span>
        </div>
        <nav>
          {enlaces.map((e) => (
            <NavLink key={e.a} to={e.a} end={e.fin} className={({ isActive }) => (isActive ? 'activo' : undefined)}>
              {e.texto}
            </NavLink>
          ))}
        </nav>
        <p className="lema">Cotiza, envía y cierra.</p>
      </aside>
      <main className="contenido">
        {sistema?.modo === 'DEMO' && (
          <div className="aviso aviso-demo">
            <strong>Demo pública con datos ficticios.</strong> No ingreses datos reales: todo se borra cada hora.
          </div>
        )}
        {sistema?.modo === 'LOCAL' && !sistema.licencia.licenciada && (
          <div className="aviso aviso-prueba">
            Versión de prueba: {sistema.licencia.cotizacionesUsadas} de {sistema.licencia.limitePrueba} cotizaciones.{' '}
            <NavLink to="/configuracion#licencia">Activar licencia</NavLink>
          </div>
        )}
        {sinSesion ? (
          <div className="aviso aviso-error">
            <strong>Sesión no iniciada.</strong> Cierra esta pestaña y abre Tratohecho desde el ícono de la aplicación.
          </div>
        ) : (
          <Outlet />
        )}
      </main>
    </div>
  )
}
