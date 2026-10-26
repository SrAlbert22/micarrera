// Solo para desarrollo: Estudio y Amigos con la base de datos simulada (sin sesión real).
import { createRoot } from 'react-dom/client'
import { useState } from 'react'
import { FunctionsClient } from '@supabase/functions-js'
import { supabase } from './supabase'
import './index.css'
import Estudio from './pantallas/Estudio'
import Amigos from './pantallas/Amigos'
import { GuiaCinco } from './cinco'

const YO = 'yo-123'
const dia = ((new Date().getDay() + 6) % 7) + 1
const h = (d: number) => { const x = new Date(); x.setHours(x.getHours() + d, 0, 0, 0); return x.toISOString() }
const hh = (d: number) => `${String((new Date().getHours() + d + 24) % 24).padStart(2, '0')}:00:00`

const DATOS: Record<string, unknown> = {
  perfiles: { id: YO, usuario: 'albert', nombre: 'Albert', logros_visibles: true, tutorial_visto: true },
  tareas: [
    { id: 't1', titulo: 'Laboratorio 3: Firewall', materia: 'Seguridad de Redes', detalle: 'Configurar reglas en pfSense', entrega: h(-5), hecha: false, origen: 'aula' },
    { id: 't2', titulo: 'Cuestionario unidad 2', materia: 'Criptografía', detalle: null, entrega: h(3), hecha: false, origen: 'aula' },
    { id: 't3', titulo: 'Leer capítulo 4', materia: 'Ética', detalle: null, entrega: h(50), hecha: false, origen: 'manual' },
    { id: 't4', titulo: 'Proyecto final', materia: 'Programación', detalle: null, entrega: h(24 * 12), hecha: false, origen: 'manual' },
    { id: 't5', titulo: 'Resumen', materia: null, detalle: null, entrega: h(-30), hecha: true, origen: 'manual' },
  ],
  aulas_virtuales: { estado: 'ok', ultimo_error: null, sincronizada: h(0) },
  horario: [{ materia: 'Criptografía', dia, hora_inicio: hh(2), hora_fin: hh(4) }],
  interacciones: [
    { id: 'i1', de: 'a1', para: YO, tipo: 'invitacion', materia: 'Criptografía', cuando: h(26), mensaje: '¿En la biblioteca?', respuesta: null, creada: h(-1) },
    { id: 'i2', de: 'a2', para: YO, tipo: 'choca5', materia: 'Redes I', cuando: null, mensaje: null, respuesta: null, creada: h(-3) },
    { id: 'i3', de: YO, para: 'a3', tipo: 'invitacion', materia: 'Ética', cuando: h(5), mensaje: null, respuesta: 'si', creada: h(-2) },
  ],
}
const RPC: Record<string, unknown> = {
  ranking_concentracion: [
    { id: 'a1', nombre: 'María Rodríguez', usuario: 'maria', minutos: 185, soy_yo: false },
    { id: YO, nombre: 'Albert', usuario: 'albert', minutos: 125, soy_yo: true },
    { id: 'a2', nombre: 'José Pérez', usuario: 'jose', minutos: 50, soy_yo: false },
  ],
  mis_amigos: [
    { id: 'a1', nombre: 'María Rodríguez', usuario: 'maria', universidad: 'ITLA', carrera: 'Ciberseguridad', amistad: 'm1', estado: 'aceptada', enviada_por_mi: false, avance: 34 },
    { id: 'a2', nombre: 'José Pérez', usuario: 'jose', universidad: 'ITLA', carrera: 'Redes', amistad: 'm2', estado: 'aceptada', enviada_por_mi: true, avance: 12 },
    { id: 'a3', nombre: 'Ana Gómez', usuario: 'ana', universidad: 'ITLA', carrera: 'Ciberseguridad', amistad: 'm3', estado: 'aceptada', enviada_por_mi: true, avance: 50 },
  ],
  sugerencias_amigos: [],
  horarios_de: [
    { usuario_id: 'a1', materia: 'Criptografía', dia, hora_inicio: hh(2), hora_fin: hh(4) },
    { usuario_id: 'a2', materia: 'Redes I', dia, hora_inicio: hh(-1), hora_fin: hh(1) },
    { usuario_id: 'a3', materia: 'Ética', dia: (dia % 7) + 1, hora_inicio: '08:00:00', hora_fin: '10:00:00' },
  ],
  logros_amigos: [
    { amigo: 'a1', nombre: 'María Rodríguez', usuario: 'maria', materia: 'Cálculo I', fecha: h(-20), ya_chocaste: false },
    { amigo: 'a3', nombre: 'Ana Gómez', usuario: 'ana', materia: 'Inglés II', fecha: h(-40), ya_chocaste: true },
  ],
}

function consulta(tabla: string) {
  let uno = false
  const q: Record<string, unknown> = {}
  for (const m of ['select', 'eq', 'neq', 'in', 'order', 'limit', 'insert', 'update', 'delete', 'upsert', 'gte', 'lte', 'is'])
    q[m] = () => q
  q.single = q.maybeSingle = () => { uno = true; return q }
  q.then = (ok: (r: unknown) => unknown) => {
    const d = DATOS[tabla] ?? (uno ? { id: 'nuevo' } : [])
    ;(window as unknown as { ultima: unknown }).ultima = tabla
    return Promise.resolve({ data: uno && Array.isArray(d) ? d[0] ?? { id: 'nuevo' } : d, error: null }).then(ok)
  }
  return q
}
const s = supabase as unknown as Record<string, unknown>
s.from = consulta
s.rpc = async (f: string) => ({ data: RPC[f] ?? null, error: null })
;(supabase.auth as unknown as Record<string, unknown>).getSession = async () => ({ data: { session: { user: { id: YO } } } })
FunctionsClient.prototype.invoke = (async () => { await new Promise((r) => setTimeout(r, 800)); return { data: { nuevas: 1, total: 2 }, error: null } }) as never

const uni = { id: 'u1', nombre: 'Instituto Tecnológico de Las Américas', siglas: 'ITLA', logo_url: '/universidades/itla.png', aula_url: 'https://aulavirtual.itla.edu.do/calendar/export.php' }

function Demo() {
  const modo = new URLSearchParams(location.search).get('ver') ?? 'estudio'
  const [p, setP] = useState<'horario' | 'estudio' | 'pensum' | 'estadisticas' | 'cuenta'>('estudio')
  return (
    <div className="app con-nav">
      {modo === "amigos" ? <Amigos onCerrar={() => {}} /> : <Estudio universidad={uni as never} />}
      <GuiaCinco pestana={p} irA={setP} reinicio={0} tutorialVisto onTutorialVisto={() => {}} />
    </div>
  )
}
createRoot(document.getElementById('root')!).render(<Demo />)
