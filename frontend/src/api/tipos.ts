export type Estado = 'BORRADOR' | 'ENVIADA' | 'ACEPTADA' | 'RECHAZADA' | 'VENCIDA' | 'REEMPLAZADA'
export type Medio = 'CORREO' | 'FIRMA' | 'VERBAL' | 'OTRO'
export type TipoDescuento = 'NINGUNO' | 'PORCENTAJE' | 'MONTO'

export interface Pagina<T> {
  items: T[]
  total: number
  pagina: number
  tamano: number
}

export interface Cliente {
  id: number
  rut?: string
  razonSocial: string
  giro?: string
  contacto?: string
  email?: string
  telefono?: string
  direccion?: string
  comuna?: string
  ciudad?: string
  notas?: string
}

export interface Item {
  id: number
  codigo?: string
  nombre: string
  descripcion?: string
  unidad?: string
  precioNeto: number
  afectoIva: boolean
  activo: boolean
}

export interface Empresa {
  razonSocial: string
  rut?: string
  giro?: string
  direccion?: string
  comuna?: string
  ciudad?: string
  telefono?: string
  email?: string
  sitioWeb?: string
  tasaIva: number
  diasValidez: number
  formaPago?: string
  plazoEntrega?: string
  garantia?: string
  condiciones?: string
  tieneLogo: boolean
}

export interface Descuento {
  tipo: TipoDescuento
  valor: number
}

export interface Linea {
  descripcion: string
  cantidad: number
  precioUnitario: number
  unidad?: string
  descuento: number
  afectaIva: boolean
  codigo?: string
  itemCatalogoId?: number
}

export interface Totales {
  lineas: { bruto: number; descuento: number; subtotal: number }[]
  subtotalAfecto: number
  subtotalExento: number
  descuentoGlobal: number
  neto: number
  exento: number
  iva: number
  total: number
}

export interface DatosCliente {
  razonSocial: string
  rut?: string
  giro?: string
  direccion?: string
  contacto?: string
  email?: string
  telefono?: string
}

export interface Cotizacion {
  id: number
  folio: number
  version: number
  numero: string
  estado: Estado
  editable: boolean
  clienteId: number
  cliente: DatosCliente
  vendedor?: string
  fechaEmision: string
  fechaValidez: string
  moneda: string
  tasaIva: number
  lineas: Linea[]
  descuentoGlobal: Descuento
  formaPago?: string
  plazoEntrega?: string
  garantia?: string
  observaciones?: string
  aceptacion?: { fecha: string; nombre: string; medio: Medio; detalle?: string }
  motivoRechazo?: string
  totales: Totales
  enviadaEn?: string
  creadaEn: string
  actualizadaEn: string
}

export interface CotizacionEntrada {
  clienteId: number
  vendedor?: string
  fechaEmision: string
  fechaValidez: string
  lineas: Linea[]
  descuentoGlobal: Descuento
  formaPago?: string
  plazoEntrega?: string
  garantia?: string
  observaciones?: string
}

export interface Evento {
  desde?: Estado
  hacia: Estado
  ocurridoEn: string
  detalle?: string
}

export interface ResumenEstado {
  estado: Estado
  cantidad: number
  total: number
}

export interface Sistema {
  modo: 'LOCAL' | 'DEMO'
  version: string
  carpetaDatos?: string
  licencia: {
    licenciada: boolean
    licencia?: { id: string; titular: string; rut?: string; tipo: string; emitida: string }
    cotizacionesUsadas: number
    limitePrueba: number
  }
}

export interface Respaldo {
  nombre: string
  bytes: number
  creado: string
}
