import { useEffect, useState, type ReactNode } from 'react'
import * as repo from '../repo'
import type { Carrera, Universidad } from '../tipos'
import { Cargando, Icono, LogoUniversidad } from '../comunes'
import AgregarCarrera from './AgregarCarrera'

export default function ElegirCarrera({ onElegida }: { onElegida: (id: string) => void }) {
  const [universidades, setUniversidades] = useState<Universidad[]>([])
  const [carreras, setCarreras] = useState<Carrera[]>([])
  const [uni, setUni] = useState<Universidad | null>(null)
  const [cargando, setCargando] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [agregando, setAgregando] = useState(false)

  useEffect(() => {
    repo.universidades().then(setUniversidades)
      .catch(() => setError('No se pudieron cargar las universidades. Revisa tu internet.'))
      .finally(() => setCargando(false))
  }, [])

  useEffect(() => {
    if (!uni) return
    setCargando(true)
    repo.carreras(uni.id).then(setCarreras).catch(() => setCarreras([])).finally(() => setCargando(false))
  }, [uni])

  async function elegir(id: string) {
    await repo.guardarCarrera(id).catch(() => {})
    onElegida(id)
  }

  if (uni && agregando) return <AgregarCarrera universidad={uni} onLista={elegir} onCancelar={() => setAgregando(false)} />

  return (
    <div className="app ancha">
      <div className="titulo-pantalla" style={{ paddingLeft: uni ? 8 : 20 }}>
        {uni && (
          <button className="boton-texto" style={{ color: 'var(--texto)' }} onClick={() => { setUni(null); setCarreras([]) }} aria-label="Volver">
            <Icono n="atras" />
          </button>
        )}
        {uni && <LogoUniversidad u={uni} tam={44} />}
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0, fontSize: 26 }}>{uni ? '¿Qué estudias?' : '¿Dónde estudias?'}</h1>
          <div className="suave" style={{ fontSize: 14 }}>{uni?.nombre ?? 'Elige tu universidad para empezar'}</div>
        </div>
      </div>

      <div className="contenido" style={{ paddingBottom: 32, gap: 10 }}>
        {error && <div className="aviso">{error}</div>}
        {cargando ? <Cargando /> : !uni ? (
          <div className="rejilla chica">
            {universidades.map((u) => (
              <Opcion key={u.id} inicial={<LogoUniversidad u={u} tam={48} />} titulo={u.siglas ?? u.nombre}
                subtitulo={u.siglas ? u.nombre : undefined} onClick={() => setUni(u)} />
            ))}
          </div>
        ) : carreras.length ? (
          <div className="rejilla chica">
            {carreras.map((c) => <Opcion key={c.id} inicial="📘" titulo={c.nombre}
              subtitulo={c.aportada_por ? '👥 Aportada por estudiantes' : undefined} onClick={() => elegir(c.id)} />)}
            <button className="tarjeta clic" onClick={() => setAgregando(true)}
              style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 14, textAlign: 'left', width: '100%', borderStyle: 'dashed' }}>
              <span style={{ width: 48, height: 48, borderRadius: '50%', background: 'var(--acento-suave)', color: 'var(--acento)',
                display: 'grid', placeItems: 'center', flex: 'none' }}><Icono n="mas" tam={24} /></span>
              <span style={{ flex: 1 }}>
                <span style={{ display: 'block', fontWeight: 600, fontSize: 16 }}>¿No está tu carrera?</span>
                <span className="suave" style={{ fontSize: 13 }}>Agrégala subiendo tu pensum</span>
              </span>
            </button>
          </div>
        ) : (
          <div className="tarjeta" style={{ padding: 28, textAlign: 'center' }}>
            <div style={{ fontSize: 44 }}>📄✨</div>
            <h3 style={{ margin: '12px 0 6px' }}>Sé el primero de {uni.siglas ?? 'tu universidad'}</h3>
            <p className="suave" style={{ margin: '0 0 18px' }}>
              Todavía nadie ha agregado una carrera aquí. Sube el pensum de la tuya (foto, PDF o texto) y la IA lo organiza en segundos.
            </p>
            <button className="boton" onClick={() => setAgregando(true)}>Agregar mi carrera</button>
          </div>
        )}
      </div>
    </div>
  )
}

function Opcion({ inicial, titulo, subtitulo, onClick }: { inicial: ReactNode; titulo: string; subtitulo?: string; onClick: () => void }) {
  return (
    <button className="tarjeta clic" onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 14, textAlign: 'left', width: '100%' }}>
      {typeof inicial === 'string' ? (
        <span style={{ width: 48, height: 48, borderRadius: '50%', background: 'var(--acento-suave)', color: 'var(--acento)',
          display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: 20, flex: 'none' }}>{inicial}</span>
      ) : inicial}
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: 600, fontSize: 16 }}>{titulo}</span>
        {subtitulo && <span className="suave" style={{ display: 'block', fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{subtitulo}</span>}
      </span>
      <Icono n="derecha" color="var(--gris)" />
    </button>
  )
}
