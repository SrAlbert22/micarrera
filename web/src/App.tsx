import { useEffect, useState } from 'react'
import type { Session } from '@supabase/supabase-js'
import { supabase } from './supabase'
import * as repo from './repo'
import { Cargando, Icono, type NombreIcono } from './comunes'
import Login from './pantallas/Login'
import ElegirCarrera from './pantallas/ElegirCarrera'
import Pensum from './pantallas/Pensum'
import Horario from './pantallas/Horario'
import Cuenta, { type EventoInstalar } from './pantallas/Cuenta'
import Estadisticas from './pantallas/Estadisticas'
import Amigos from './pantallas/Amigos'
import Estudio from './pantallas/Estudio'
import type { Universidad } from './tipos'
import TestVocacional, { abiertoConEnlace } from './pantallas/TestVocacional'
import CompletarPerfil from './pantallas/CompletarPerfil'
import { GuiaCinco, reiniciarCinco } from './cinco'
import { AvisoInvitacion } from './invitar'
import { DialogoContrasena, debePedirContrasena, marcarPedirContrasena } from './contrasena'

type Pestana = 'horario' | 'estudio' | 'pensum' | 'estadisticas' | 'cuenta'
const PESTANAS: { id: Pestana; titulo: string; icono: NombreIcono }[] = [
  { id: 'horario', titulo: 'Horario', icono: 'horario' },
  { id: 'estudio', titulo: 'Estudio', icono: 'estudio' },
  { id: 'pensum', titulo: 'Pensum', icono: 'lista' },
  { id: 'estadisticas', titulo: 'Estadísticas', icono: 'grafica' },
  { id: 'cuenta', titulo: 'Cuenta', icono: 'persona' },
]

export default function App() {
  const [sesion, setSesion] = useState<Session | null | undefined>(undefined)
  const [instalar, setInstalar] = useState<EventoInstalar | null>(null)

  useEffect(() => {
    supabase.auth.getSession().then(({ data }) => setSesion(data.session))
    const { data } = supabase.auth.onAuthStateChange((_evento, s) => setSesion(s))
    // Chrome/Edge/Android avisan que la web se puede instalar: lo guardamos para el botón de Cuenta.
    const alPoder = (e: Event) => { e.preventDefault(); setInstalar(e as EventoInstalar) }
    window.addEventListener('beforeinstallprompt', alPoder)
    return () => { data.subscription.unsubscribe(); window.removeEventListener('beforeinstallprompt', alPoder) }
  }, [])

  if (sesion === undefined) return <Cargando />
  if (!sesion) return <Login />
  return <Inicio key={sesion.user.id} instalar={instalar} />
}

function Inicio({ instalar }: { instalar: EventoInstalar | null }) {
  const [carreraId, setCarreraId] = useState<string | null | undefined>(undefined)
  const [pedirContrasena, setPedirContrasena] = useState(debePedirContrasena)
  const [verAmigos, setVerAmigos] = useState(false)
  const [verTest, setVerTest] = useState(abiertoConEnlace)
  const [faltaNombre, setFaltaNombre] = useState<{ usuario: string | null } | null>(null)
  const [reinicioCinco, setReinicioCinco] = useState(0)
  const [tutorialVisto, setTutorialVisto] = useState(true)
  const [universidad, setUniversidad] = useState<Universidad | null>(null)
  const [pestana, setPestana] = useState<Pestana>(() => (sessionStorage.getItem('pestana') as Pestana) || 'horario')

  useEffect(() => {
    repo.perfil().then((p) => {
      setCarreraId(p?.carrera_id ?? null)
      setTutorialVisto(p?.tutorial_visto ?? true)
      if (p?.carrera_id) repo.carreraDetalle(p.carrera_id).then((c) => setUniversidad(c?.universidades ?? null)).catch(() => {})
      // Sin nombre tus amigos no te encuentran: lo pedimos una vez por sesión ("Ahora no" lo deja para después)
      let omitido = false
      try { omitido = sessionStorage.getItem('sinNombre') === '1' } catch { /* sin almacenamiento */ }
      if (!p?.nombre?.trim() && !omitido) setFaltaNombre({ usuario: p?.usuario ?? null })
    }).catch(() => setCarreraId(null))
  }, [])
  useEffect(() => { try { sessionStorage.setItem('pestana', pestana) } catch { /* sin almacenamiento */ } }, [pestana])

  const cambiarCarrera = () => { repo.guardarCarrera(null).catch(() => {}); setCarreraId(null) }

  if (carreraId === undefined) return <Cargando />
  if (verTest) return <TestVocacional onCerrar={() => setVerTest(false)} />
  if (faltaNombre) return <CompletarPerfil usuarioActual={faltaNombre.usuario} onListo={() => {
    try { sessionStorage.setItem('sinNombre', '1') } catch { /* sin almacenamiento */ }
    setFaltaNombre(null)
  }} />
  if (carreraId === null) return <ElegirCarrera onElegida={setCarreraId} />

  return (
    <div className="app con-nav">
      {verAmigos && <Amigos onCerrar={() => setVerAmigos(false)} />}
      {!verAmigos && pestana === 'horario' && <Horario onAmigos={() => setVerAmigos(true)} irA={(p) => { setPestana(p); window.scrollTo(0, 0) }} />}
      {!verAmigos && pestana === 'estudio' && <Estudio universidad={universidad} />}
      {!verAmigos && pestana === 'pensum' && <Pensum carreraId={carreraId} onCambiarCarrera={cambiarCarrera} />}
      {!verAmigos && pestana === 'estadisticas' && <Estadisticas carreraId={carreraId} />}
      {!verAmigos && pestana === 'cuenta' && <Cuenta carreraId={carreraId} onCambiarCarrera={cambiarCarrera} instalar={instalar} onAmigos={() => setVerAmigos(true)} onTest={() => setVerTest(true)}
        onCinco={() => { reiniciarCinco(); setReinicioCinco((n) => n + 1) }} />}

      <AvisoInvitacion />
      {!verAmigos && <GuiaCinco pestana={pestana} irA={setPestana} reinicio={reinicioCinco} tutorialVisto={tutorialVisto}
        onTutorialVisto={() => { setTutorialVisto(true); repo.marcarTutorialVisto().catch(() => {}) }} />}
      {pedirContrasena && (
        <DialogoContrasena primeraVez onCerrar={() => { setPedirContrasena(false); marcarPedirContrasena(false) }} />
      )}

      <nav className="nav">
        <div className="nav-interior">
          <div className="nav-marca"><img src="/pwa-192.png" alt="" />MiCarrera</div>
          {PESTANAS.map((p) => (
            <button key={p.id} data-guia={p.id} className={pestana === p.id ? 'activa' : ''} onClick={() => { setPestana(p.id); setVerAmigos(false); window.scrollTo(0, 0) }}>
              <span className="icono-nav"><Icono n={p.icono} tam={22} /></span>
              {p.titulo}
            </button>
          ))}
        </div>
      </nav>
    </div>
  )
}
