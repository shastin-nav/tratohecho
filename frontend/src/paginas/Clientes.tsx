import { zodResolver } from '@hookform/resolvers/zod'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'
import { api, consulta } from '../api/cliente'
import type { Cliente, Pagina } from '../api/tipos'
import { Campo, Cargando, MensajeError, Modal, Paginador, Vacio } from '../componentes/Comunes'
import { useDiferido } from '../hooks/useDiferido'
import { formatearRut, rutValido } from '../lib/formato'

const opcional = (max: number) => z.string().max(max).optional()

const esquema = z.object({
  rut: z
    .string()
    .optional()
    .refine((r) => !r || rutValido(r), 'RUT inválido: revisa el dígito verificador'),
  razonSocial: z.string().trim().min(1, 'Obligatorio').max(200),
  giro: opcional(200),
  contacto: opcional(200),
  email: z.union([z.literal(''), z.email('Correo inválido')]).optional(),
  telefono: opcional(50),
  direccion: opcional(300),
  comuna: opcional(100),
  ciudad: opcional(100),
  notas: opcional(5000),
})
type Formulario = z.infer<typeof esquema>

export function ClienteFormulario({ cliente, onGuardado }: { cliente?: Cliente; onGuardado: (c: Cliente) => void }) {
  const consultas = useQueryClient()
  const {
    register,
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<Formulario>({
    resolver: zodResolver(esquema),
    defaultValues: {
      rut: cliente?.rut ?? '',
      razonSocial: cliente?.razonSocial ?? '',
      giro: cliente?.giro ?? '',
      contacto: cliente?.contacto ?? '',
      email: cliente?.email ?? '',
      telefono: cliente?.telefono ?? '',
      direccion: cliente?.direccion ?? '',
      comuna: cliente?.comuna ?? '',
      ciudad: cliente?.ciudad ?? '',
      notas: cliente?.notas ?? '',
    },
  })
  const guardar = useMutation({
    mutationFn: (f: Formulario) => {
      const cuerpo = Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v === '' ? null : v]))
      return cliente ? api.put<Cliente>(`/api/clientes/${cliente.id}`, cuerpo) : api.post<Cliente>('/api/clientes', cuerpo)
    },
    onSuccess: (c) => {
      consultas.invalidateQueries({ queryKey: ['clientes'] })
      onGuardado(c)
    },
  })
  return (
    <form onSubmit={handleSubmit((f) => guardar.mutate(f))} className="grilla" noValidate>
      <MensajeError error={guardar.error} />
      <Campo etiqueta="Nombre o razón social" error={errors.razonSocial?.message} ancho="completo">
        <input {...register('razonSocial')} autoFocus />
      </Campo>
      <Campo etiqueta="RUT" error={errors.rut?.message} ayuda="Opcional para personas naturales">
        <Controller
          control={control}
          name="rut"
          render={({ field }) => <input {...field} onChange={(e) => field.onChange(formatearRut(e.target.value))} placeholder="12.345.678-5" />}
        />
      </Campo>
      <Campo etiqueta="Giro">
        <input {...register('giro')} />
      </Campo>
      <Campo etiqueta="Persona de contacto">
        <input {...register('contacto')} />
      </Campo>
      <Campo etiqueta="Correo" error={errors.email?.message}>
        <input type="email" {...register('email')} />
      </Campo>
      <Campo etiqueta="Teléfono">
        <input type="tel" {...register('telefono')} />
      </Campo>
      <Campo etiqueta="Dirección">
        <input {...register('direccion')} />
      </Campo>
      <Campo etiqueta="Comuna">
        <input {...register('comuna')} />
      </Campo>
      <Campo etiqueta="Ciudad">
        <input {...register('ciudad')} />
      </Campo>
      <Campo etiqueta="Notas internas" ancho="completo" ayuda="No aparecen en la cotización">
        <textarea rows={2} {...register('notas')} />
      </Campo>
      <div className="ancho-completo acciones">
        <button className="boton" disabled={guardar.isPending}>
          {guardar.isPending ? 'Guardando…' : 'Guardar cliente'}
        </button>
      </div>
    </form>
  )
}

export function Clientes() {
  const consultas = useQueryClient()
  const [texto, setTexto] = useState('')
  const [pagina, setPagina] = useState(0)
  const [editando, setEditando] = useState<Cliente | 'nuevo' | null>(null)
  const q = useDiferido(texto)
  const { data, isLoading, error } = useQuery({
    queryKey: ['clientes', q, pagina],
    queryFn: () => api.get<Pagina<Cliente>>(`/api/clientes${consulta({ q, pagina })}`),
    placeholderData: keepPreviousData,
  })
  const eliminar = useMutation({
    mutationFn: (c: Cliente) => api.delete<{ resultado: string }>(`/api/clientes/${c.id}`),
    onSuccess: (r) => {
      consultas.invalidateQueries({ queryKey: ['clientes'] })
      consultas.invalidateQueries({ queryKey: ['cotizaciones'] })
      alert(
        r.resultado === 'ANONIMIZADO'
          ? 'El cliente tenía cotizaciones: se anonimizaron sus datos personales y se conservaron los montos.'
          : 'Cliente eliminado.',
      )
    },
  })
  return (
    <section>
      <header className="cabecera-pagina">
        <h1>Clientes</h1>
        <button className="boton" onClick={() => setEditando('nuevo')}>
          Nuevo cliente
        </button>
      </header>
      <div className="filtros">
        <input
          type="search"
          placeholder="Buscar por nombre, RUT o contacto"
          value={texto}
          onChange={(e) => {
            setTexto(e.target.value)
            setPagina(0)
          }}
          aria-label="Buscar clientes"
        />
      </div>
      <MensajeError error={error || eliminar.error} />
      {isLoading ? (
        <Cargando />
      ) : data?.items.length ? (
        <>
          <table className="tabla">
            <thead>
              <tr>
                <th>Nombre o razón social</th>
                <th>RUT</th>
                <th>Contacto</th>
                <th>Comuna</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {data.items.map((c) => (
                <tr key={c.id}>
                  <td>
                    <button className="enlace" onClick={() => setEditando(c)}>
                      {c.razonSocial}
                    </button>
                  </td>
                  <td>{c.rut ?? '—'}</td>
                  <td>{[c.contacto, c.email, c.telefono].filter(Boolean).join(' · ') || '—'}</td>
                  <td>{c.comuna ?? '—'}</td>
                  <td className="acciones-fila">
                    <Link className="boton boton-chico" to={`/cotizaciones/nueva?clienteId=${c.id}`}>
                      Cotizar
                    </Link>
                    <a className="boton boton-chico boton-secundario" href={`/api/clientes/${c.id}/exportar`} title="Derecho de acceso y portabilidad">
                      Exportar datos
                    </a>
                    <button
                      className="boton boton-chico boton-peligro"
                      onClick={() =>
                        confirm(
                          `¿Eliminar a ${c.razonSocial}? Si tiene cotizaciones, sus datos personales se anonimizarán y los montos se conservarán.`,
                        ) && eliminar.mutate(c)
                      }
                    >
                      Eliminar
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <Paginador pagina={pagina} tamano={data.tamano} total={data.total} onCambiar={setPagina} />
        </>
      ) : (
        <Vacio>{q ? 'Ningún cliente coincide con la búsqueda.' : 'Aún no registras clientes.'}</Vacio>
      )}
      {editando && (
        <Modal titulo={editando === 'nuevo' ? 'Nuevo cliente' : 'Editar cliente'} onCerrar={() => setEditando(null)}>
          <ClienteFormulario cliente={editando === 'nuevo' ? undefined : editando} onGuardado={() => setEditando(null)} />
        </Modal>
      )}
    </section>
  )
}
