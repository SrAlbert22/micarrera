// Solo para desarrollo: "Agrega tu carrera" sin sesión, con la IA y la base de datos simuladas.
import { createRoot } from 'react-dom/client'
import { FunctionsClient } from '@supabase/functions-js'
import { supabase } from './supabase'
import './index.css'
import AgregarCarrera from './pantallas/AgregarCarrera'
import CompletarPerfil from './pantallas/CompletarPerfil'
import { GuiaCinco } from './cinco'
import { useState } from 'react'

const materias = [
  ['ESP-101', 'Lengua Española I', 3, 1, []], ['MAT-101', 'Matemática Básica', 4, 1, []], ['INF-101', 'Introducción a la Informática', 3, 1, []],
  ['ORI-100', 'Orientación Universitaria', 1, 1, []], ['ING-101', 'Inglés I', 3, 1, []],
  ['ESP-102', 'Lengua Española II', 3, 2, ['ESP-101']], ['MAT-201', 'Cálculo I', 4, 2, ['MAT-101']], ['INF-201', 'Programación I', 4, 2, ['INF-101']],
  ['ING-102', 'Inglés II', 3, 2, ['ING-101']], ['HIS-101', 'Historia Dominicana', 3, 2, []],
  ['MAT-202', 'Cálculo II', 4, 3, ['MAT-201']], ['INF-202', 'Programación II', 4, 3, ['INF-201']], ['INF-210', 'Base de Datos I', 4, 3, ['INF-201']],
].map(([codigo, nombre, creditos, periodo, prerequisitos]) => ({ codigo, nombre, creditos, periodo, prerequisitos }))

FunctionsClient.prototype.invoke = (async () => {
  await new Promise((r) => setTimeout(r, 1500))
  return { data: { carrera: 'Licenciatura en Informática', materias }, error: null }
}) as never
;(supabase as unknown as { rpc: unknown }).rpc = async (_f: string, args: unknown) => {
  ;(window as unknown as { guardado: unknown }).guardado = args
  if (_f === 'usuario_disponible') return { data: (args as { u: string }).u !== 'maria.rodriguez', error: null }
  return { data: { id: 'demo-123', existente: false }, error: null }
}

const uni = { id: 'u1', nombre: 'Universidad Dominicana O&M', siglas: 'O&M', logo_url: '/universidades/om.png' }
function DemoCinco() {
  const [p, setP] = useState<'horario' | 'estudio' | 'pensum' | 'estadisticas' | 'cuenta'>('horario')
  try { localStorage.removeItem('cinco') } catch { /* nada */ }
  return (
    <div className="app con-nav">
      <div className="titulo-pantalla"><h1>{p}</h1></div>
      <GuiaCinco pestana={p} irA={setP} reinicio={0} tutorialVisto={false} onTutorialVisto={() => {}} />
      <nav className="nav"><div className="nav-interior">
        {(['horario', 'estudio', 'pensum', 'estadisticas', 'cuenta'] as const).map((x) => (
          <button key={x} data-guia={x} className={p === x ? 'activa' : ''} onClick={() => setP(x)}><span className="icono-nav">•</span>{x}</button>
        ))}
      </div></nav>
    </div>
  )
}

createRoot(document.getElementById('root')!).render(location.search.includes('cinco') ? <DemoCinco /> : location.search.includes('perfil')
  ? <CompletarPerfil usuarioActual={null} onListo={() => { document.title = 'listo' }} />
  : <AgregarCarrera universidad={uni} onLista={(id) => { document.title = 'guardada:' + id }} onCancelar={() => { document.title = 'cancelado' }} />,
)
