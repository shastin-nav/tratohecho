import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'
import { api, consulta } from '../api/cliente'
import type { Item, Pagina } from '../api/tipos'
import { Campo, Cargando, MensajeError, Modal, Paginador, Vacio } from '../componentes/Comunes'
import { useDiferido } from '../hooks/useDiferido'
import { brutoDesdeNeto, clp, netoDesdeBruto } from '../lib/formato'

const esquema = z.object({
  codigo: z.string().max(50).optional(),
  nombre: z.string().trim().min(1, 'Obligatorio').max(200),
  descripcion: z.string().max(5000).optional(),
  unidad: z.string().max(30).optional(),
  precio: z.number({ error: 'Ingresa un precio' }).int('Sin decimales').min(0, 'No puede ser negativo'),
  precioConIva: z.boolean(),
  afectoIva: z.boolean(),
  activo: z.boolean(),
})
type Formulario = z.infer<typeof esquema>

function ItemFormulario({ item, onGuardado }: { item?: Item; onGuardado: () => void }) {
  const consultas = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Formulario>({
    resolver: zodResolver(esquema),
    defaultValues: {
      codigo: item?.codigo ?? '',
      nombre: item?.nombre ?? '',
      descripcion: item?.descripcion ?? '',
      unidad: item?.unidad ?? 'un',
      precio: item?.precioNeto ?? 0,
      precioConIva: false,
      afectoIva: item?.afectoIva ?? true,
      activo: item?.activo ?? true,
    },
  })
  const [precio, conIva, afecto] = useWatch({ control, name: ['precio', 'precioConIva', 'afectoIva'] })
  const neto = conIva && afecto ? netoDesdeBruto(precio || 0) : precio || 0
  const guardar = useMutation({
    mutationFn: (f: Formulario) => {
      const cuerpo = {
        codigo: f.codigo || null,
        nombre: f.nombre,
        descripcion: f.descripcion || null,
        unidad: f.unidad || null,
        precioNeto: f.precioConIva && f.afectoIva ? netoDesdeBruto(f.precio) : f.precio,
        afectoIva: f.afectoIva,
        activo: f.activo,
      }
      return item ? api.put<Item>(`/api/catalogo/${item.id}`, cuerpo) : api.post<Item>('/api/catalogo', cuerpo)
    },
    onSuccess: () => {
      consultas.invalidateQueries({ queryKey: ['catalogo'] })
      onGuardado()
    },
  })
  return (
    <form onSubmit={handleSubmit((f) => guardar.mutate(f))} className="grilla" noValidate>
      <MensajeError error={guardar.error} />
      <Campo etiqueta="Nombre del producto o servicio" error={errors.nombre?.message} ancho="completo">
        <input {...register('nombre')} autoFocus />
      </Campo>
      <Campo etiqueta="Código (opcional)">
        <input {...register('codigo')} />
      </Campo>
      <Campo etiqueta="Unidad" ayuda="un, m, m², h, kg…">
        <input {...register('unidad')} />
      </Campo>
      <Campo
        etiqueta={conIva && afecto ? 'Precio con IVA' : 'Precio neto'}
        error={errors.precio?.message}
        ayuda={afecto ? `Neto ${clp(neto)} · con IVA ${clp(brutoDesdeNeto(neto))}` : 'Exento de IVA'}
      >
        <input type="number" min="0" step="1" {...register('precio', { valueAsNumber: true })} />
      </Campo>
      <div className="campo">
        <label className="interruptor">
          <input type="checkbox" {...register('precioConIva')} disabled={!afecto} /> Estoy ingresando el precio con IVA
        </label>
        <label className="interruptor">
          <input type="checkbox" {...register('afectoIva')} /> Afecto a IVA
        </label>
        <label className="interruptor">
          <input type="checkbox" {...register('activo')} /> Activo (aparece al cotizar)
        </label>
      </div>
      <Campo etiqueta="Descripción" ancho="completo">
        <textarea rows={2} {...register('descripcion')} />
      </Campo>
      <div className="ancho-completo acciones">
        <button className="boton" disabled={guardar.isPending}>
          Guardar
        </button>
      </div>
    </form>
  )
}

export function Catalogo() {
  const [texto, setTexto] = useState('')
  const [pagina, setPagina] = useState(0)
  const [editando, setEditando] = useState<Item | 'nuevo' | null>(null)
  const q = useDiferido(texto)
  const { data, isLoading, error } = useQuery({
    queryKey: ['catalogo', q, pagina],
    queryFn: () => api.get<Pagina<Item>>(`/api/catalogo${consulta({ q, pagina })}`),
    placeholderData: keepPreviousData,
  })
  return (
    <section>
      <header className="cabecera-pagina">
        <h1>Catálogo</h1>
        <button className="boton" onClick={() => setEditando('nuevo')}>
          Nuevo producto o servicio
        </button>
      </header>
      <div className="filtros">
        <input
          type="search"
          placeholder="Buscar por nombre o código"
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value)
            setPagina(0)
          }}
          aria-label="Buscar en el catálogo"
        />
      </div>
      <MensajeError error={error} />
      {isLoading ? (
        <Cargando />
      ) : data?.items.length ? (
        <>
          <table className="tabla">
            <thead>
              <tr>
                <th>Código</th>
                <th>Nombre</th>
                <th>Unidad</th>
                <th className="num">Neto</th>
                <th className="num">Con IVA</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {data.items.map((i) => (
                <tr key={i.id} className={i.activo ? undefined : 'inactivo'}>
                  <td>{i.codigo ?? '—'}</td>
                  <td>
                    <button className="enlace" onClick={() => setEditando(i)}>
                      {i.nombre}
                    </button>
                  </td>
                  <td>{i.unidad ?? '—'}</td>
                  <td className="num">{clp(i.precioNeto)}</td>
                  <td className="num">{i.afectoIva ? clp(brutoDesdeNeto(i.precioNeto)) : 'Exento'}</td>
                  <td>{i.activo ? 'Activo' : 'Inactivo'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginador pagina={pagina} tamano={data.tamano} total={data.total} onCambiar={setPagina} />
        </>
      ) : (
        <Vacio>Agrega los productos y servicios que cotizas seguido para no escribirlos cada vez.</Vacio>
      )}
      {editando && (
        <Modal titulo={editando === 'nuevo' ? 'Nuevo producto o servicio' : 'Editar'} onCerrar={() => setEditando(null)}>
          <ItemFormulario item={editando === 'nuevo' ? undefined : editando} onGuardado={() => setEditando(null)} />
        </Modal>
      )}
    </section>
  )
}
