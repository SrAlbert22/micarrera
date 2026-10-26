// Solo para desarrollo: la pestaña Estadísticas con datos inventados.
import { createRoot } from 'react-dom/client'
import './index.css'
import { calcular } from './estadisticas'
import { VistaEstadisticas } from './pantallas/Estadisticas'
import type { Materia, Progreso } from './tipos'

const m = (id: string, nombre: string, cuatrimestre: number, creditos = 4): Materia =>
  ({ id, carrera_id: 'c', codigo: `COD${id}`, nombre, creditos, cuatrimestre, prerequisitos: [] })
const materias = [
  m('1', 'Introducción a la Ciberseguridad', 1), m('2', 'Matemática Básica', 1), m('3', 'Lengua Española I', 1, 3),
  m('4', 'Fundamentos de Redes', 1), m('5', 'Algoritmos', 2), m('6', 'Inglés Nivel 4-6', 2, 0), m('7', 'Cálculo Diferencial', 2),
  m('8', 'Redes de Datos II', 3), m('9', 'Seguridad de la Información', 3, 3), m('10', 'Sistemas Operativos III', 3, 3),
  m('11', 'Hácker Ético I', 4), m('12', 'Redes de Datos III', 4),
]
const estados = { 1: 'aprobada', 2: 'aprobada', 3: 'aprobada', 4: 'aprobada', 5: 'aprobada', 6: 'aprobada', 7: 'en_curso', 8: 'en_curso', 10: 'aprobada' } as const
const p = (id: string, nota: number, letra: string): Progreso => ({ usuario_id: 'u', materia_id: id, estado: 'aprobada', nota, letra, origen: 'portal' })
const notas = { 1: p('1', 93, 'A'), 2: p('2', 78, 'C+'), 3: p('3', 87, 'B'), 4: p('4', 90, 'A'), 5: p('5', 73, 'C'), 10: p('10', 93, 'A') }
const clases = [
  { materia: 'Inglés', dia: 3, hora_inicio: '14:00', hora_fin: '17:00' },
  { materia: 'Redes II', dia: 4, hora_inicio: '14:00', hora_fin: '18:00' },
  { materia: 'Cálculo', dia: 5, hora_inicio: '14:00', hora_fin: '18:00' },
  { materia: 'Seguridad', dia: 1, hora_inicio: '08:00', hora_fin: '10:00' },
]
const e = calcular(materias, estados as never, notas as never, 2.7, clases)
createRoot(document.getElementById('root')!).render(<div className="app con-nav"><VistaEstadisticas e={e} /><nav className="nav"><div className="nav-interior"><div className="nav-marca"><img src="/pwa-192.png" alt="" />MiCarrera</div></div></nav></div>)
