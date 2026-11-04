import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { z } from 'zod'
import { api, consulta } from '../api/cliente'
import type { Cliente, Cotizacion, CotizacionEntrada, Item, Pagina, Totales } from '../api/tipos'
import { Campo, Cargando, MensajeError, Modal } from '../componentes/Comunes'
import { useDiferido } from '../hooks/useDiferido'
import { brutoDesdeNeto, clp, hoyIso, netoDesdeBruto, sumarDias } from '../lib/formato'
import { ClienteFormulario } from './Clientes'

const numeroRequerido = (mensaje = 'Ingresa un número') => z.number({ error: mensaje }).refine((n) => !Number.isNaN(n), mensaje)

const esquema = z
  .object({
    clienteId: z.number().int().positive('Elige un cliente'),
    vendedor: z.string().max(100).optional(),
    fechaEmision: z.string().min(10, 'Fecha obligatoria'),
    fechaValidez: z.string().min(10, 'Fecha obligatoria'),
    lineas: z.array(
      z.object({
        descripcion: z.string().trim().min(1, 'Describe el producto o servicio').max(1000),
        cantidad: numeroRequerido().refine((n) => n > 0, 'Debe ser mayor que 0'),
        precioUnitario: numeroRequerido().refine((n) => Number.isInteger(n) && n >= 0, 'Pesos enteros, sin decimales'),
        unidad: z.string().max(30).optional(),
        descuento: numeroRequerido().refine((n) => n >= 0 && n <= 100, 'Entre 0 y 100'),
        afectaIva: z.boolean(),
        codigo: z.string().max(50).optional(),
        itemCatalogoId: z.number().optional(),
      }),
    ),
    descuentoTipo: z.enum(['NINGUNO', 'PORCENTAJE', 'MONTO']),
    descuentoValor: numeroRequerido(),
    formaPago: z.string().max(500).optional(),
    plazoEntrega: z.string().max(500).optional(),
    garantia: z.string().max(500).optional(),
    observaciones: z.string().max(5000).optional(),
  })
  .refine((f) => f.fechaValidez >= f.fechaEmision, { path: ['fechaValidez'], message: 'No puede ser anterior a la emisión' })

type Formulario = z.infer<typeof esquema>
type Plantilla = Pick<CotizacionEntrada, 'fechaEmision' | 'fechaValidez' | 'formaPago' | 'plazoEntrega' | 'garantia'>

const lineaVacia = { descripcion: '', cantidad: 1, precioUnitario: 0, unidad: '', descuento: 0, afectaIva: true, codigo: '' }

function aEntrada(f: Formulario): CotizacionEntrada {
  return {
    clienteId: f.clienteId,
    vendedor: f.vendedor || undefined,
    fechaEmision: f.fechaEmision,
    fechaValidez: f.fechaValidez,
    lineas: f.lineas.map((l) => ({ ...l, unidad: l.unidad || undefined, codigo: l.codigo || undefined })),
    descuentoGlobal: { tipo: f.descuentoTipo, valor: f.descuentoTipo === 'NINGUNO' ? 0 : f.descuentoValor },
    formaPago: f.formaPago || undefined,
    plazoEntrega: f.plazoEntrega || undefined,
    garantia: f.garantia || undefined,
    observaciones: f.observaciones || undefined,
  }
}

export function CotizacionEditor() {
  const { id } = useParams()
  const [params] = useSearchParams()
  const editando = id !== undefined
  const existente = useQuery({ queryKey: ['cotizacion', id], queryFn: () => api.get<Cotizacion>(`/api/cotizaciones/${id}`), enabled: editando })
  const plantilla = useQuery({ queryKey: ['plantilla'], queryFn: () => api.get<Plantilla>('/api/cotizaciones/plantilla'), enabled: !editando })
  const clienteInicial = params.get('clienteId')
  const clientePrevio = useQuery({
    queryKey: ['cliente', clienteInicial],
    queryFn: () => api.get<Cliente>(`/api/clientes/${clienteInicial}`),
    enabled: !editando && !!clienteInicial,
  })

  if (editando && existente.isLoading) return <Cargando />
  if (!editando && (plantilla.isLoading || clientePrevio.isLoading)) return <Cargando />
  if (existente.error || plantilla.error) return <MensajeError error={existente.error || plantilla.error} />
  if (existente.data && !existente.data.editable) {
    return (
      <div className="aviso">
        La cotización {existente.data.numero} ya fue enviada y no se puede modificar: para cambiarla, crea una{' '}
        <Link to={`/cotizaciones/${existente.data.id}`}>nueva versión</Link>.
      </div>
    )
  }

  const c = existente.data
  const p = plantilla.data
  const inicial: Formulario = c
    ? {
        clienteId: c.clienteId,
        vendedor: c.vendedor ?? '',
        fechaEmision: c.fechaEmision,
        fechaValidez: c.fechaValidez,
        lineas: c.lineas.map((l) => ({ ...l, unidad: l.unidad ?? '', codigo: l.codigo ?? '' })),
        descuentoTipo: c.descuentoGlobal.tipo,
        descuentoValor: c.descuentoGlobal.valor,
        formaPago: c.formaPago ?? '',
        plazoEntrega: c.plazoEntrega ?? '',
        garantia: c.garantia ?? '',
        observaciones: c.observaciones ?? '',
      }
    : {
        clienteId: clientePrevio.data?.id ?? 0,
        vendedor: localStorage.getItem('vendedor') ?? '',
        fechaEmision: p?.fechaEmision ?? hoyIso(),
        fechaValidez: p?.fechaValidez ?? sumarDias(hoyIso(), 15),
        lineas: [lineaVacia],
        descuentoTipo: 'NINGUNO',
        descuentoValor: 0,
        formaPago: p?.formaPago ?? '',
        plazoEntrega: p?.plazoEntrega ?? '',
        garantia: p?.garantia ?? '',
        observaciones: '',
      }
  return <Editor inicial={inicial} existente={c} nombreCliente={c?.cliente.razonSocial ?? clientePrevio.data?.razonSocial ?? ''} />
}

function Editor({ inicial, existente, nombreCliente }: { inicial: Formulario; existente?: Cotizacion; nombreCliente: string }) {
  const navegar = useNavigate()
  const consultas = useQueryClient()
  const [conIva, setConIva] = useState(false)
  const [cliente, setCliente] = useState(nombreCliente)
  const {
    register,
    control,
    handleSubmit,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<Formulario>({ resolver: zodResolver(esquema), defaultValues: inicial })
  const { fields, append, remove, move } = useFieldArray({ control, name: 'lineas' })

  const lineas = useWatch({ control, name: 'lineas' })
  const descuentoTipo = useWatch({ control, name: 'descuentoTipo' })
  const descuentoValor = useWatch({ control, name: 'descuentoValor' })
  const solicitudCalculo = useDiferido(
    JSON.stringify({
      lineas: (lineas ?? [])
        .filter((l) => l.cantidad > 0 && Number.isInteger(l.precioUnitario))
        .map((l) => ({ ...l, descripcion: l.descripcion || '—', descuento: Number.isNaN(l.descuento) ? 0 : l.descuento })),
      descuentoGlobal: { tipo: descuentoTipo, valor: descuentoTipo === 'NINGUNO' || Number.isNaN(descuentoValor) ? 0 : descuentoValor },
    }),
  )
  const calculo = useQuery({
    queryKey: ['calculo', solicitudCalculo],
    queryFn: () => api.post<Totales>('/api/cotizaciones/calcular', JSON.parse(solicitudCalculo)),
    placeholderData: (previo) => previo,
    retry: false,
  })

  const guardar = useMutation({
    mutationFn: (f: Formulario) =>
      existente ? api.put<Cotizacion>(`/api/cotizaciones/${existente.id}`, aEntrada(f)) : api.post<Cotizacion>('/api/cotizaciones', aEntrada(f)),
    onSuccess: (c, f) => {
      if (f.vendedor) localStorage.setItem('vendedor', f.vendedor)
      consultas.invalidateQueries({ queryKey: ['cotizaciones'] })
      consultas.invalidateQueries({ queryKey: ['sistema'] })
      consultas.setQueryData(['cotizacion', String(c.id)], c)
      navegar(`/cotizaciones/${c.id}`)
    },
  })

  const agregarDelCatalogo = (item: Item) => {
    const vacia = fields.length === 1 && !lineas?.[0]?.descripcion
    const nueva = {
      descripcion: item.nombre,
      cantidad: 1,
      precioUnitario: item.precioNeto,
      unidad: item.unidad ?? '',
      descuento: 0,
      afectaIva: item.afectoIva,
      codigo: item.codigo ?? '',
      itemCatalogoId: item.id,
    }
    if (vacia) setValue('lineas.0', nueva)
    else append(nueva)
  }

  return (
    <form onSubmit={handleSubmit((f) => guardar.mutate(f))} noValidate>
      <header className="cabecera-pagina">
        <h1>{existente ? `Editar ${existente.numero}` : 'Nueva cotización'}</h1>
        <div className="acciones">
          <Link className="boton boton-secundario" to={existente ? `/cotizaciones/${existente.id}` : '/cotizaciones'}>
            Cancelar
          </Link>
          <button className="boton" type="submit" disabled={isSubmitting || guardar.isPending}>
            {guardar.isPending ? 'Guardando…' : 'Guardar borrador'}
          </button>
        </div>
      </header>
      <MensajeError error={guardar.error} />

      <fieldset className="panel">
        <legend>Cliente y fechas</legend>
        <div className="grilla">
          <Campo etiqueta="Cliente" error={errors.clienteId?.message} ancho="medio">
            <SelectorCliente
              nombre={cliente}
              onElegir={(c) => {
                setCliente(c.razonSocial)
                setValue('clienteId', c.id, { shouldValidate: true })
              }}
            />
          </Campo>
          <Campo etiqueta="Vendedor" ancho="medio">
            <input {...register('vendedor')} placeholder="Quién atiende al cliente" />
          </Campo>
          <Campo etiqueta="Fecha de emisión" error={errors.fechaEmision?.message}>
            <input type="date" {...register('fechaEmision')} />
          </Campo>
          <Campo etiqueta="Válida hasta" error={errors.fechaValidez?.message} ayuda="Pasada esta fecha la oferta vence.">
            <input type="date" {...register('fechaValidez')} />
          </Campo>
        </div>
      </fieldset>

      <fieldset className="panel">
        <legend>Detalle</legend>
        <div className="barra-lineas">
          <SelectorCatalogo onElegir={agregarDelCatalogo} />
          <label className="interruptor">
            <input type="checkbox" checked={conIva} onChange={(e) => setConIva(e.target.checked)} />
            Escribir precios con IVA incluido
          </label>
        </div>
        <div className="tabla-desplazable">
          <table className="tabla tabla-lineas">
            <thead>
              <tr>
                <th>Descripción</th>
                <th style={{ width: '6rem' }}>Cant.</th>
                <th style={{ width: '5rem' }}>Unidad</th>
                <th style={{ width: '9rem' }}>{conIva ? 'Precio c/IVA' : 'Precio neto'}</th>
                <th style={{ width: '5rem' }}>Dcto. %</th>
                <th style={{ width: '4.5rem' }}>Exento</th>
                <th className="num" style={{ width: '8rem' }}>
                  Total
                </th>
                <th style={{ width: '5.5rem' }} />
              </tr>
            </thead>
            <tbody>
              {fields.map((campo, i) => {
                const e = errors.lineas?.[i]
                return (
                  <tr key={campo.id}>
                    <td>
                      <textarea rows={1} {...register(`lineas.${i}.descripcion`)} aria-label={`Descripción línea ${i + 1}`} />
                      {e?.descripcion && <span className="campo-error">{e.descripcion.message}</span>}
                    </td>
                    <td>
                      <input type="number" step="0.001" min="0" {...register(`lineas.${i}.cantidad`, { valueAsNumber: true })} aria-label="Cantidad" />
                      {e?.cantidad && <span className="campo-error">{e.cantidad.message}</span>}
                    </td>
                    <td>
                      <input {...register(`lineas.${i}.unidad`)} aria-label="Unidad" />
                    </td>
                    <td>
                      <Controller
                        control={control}
                        name={`lineas.${i}.precioUnitario`}
                        render={({ field }) => (
                          <input
                            type="number"
                            step="1"
                            min="0"
                            aria-label="Precio"
                            value={Number.isNaN(field.value) ? '' : conIva ? brutoDesdeNeto(field.value) : field.value}
                            onChange={(ev) => {
                              const v = ev.target.valueAsNumber
                              field.onChange(Number.isNaN(v) ? NaN : conIva ? netoDesdeBruto(v) : v)
                            }}
                            onBlur={field.onBlur}
                          />
                        )}
                      />
                      {e?.precioUnitario && <span className="campo-error">{e.precioUnitario.message}</span>}
                    </td>
                    <td>
                      <input type="number" step="0.01" min="0" max="100" {...register(`lineas.${i}.descuento`, { valueAsNumber: true })} aria-label="Descuento" />
                    </td>
                    <td className="centro">
                      <Controller
                        control={control}
                        name={`lineas.${i}.afectaIva`}
                        render={({ field }) => (
                          <input type="checkbox" aria-label="Exento de IVA" checked={!field.value} onChange={(ev) => field.onChange(!ev.target.checked)} />
                        )}
                      />
                    </td>
                    <td className="num">{calculo.data?.lineas[i] ? clp(calculo.data.lineas[i].subtotal) : '—'}</td>
                    <td className="acciones-fila">
                      <button type="button" className="boton-icono" disabled={i === 0} onClick={() => move(i, i - 1)} aria-label="Subir">
                        ↑
                      </button>
                      <button type="button" className="boton-icono" onClick={() => remove(i)} aria-label="Quitar línea">
                        ×
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
        <button type="button" className="boton boton-secundario" onClick={() => append(lineaVacia)}>
          + Agregar línea libre
        </button>

        <div className="pie-totales">
          <div className="descuento-global">
            <Campo etiqueta="Descuento global">
              <select {...register('descuentoTipo')}>
                <option value="NINGUNO">Sin descuento</option>
                <option value="PORCENTAJE">Porcentaje (%)</option>
                <option value="MONTO">Monto ($ neto)</option>
              </select>
            </Campo>
            {descuentoTipo !== 'NINGUNO' && (
              <Campo etiqueta={descuentoTipo === 'PORCENTAJE' ? 'Porcentaje' : 'Monto'}>
                <input type="number" min="0" step={descuentoTipo === 'PORCENTAJE' ? '0.01' : '1'} {...register('descuentoValor', { valueAsNumber: true })} />
              </Campo>
            )}
          </div>
          <TablaTotales totales={calculo.data} error={calculo.error} />
        </div>
      </fieldset>

      <fieldset className="panel">
        <legend>Condiciones comerciales</legend>
        <div className="grilla">
          <Campo etiqueta="Forma de pago" ancho="medio">
            <input {...register('formaPago')} />
          </Campo>
          <Campo etiqueta="Plazo de entrega" ancho="medio">
            <input {...register('plazoEntrega')} />
          </Campo>
          <Campo etiqueta="Garantía" ancho="completo">
            <input {...register('garantia')} />
          </Campo>
          <Campo etiqueta="Observaciones" ancho="completo">
            <textarea rows={3} {...register('observaciones')} />
          </Campo>
        </div>
      </fieldset>
    </form>
  )
}

export function TablaTotales({ totales, error }: { totales?: Totales; error?: unknown }) {
  if (error) return <MensajeError error={error} />
  if (!totales) return null
  return (
    <table className="totales">
      <tbody>
        {totales.descuentoGlobal > 0 && (
          <>
            <tr>
              <td>Subtotal</td>
              <td className="num">{clp(totales.subtotalAfecto + totales.subtotalExento)}</td>
            </tr>
            <tr>
              <td>Descuento</td>
              <td className="num">−{clp(totales.descuentoGlobal)}</td>
            </tr>
          </>
        )}
        <tr>
          <td>Neto</td>
          <td className="num">{clp(totales.neto)}</td>
        </tr>
        {totales.exento > 0 && (
          <tr>
            <td>Exento</td>
            <td className="num">{clp(totales.exento)}</td>
          </tr>
        )}
        <tr>
          <td>IVA</td>
          <td className="num">{clp(totales.iva)}</td>
        </tr>
        <tr className="total">
          <td>Total</td>
          <td className="num">{clp(totales.total)}</td>
        </tr>
      </tbody>
    </table>
  )
}

function SelectorCliente({ nombre, onElegir }: { nombre: string; onElegir: (c: Cliente) => void }) {
  const [texto, setTexto] = useState('')
  const [abierto, setAbierto] = useState(false)
  const [creando, setCreando] = useState(false)
  const q = useDiferido(texto, 250)
  const { data } = useQuery({
    queryKey: ['clientes', 'selector', q],
    queryFn: () => api.get<Pagina<Cliente>>(`/api/clientes${consulta({ q, tamano: 8 })}`),
    enabled: abierto,
  })
  useEffect(() => setTexto(''), [nombre])
  return (
    <div className="selector">
      <input
        value={abierto ? texto : nombre}
        placeholder="Busca por nombre o RUT"
        onFocus={() => setAbierto(true)}
        onBlur={() => setTimeout(() => setAbierto(false), 150)}
        onChange={(e) => setTexto(e.target.value)}
        aria-label="Buscar cliente"
      />
      {abierto && (
        <ul className="selector-opciones" role="listbox">
          {data?.items.map((c) => (
            <li key={c.id} role="option" aria-selected={false} onMouseDown={() => onElegir(c)}>
              <strong>{c.razonSocial}</strong> <span>{c.rut ?? ''}</span>
            </li>
          ))}
          <li className="selector-nuevo" onMouseDown={() => setCreando(true)}>
            + Crear cliente nuevo
          </li>
        </ul>
      )}
      {creando && (
        <Modal titulo="Nuevo cliente" onCerrar={() => setCreando(false)}>
          <ClienteFormulario
            onGuardado={(c) => {
              setCreando(false)
              onElegir(c)
            }}
          />
        </Modal>
      )}
    </div>
  )
}

function SelectorCatalogo({ onElegir }: { onElegir: (i: Item) => void }) {
  const [texto, setTexto] = useState('')
  const [abierto, setAbierto] = useState(false)
  const q = useDiferido(texto, 250)
  const { data } = useQuery({
    queryKey: ['catalogo', 'selector', q],
    queryFn: () => api.get<Pagina<Item>>(`/api/catalogo${consulta({ q, soloActivos: true, tamano: 10 })}`),
    enabled: abierto,
  })
  return (
    <div className="selector selector-catalogo">
      <input
        value={texto}
        placeholder="Agregar desde el catálogo…"
        onFocus={() => setAbierto(true)}
        onBlur={() => setTimeout(() => setAbierto(false), 150)}
        onChange={(e) => setTexto(e.target.value)}
        aria-label="Buscar en el catálogo"
      />
      {abierto && (
        <ul className="selector-opciones" role="listbox">
          {data?.items.length === 0 && <li className="selector-vacio">Sin resultados. Agrega productos en Catálogo.</li>}
          {data?.items.map((i) => (
            <li
              key={i.id}
              role="option"
              aria-selected={false}
              onMouseDown={() => {
                onElegir(i)
                setTexto('')
              }}
            >
              <strong>{i.nombre}</strong> <span>{i.codigo ?? ''}</span> <span className="num">{clp(i.precioNeto)}{i.afectoIva ? " + IVA" : " exento"}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
