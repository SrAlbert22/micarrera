// Lógica de Amigos (la misma que Amigos.kt en Android).
import type { Clase } from './tipos'
import { diaDeHoy, minutos } from './util'

/** Una persona en Amigos (de mis_amigos, sugerencias_amigos o buscar_usuarios). Nunca trae notas ni índice. */
export interface Persona {
  id: string
  nombre: string | null
  usuario: string | null
  universidad: string | null
  carrera: string | null
  amistad?: string
  estado?: 'pendiente' | 'aceptada'
  enviada_por_mi?: boolean
  avance?: number | null
  en_comun?: number
}

export interface HuecoLibre { dia: number; desde: string; hasta: string }

export const FORMATO_USUARIO = /^[a-z0-9._]{3,20}$/
export const limpiarUsuario = (t: string) => t.trim().replace(/^@/, '').toLowerCase()

export const nombreVisible = (p: Persona) => p.nombre?.trim() || (p.usuario ? `@${p.usuario}` : 'Estudiante')
export const iniciales = (p: Persona) =>
  nombreVisible(p).replace(/^@/, '').split(/[\s._-]+/).filter(Boolean).slice(0, 2).map((x) => x[0].toUpperCase()).join('') || '?'

const cod = (c: Clase) => (c.codigo ?? '').toUpperCase().replace(/[-\s]/g, '')
const hora = (m: number) => `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`

/** Misma clase: mismo día y hora; con código en las dos → mismo código y grupo; si no, mismo nombre. */
export function mismaClase(a: Clase, b: Clase): boolean {
  if (a.dia !== b.dia || a.hora_inicio.slice(0, 5) !== b.hora_inicio.slice(0, 5)) return false
  if (cod(a) && cod(b)) return cod(a) === cod(b) && (!a.grupo?.trim() || !b.grupo?.trim() || a.grupo.trim() === b.grupo.trim())
  return a.materia.trim().toLowerCase() === b.materia.trim().toLowerCase()
}

export const clasesEnComun = (mias: Clase[], suyas: Clase[]) =>
  mias.filter((m) => suyas.some((s) => mismaClase(m, s)))
    .sort((a, b) => a.dia - b.dia || minutos(a.hora_inicio) - minutos(b.hora_inicio))

/** Huecos en que los dos están libres (lunes a sábado, 7 am–10 pm, de al menos una hora), solo días con clases. */
export function horasLibresEnComun(mias: Clase[], suyas: Clase[], desde = 7 * 60, hasta = 22 * 60, minimo = 60): HuecoLibre[] {
  const todas = [...mias, ...suyas]
  const huecos: HuecoLibre[] = []
  for (let dia = 1; dia <= 6; dia++) {
    const ocupado = todas.filter((c) => c.dia === dia)
      .map((c) => [Math.max(minutos(c.hora_inicio), desde), Math.min(minutos(c.hora_fin), hasta)] as const)
      .filter(([i, f]) => i < f).sort((a, b) => a[0] - b[0])
    if (!ocupado.length) continue
    let cursor = desde
    for (const [i, f] of ocupado) {
      if (i - cursor >= minimo) huecos.push({ dia, desde: hora(cursor), hasta: hora(i) })
      cursor = Math.max(cursor, f)
    }
    if (hasta - cursor >= minimo) huecos.push({ dia, desde: hora(cursor), hasta: hora(hasta) })
  }
  return huecos
}

export type Ahora = { libre: true; proxima: Clase | null } | { libre: false; clase: Clase }

/** Si alguien está en clase en este momento o libre (y cuál es su próxima clase de hoy). */
export function estadoAhora(clases: Clase[], ahora = new Date()): Ahora {
  const dia = diaDeHoy(ahora), m = ahora.getHours() * 60 + ahora.getMinutes()
  const hoy = clases.filter((c) => c.dia === dia).sort((a, b) => minutos(a.hora_inicio) - minutos(b.hora_inicio))
  const clase = hoy.find((c) => minutos(c.hora_inicio) <= m && m < minutos(c.hora_fin))
  if (clase) return { libre: false, clase }
  return { libre: true, proxima: hoy.find((c) => minutos(c.hora_inicio) > m) ?? null }
}

export const amigosEnClase = (clase: Clase, horarios: Map<Persona, Clase[]>) =>
  [...horarios.entries()].filter(([, suyas]) => suyas.some((s) => mismaClase(clase, s))).map(([p]) => p)

export const OPCIONES_BUSCABLE = [
  ['universidad', 'Todos', 'Te encuentran por tu nombre o @usuario, y apareces en "Quizás conozcas" de tu universidad.'],
  ['usuario', 'Solo con mi @usuario', 'No apareces en sugerencias; te encuentra quien sepa tu usuario.'],
  ['nadie', 'Nadie', 'Nadie te encuentra. Tus amigos actuales se quedan.'],
] as const
export const OPCIONES_HORARIO = [
  ['amigos', 'Mis amigos', 'Ven tu horario y las clases que tienen en común.'],
  ['nadie', 'Nadie', 'Nadie ve tu horario, ni tus amigos.'],
] as const

/** Un @usuario a partir del nombre: "María Rodríguez" -> "maria.rodriguez" (sin tildes, 3 a 20 caracteres). */
export function sugerirUsuario(nombre: string): string {
  const partes = nombre.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9\s]/g, '').split(/\s+/).filter(Boolean)
  const base = partes.slice(0, 2).join('.').slice(0, 20).replace(/\.$/, '')
  return base.length >= 3 ? base : ''
}
