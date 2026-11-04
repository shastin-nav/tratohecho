import { zodResolver } from '@hookform/resolvers/zod'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { z } from 'zod'
import { api } from '../api/cliente'
import type { Empresa, Respaldo, Sistema } from '../api/tipos'
import { Campo, Cargando, MensajeError } from '../componentes/Comunes'
import { useSistema } from '../componentes/Diseno'
import { fecha, fechaHora, formatearRut, rutValido } from '../lib/formato'

export function Configuracion() {
  const sistema = useSistema()
  return (
    <section>
      <header className="cabecera-pagina">
        <h1>Configuración</h1>
      </header>
      <DatosEmpresa />
      <Logo />
      {sistema.data?.modo === 'LOCAL' && (
        <>
          <Licencia sistema={sistema.data} />
          <Respaldos carpeta={sistema.data.carpetaDatos} />
        </>
      )}
      <div className="panel">
        <h2>Privacidad</h2>
        <p>
          Tratohecho guarda todo en este equipo{sistema.data?.carpetaDatos ? <> (<code>{sistema.data.carpetaDatos}</code>)</> : null}. No envía
          datos de tus clientes a ningún servidor ni tiene telemetría. Tu empresa es la responsable de los datos personales que registras
          (Ley 21.719): desde <b>Clientes</b> puedes exportar los datos de una persona o eliminarlos cuando te lo pida.
        </p>
        <p className="campo-ayuda">Versión {sistema.data?.version}</p>
      </div>
    </section>
  )
}

const opcional = (max: number) => z.string().max(max).optional()
const esquemaEmpresa = z.object({
  razonSocial: z.string().trim().min(1, 'Obligatorio').max(200),
  rut: z
    .string()
    .optional()
    .refine((r) => !r || rutValido(r), 'RUT inválido'),
  giro: opcional(200),
  direccion: opcional(300),
  comuna: opcional(100),
  ciudad: opcional(100),
  telefono: opcional(50),
  email: z.union([z.literal(''), z.email('Correo inválido')]).optional(),
  sitioWeb: opcional(200),
  tasaIva: z.number({ error: 'Obligatorio' }).min(0).max(100),
  diasValidez: z.number({ error: 'Obligatorio' }).int().min(1, 'Mínimo 1 día').max(365, 'Máximo 365 días'),
  formaPago: opcional(500),
  plazoEntrega: opcional(500),
  garantia: opcional(500),
  condiciones: opcional(5000),
})
type FormEmpresa = z.infer<typeof esquemaEmpresa>

function DatosEmpresa() {
  const { data, isLoading, error } = useQuery({ queryKey: ['empresa'], queryFn: () => api.get<Empresa>('/api/empresa') })
  if (isLoading) return <Cargando />
  if (!data) return <MensajeError error={error} />
  return <FormularioEmpresa empresa={data} />
}

function FormularioEmpresa({ empresa }: { empresa: Empresa }) {
  const consultas = useQueryClient()
  const [guardado, setGuardado] = useState(false)
  const {
    register,
    control,
    handleSubmit,
    formState: { errors, isDirty },
    reset,
  } = useForm<FormEmpresa>({
    resolver: zodResolver(esquemaEmpresa),
    defaultValues: Object.fromEntries(Object.entries(empresa).map(([k, v]) => [k, v ?? ''])) as FormEmpresa,
  })
  const guardar = useMutation({
    mutationFn: (f: FormEmpresa) =>
      api.put<Empresa>('/api/empresa', Object.fromEntries(Object.entries(f).map(([k, v]) => [k, v === '' ? null : v]))),
    onSuccess: (e) => {
      consultas.setQueryData(['empresa'], e)
      consultas.invalidateQueries({ queryKey: ['plantilla'] })
      reset(Object.fromEntries(Object.entries(e).map(([k, v]) => [k, v ?? ''])) as FormEmpresa)
      setGuardado(true)
    },
  })
  return (
    <form className="panel" onSubmit={handleSubmit((f) => guardar.mutate(f))} noValidate onChange={() => setGuardado(false)}>
      <h2>Datos de tu empresa</h2>
      <p className="campo-ayuda">Aparecen en el encabezado de cada cotización.</p>
      <MensajeError error={guardar.error} />
      <div className="grilla">
        <Campo etiqueta="Razón social" error={errors.razonSocial?.message} ancho="medio">
          <input {...register('razonSocial')} />
        </Campo>
        <Campo etiqueta="RUT" error={errors.rut?.message}>
          <Controller control={control} name="rut" render={({ field }) => <input {...field} onChange={(e) => field.onChange(formatearRut(e.target.value))} />} />
        </Campo>
        <Campo etiqueta="Giro">
          <input {...register('giro')} />
        </Campo>
        <Campo etiqueta="Dirección" ancho="medio">
          <input {...register('direccion')} />
        </Campo>
        <Campo etiqueta="Comuna">
          <input {...register('comuna')} />
        </Campo>
        <Campo etiqueta="Ciudad">
          <input {...register('ciudad')} />
        </Campo>
        <Campo etiqueta="Teléfono">
          <input {...register('telefono')} />
        </Campo>
        <Campo etiqueta="Correo" error={errors.email?.message}>
          <input type="email" {...register('email')} />
        </Campo>
        <Campo etiqueta="Sitio web">
          <input {...register('sitioWeb')} />
        </Campo>
        <Campo etiqueta="IVA (%)" error={errors.tasaIva?.message} ayuda="19 % en Chile">
          <input type="number" step="0.01" {...register('tasaIva', { valueAsNumber: true })} />
        </Campo>
        <Campo etiqueta="Validez por defecto (días)" error={errors.diasValidez?.message}>
          <input type="number" {...register('diasValidez', { valueAsNumber: true })} />
        </Campo>
      </div>
      <h3>Condiciones por defecto</h3>
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
        <Campo etiqueta="Condiciones generales (al pie del PDF)" ancho="completo">
          <textarea rows={3} {...register('condiciones')} />
        </Campo>
      </div>
      <div className="acciones">
        {guardado && <span className="ok">Guardado ✓</span>}
        <button className="boton" disabled={!isDirty || guardar.isPending}>
          Guardar cambios
        </button>
      </div>
    </form>
  )
}

function Logo() {
  const consultas = useQueryClient()
  const empresa = useQuery({ queryKey: ['empresa'], queryFn: () => api.get<Empresa>('/api/empresa') })
  const [version, setVersion] = useState(0)
  const subir = useMutation({
    mutationFn: (archivo: File | null) => {
      if (!archivo) return api.delete<Empresa>('/api/empresa/logo')
      const datos = new FormData()
      datos.append('archivo', archivo)
      return fetch('/api/empresa/logo', { method: 'PUT', body: datos, credentials: 'same-origin' }).then(async (r) => {
        if (!r.ok) throw new Error((await r.json()).detail ?? 'No se pudo subir el logo')
        return (await r.json()) as Empresa
      })
    },
    onSuccess: (e) => {
      consultas.setQueryData(['empresa'], e)
      setVersion((v) => v + 1)
    },
  })
  return (
    <div className="panel">
      <h2>Logo</h2>
      <MensajeError error={subir.error} />
      <div className="logo-fila">
        {empresa.data?.tieneLogo ? <img className="logo-vista" src={`/api/empresa/logo?v=${version}`} alt="Logo actual" /> : <span className="campo-ayuda">Sin logo</span>}
        <label className="boton boton-secundario">
          Elegir imagen (PNG o JPG, máx. 1 MB)
          <input type="file" accept="image/png,image/jpeg" hidden onChange={(e) => e.target.files?.[0] && subir.mutate(e.target.files[0])} />
        </label>
        {empresa.data?.tieneLogo && (
          <button className="boton boton-secundario" onClick={() => subir.mutate(null)}>
            Quitar
          </button>
        )}
      </div>
    </div>
  )
}

function Licencia({ sistema }: { sistema: Sistema }) {
  const consultas = useQueryClient()
  const [clave, setClave] = useState('')
  const activar = useMutation({
    mutationFn: () => api.post('/api/sistema/licencia', { clave }),
    onSuccess: () => {
      setClave('')
      consultas.invalidateQueries({ queryKey: ['sistema'] })
    },
  })
  const l = sistema.licencia
  return (
    <div className="panel" id="licencia">
      <h2>Licencia</h2>
      {l.licenciada && l.licencia ? (
        <p>
          Licencia <b>{l.licencia.tipo.toLowerCase()}</b> a nombre de <b>{l.licencia.titular}</b>
          {l.licencia.rut ? ` (${formatearRut(l.licencia.rut)})` : ''}, emitida el {fecha(l.licencia.emitida)}. N° {l.licencia.id}. Pago único: sin
          vencimiento.
        </p>
      ) : (
        <>
          <p>
            Estás usando la versión de prueba ({l.cotizacionesUsadas} de {l.limitePrueba} cotizaciones; los PDF llevan marca de agua). Pega aquí
            la clave que recibiste al comprar.
          </p>
          <MensajeError error={activar.error} />
          <div className="fila">
            <textarea rows={2} value={clave} onChange={(e) => setClave(e.target.value)} placeholder="TH1.…" aria-label="Clave de licencia" />
            <button className="boton" disabled={!clave.trim() || activar.isPending} onClick={() => activar.mutate()}>
              Activar
            </button>
          </div>
        </>
      )}
    </div>
  )
}

function Respaldos({ carpeta }: { carpeta?: string }) {
  const consultas = useQueryClient()
  const lista = useQuery({ queryKey: ['respaldos'], queryFn: () => api.get<Respaldo[]>('/api/respaldos') })
  const crear = useMutation({
    mutationFn: () => api.post<{ nombre: string }>('/api/respaldos'),
    onSuccess: (r) => {
      consultas.invalidateQueries({ queryKey: ['respaldos'] })
      window.location.href = `/api/respaldos/${encodeURIComponent(r.nombre)}`
    },
  })
  const restaurar = useMutation({
    mutationFn: async (archivo: File) => {
      const datos = new FormData()
      datos.append('archivo', archivo)
      const r = await fetch('/api/respaldos/restaurar', { method: 'POST', body: datos, credentials: 'same-origin' })
      if (!r.ok) throw new Error((await r.json()).detail ?? 'No se pudo restaurar')
      return (await r.json()) as { respaldoPrevio: string }
    },
    onSuccess: (r) => {
      consultas.invalidateQueries()
      alert(`Respaldo restaurado. Por si acaso, tus datos anteriores quedaron en ${r.respaldoPrevio}.`)
    },
  })
  return (
    <div className="panel">
      <h2>Respaldos</h2>
      <p>
        Tratohecho crea un respaldo automático cada vez que se abre (guarda los últimos 7)
        {carpeta ? (
          <>
            {' '}
            en <code>{carpeta}/respaldos</code>
          </>
        ) : null}
        . Copia un respaldo a un pendrive o a la nube de vez en cuando: si el computador falla, es la única forma de recuperar tus datos.
      </p>
      <MensajeError error={crear.error || restaurar.error} />
      <div className="acciones">
        <button className="boton" onClick={() => crear.mutate()} disabled={crear.isPending}>
          Crear y descargar respaldo
        </button>
        <label className="boton boton-secundario">
          Restaurar desde archivo…
          <input
            type="file"
            accept=".zip"
            hidden
            onChange={(e) => {
              const archivo = e.target.files?.[0]
              if (archivo && confirm('Se reemplazarán los datos actuales por los del respaldo (antes se guarda una copia). ¿Continuar?')) {
                restaurar.mutate(archivo)
              }
              e.target.value = ''
            }}
          />
        </label>
      </div>
      {lista.data && lista.data.length > 0 && (
        <ul className="lista-simple">
          {lista.data.map((r) => (
            <li key={r.nombre}>
              <a href={`/api/respaldos/${encodeURIComponent(r.nombre)}`}>{r.nombre}</a> · {(r.bytes / 1024).toFixed(0)} KB · {fechaHora(r.creado)}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
