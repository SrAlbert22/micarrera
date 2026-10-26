// Test vocacional "¿Qué debo estudiar?": modelo RIASEC de Holland (lo mismo que Vocacional.kt en Android).
// Cada pregunta suma de 0 a 3 a uno de los 6 tipos; las carreras tienen un tipo principal y uno secundario.
import type { CarreraVocacional, PreguntaVocacional, TipoVocacional } from './tipos'

export const ORDEN: TipoVocacional[] = ['R', 'I', 'A', 'S', 'E', 'C']

export const TIPOS: Record<TipoVocacional, { nombre: string; emoji: string; descripcion: string }> = {
  R: { nombre: 'Hacedor', emoji: '🔧', descripcion: 'Te gusta trabajar con las manos, usar herramientas y ver resultados concretos.' },
  I: { nombre: 'Investigador', emoji: '🔬', descripcion: 'Te gusta analizar, resolver problemas y entender cómo funcionan las cosas.' },
  A: { nombre: 'Creativo', emoji: '🎨', descripcion: 'Te gusta crear, imaginar y expresarte con tu propio estilo.' },
  S: { nombre: 'Social', emoji: '🤝', descripcion: 'Te gusta ayudar, enseñar y trabajar con personas.' },
  E: { nombre: 'Emprendedor', emoji: '🚀', descripcion: 'Te gusta liderar, convencer y sacar adelante proyectos.' },
  C: { nombre: 'Organizador', emoji: '📋', descripcion: 'Te gusta el orden, los datos y hacer las cosas con precisión.' },
}

export const RESPUESTAS = [
  { valor: 0, texto: 'No me gusta', emoji: '👎' },
  { valor: 1, texto: 'Me da igual', emoji: '😐' },
  { valor: 2, texto: 'Me gusta', emoji: '🙂' },
  { valor: 3, texto: '¡Me encanta!', emoji: '😍' },
] as const

export type Puntajes = Record<TipoVocacional, number>

/** Puntaje de cada tipo de 0 a 1 (lo que sumó / lo máximo que podía sumar). */
export function puntajes(preguntas: PreguntaVocacional[], respuestas: Record<number, number>): Puntajes {
  const r = { R: 0, I: 0, A: 0, S: 0, E: 0, C: 0 } as Puntajes
  for (const t of ORDEN) {
    const suyas = preguntas.filter((p) => p.tipo === t)
    const suma = suyas.reduce((s, p) => s + (respuestas[p.id] ?? 0), 0)
    r[t] = suyas.length ? suma / (3 * suyas.length) : 0
  }
  return r
}

/** Los 6 tipos de mayor a menor (en empate, el orden RIASEC). */
export const ranking = (p: Puntajes): TipoVocacional[] => [...ORDEN].sort((a, b) => p[b] - p[a])

/** Afinidad 0–100 con una carrera: su tipo principal pesa el doble que el secundario. */
export function afinidad(c: CarreraVocacional, p: Puntajes): number {
  const [t1, t2] = c.tipos.split('') as TipoVocacional[]
  return Math.round((100 * (2 * (p[t1] ?? 0) + (p[t2] ?? 0))) / 3)
}

/** Las [n] carreras que más encajan (en empate, gana la que empieza con tu tipo más fuerte). */
export function recomendar(carreras: CarreraVocacional[], p: Puntajes, n = 6) {
  const primero = ranking(p)[0]
  return carreras
    .map((c) => ({ carrera: c, afinidad: afinidad(c, p) }))
    .sort((a, b) => b.afinidad - a.afinidad
      || Number(b.carrera.tipos[0] === primero) - Number(a.carrera.tipos[0] === primero)
      || a.carrera.nombre.localeCompare(b.carrera.nombre))
    .slice(0, n)
}

/** Universidades que ofrecen más de tus carreras recomendadas (siglas y cuántas). */
export function universidadesTop(recomendadas: { carrera: CarreraVocacional }[], n = 3) {
  const cuenta = new Map<string, number>()
  for (const { carrera } of recomendadas) for (const s of carrera.universidades) cuenta.set(s, (cuenta.get(s) ?? 0) + 1)
  return [...cuenta.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0])).slice(0, n)
    .map(([siglas, cuantas]) => ({ siglas, cuantas }))
}
