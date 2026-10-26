// Cálculos de la pestaña Estadísticas (los mismos que Estadisticas.kt en Android).
import type { Clase, Estado, Materia, Progreso } from './tipos'
import { minutos } from './util'

export interface PeriodoStats { numero: number; aprobados: number; enCurso: number; total: number }

export interface Estadisticas {
  promedio: number | null
  materiasConNota: number
  indice: number | null
  credAprobados: number
  credEnCurso: number
  credTotal: number
  horasSemana: number
  horasPorDia: [number, number][] // [día 1–7, horas]; lunes a sábado siempre, domingo solo si hay
  letras: [string, number][]
  letrasDesdeNota: boolean
  periodos: PeriodoStats[]
  mejores: [string, number][]
  reforzar: [string, number][]
  periodosFaltan: number
  graduacion: Date | null // estimado: un período cada 4 meses
}

const LETRAS = ['A', 'B', 'C', 'D', 'F']

/** "B+" → "B"; sin letra, se deduce de la nota (90 A, 80 B, 70 C, 60 D, menos F). W, R… no cuentan. */
export function letraBase(letra: string | null, nota: number | null): string | null {
  const l = letra?.trim().toUpperCase()[0]
  if (l) return LETRAS.includes(l) ? l : null
  if (nota == null) return null
  return nota >= 90 ? 'A' : nota >= 80 ? 'B' : nota >= 70 ? 'C' : nota >= 60 ? 'D' : 'F'
}

export function calcular(
  materias: Materia[], estados: Record<string, Estado>, notas: Record<string, Progreso>,
  indice: number | null, clases: Clase[], hoy = new Date(),
): Estadisticas {
  const estado = (m: Materia) => estados[m.id] ?? 'pendiente'
  const suma = (l: Materia[]) => l.reduce((s, m) => s + m.creditos, 0)
  const conNota = materias.flatMap((m) => (notas[m.id]?.nota != null ? [[m, notas[m.id].nota!] as [Materia, number]] : []))

  const letras: Record<string, number> = {}
  let desdeNota = false
  for (const m of materias) {
    const p = notas[m.id]
    if (!p) continue
    const l = letraBase(p.letra, p.nota)
    if (!l) continue
    if (!p.letra) desdeNota = true
    letras[l] = (letras[l] ?? 0) + 1
  }

  const porDia: Record<number, number> = {}
  for (const c of clases) porDia[c.dia] = (porDia[c.dia] ?? 0) + Math.max(minutos(c.hora_fin) - minutos(c.hora_inicio), 0) / 60
  const dias = [1, 2, 3, 4, 5, 6, ...((porDia[7] ?? 0) > 0 ? [7] : [])]

  const grupos = new Map<number, Materia[]>()
  for (const m of materias) grupos.set(m.cuatrimestre, [...(grupos.get(m.cuatrimestre) ?? []), m])
  const periodos = [...grupos.entries()].sort((a, b) => a[0] - b[0]).map(([numero, l]) => ({
    numero,
    aprobados: suma(l.filter((m) => estado(m) === 'aprobada')),
    enCurso: suma(l.filter((m) => estado(m) === 'en_curso')),
    total: suma(l),
  }))
  const faltan = new Set(materias.filter((m) => estado(m) !== 'aprobada').map((m) => m.cuatrimestre)).size

  const ordenadas = [...conNota].sort((a, b) => b[1] - a[1])
  const graduacion = faltan > 0 && materias.length ? new Date(hoy.getFullYear(), hoy.getMonth() + 4 * faltan, 1) : null

  return {
    promedio: conNota.length ? conNota.reduce((s, [, n]) => s + n, 0) / conNota.length : null,
    materiasConNota: conNota.length,
    indice,
    credAprobados: suma(materias.filter((m) => estado(m) === 'aprobada')),
    credEnCurso: suma(materias.filter((m) => estado(m) === 'en_curso')),
    credTotal: suma(materias),
    horasSemana: Object.values(porDia).reduce((s, h) => s + h, 0),
    horasPorDia: dias.map((d) => [d, porDia[d] ?? 0]),
    letras: LETRAS.map((l) => [l, letras[l] ?? 0]),
    letrasDesdeNota: desdeNota,
    periodos,
    mejores: ordenadas.slice(0, 3).map(([m, n]) => [m.nombre, n]),
    reforzar: [...ordenadas].reverse().filter(([, n]) => n < 80).slice(0, 3).map(([m, n]) => [m.nombre, n]),
    periodosFaltan: faltan,
    graduacion,
  }
}
