import './configuracion-zod'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { ErrorApi } from './api/cliente'
import { Diseno } from './componentes/Diseno'
import { Catalogo } from './paginas/Catalogo'
import { Clientes } from './paginas/Clientes'
import { Configuracion } from './paginas/Configuracion'
import { CotizacionDetalle } from './paginas/CotizacionDetalle'
import { CotizacionEditor } from './paginas/CotizacionEditor'
import { Cotizaciones } from './paginas/Cotizaciones'
import { Panel } from './paginas/Panel'
import './estilos.css'

const clienteConsultas = new QueryClient({
  defaultOptions: {
    queries: {
      retry: (intentos, error) => !(error instanceof ErrorApi && error.estado < 500) && intentos < 2,
      refetchOnWindowFocus: false,
    },
  },
})

const rutas = createBrowserRouter([
  {
    element: <Diseno />,
    children: [
      { path: '/', element: <Panel /> },
      { path: '/cotizaciones', element: <Cotizaciones /> },
      { path: '/cotizaciones/nueva', element: <CotizacionEditor /> },
      { path: '/cotizaciones/:id', element: <CotizacionDetalle /> },
      { path: '/cotizaciones/:id/editar', element: <CotizacionEditor /> },
      { path: '/clientes', element: <Clientes /> },
      { path: '/catalogo', element: <Catalogo /> },
      { path: '/configuracion', element: <Configuracion /> },
    ],
  },
])

createRoot(document.getElementById('raiz')!).render(
  <StrictMode>
    <QueryClientProvider client={clienteConsultas}>
      <RouterProvider router={rutas} />
    </QueryClientProvider>
  </StrictMode>,
)
