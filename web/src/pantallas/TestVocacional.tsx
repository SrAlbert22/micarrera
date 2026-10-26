import { useEffect, useMemo, useState, type ReactNode } from 'react'
import * as repo from '../repo'
import type { CarreraVocacional, PreguntaVocacional, Universidad } from '../tipos'
import { Cargando, Icono, LogoUniversidad } from '../comunes'
import { RESPUESTAS, TIPOS, puntajes, ranking, recomendar, universidadesTop } from '../vocacional'

/** ¿Se abrió con el enlace …/?test? */
export const abiertoConEnlace = () => new URLSearchParams(window.location.search).has('test')

function quitarEnlace() {
  const url = new URL(window.location.href)
  if (url.searchParams.has('test')) { url.searchParams.delete('test'); history.replaceState(null, '', url) }
}

const GUARDADO = 'testVocacional'
function leerGuardado(): Record<number, number> | null {
  try { return JSON.parse(localStorage.getItem(GUARDADO) ?? 'null') } catch { return null }
}

type Datos = { preguntas: PreguntaVocacional[]; carreras: CarreraVocacional[]; universidades: Map<string, Universidad> }

/**
 * "¿Qué debo estudiar?": 30 preguntas sobre lo que te gusta hacer → tu perfil (RIASEC),
 * las carreras que más encajan y en qué universidades de RD estudiarlas. No necesita cuenta.
 */
export default function TestVocacional({ onCerrar, onCrearCuenta }: { onCerrar: () => void; onCrearCuenta?: () => void }) {
  const [datos, setDatos] = useState<Datos | null>(null)
  const [error, setError] = useState(false)
  const [fase, setFase] = useState<'inicio' | 'preguntas' | 'resultado'>('inicio')
  const [actual, setActual] = useState(0)
  const [respuestas, setRespuestas] = useState<Record<number, number>>({})
  const [guardado] = useState(leerGuardado)

  useEffect(() => {
    Promise.all([repo.preguntasVocacionales(), repo.carrerasVocacionales(), repo.universidades()])
      .then(([preguntas, carreras, us]) =>
        setDatos({ preguntas, carreras, universidades: new Map(us.filter((u) => u.siglas).map((u) => [u.siglas!, u])) }))
      .catch(() => setError(true))
  }, [])

  const cerrar = () => { quitarEnlace(); onCerrar() }
  const empezar = () => { setRespuestas({}); setActual(0); setFase('preguntas'); window.scrollTo(0, 0) }

  function responder(valor: number) {
    if (!datos) return
    const p = datos.preguntas[actual]
    const nuevas = { ...respuestas, [p.id]: valor }
    setRespuestas(nuevas)
    setTimeout(() => {
      if (actual + 1 < datos.preguntas.length) setActual(actual + 1)
      else {
        try { localStorage.setItem(GUARDADO, JSON.stringify(nuevas)) } catch { /* sin almacenamiento */ }
        setFase('resultado'); window.scrollTo(0, 0)
      }
    }, 160)
  }

  // Con teclado (computadora): 1 a 4 para responder.
  useEffect(() => {
    if (fase !== 'preguntas') return
    const tecla = (e: KeyboardEvent) => { const n = Number(e.key); if (n >= 1 && n <= 4) responder(n - 1) }
    window.addEventListener('keydown', tecla)
    return () => window.removeEventListener('keydown', tecla)
  })

  return (
    <div className="pantalla-completa">
      <div className="app">
        {error ? (
          <Encabezado onAtras={cerrar} />
        ) : !datos ? <Cargando /> : fase === 'inicio' ? (
          <Inicio total={datos.preguntas.length} onEmpezar={empezar} onCerrar={cerrar}
            onVerAnterior={guardado ? () => { setRespuestas(guardado); setFase('resultado') } : undefined} />
        ) : fase === 'preguntas' ? (
          <Pregunta pregunta={datos.preguntas[actual]} numero={actual + 1} total={datos.preguntas.length}
            elegida={respuestas[datos.preguntas[actual].id]} onResponder={responder}
            onAtras={() => (actual > 0 ? setActual(actual - 1) : setFase('inicio'))} />
        ) : (
          <Resultado datos={datos} respuestas={respuestas} onRepetir={empezar} onCerrar={cerrar}
            onCrearCuenta={onCrearCuenta ? () => { quitarEnlace(); onCrearCuenta() } : undefined} />
        )}
        {error && (
          <div className="contenido">
            <div className="aviso">No se pudo cargar el test. Revisa tu internet e intenta otra vez.</div>
          </div>
        )}
      </div>
    </div>
  )
}

function Encabezado({ onAtras, icono = 'cerrar', children }: { onAtras: () => void; icono?: 'cerrar' | 'atras'; children?: ReactNode }) {
  return (
    <div className="titulo-pantalla" style={{ paddingLeft: 8 }}>
      <button className="boton-texto" style={{ color: 'var(--texto)' }} onClick={onAtras} aria-label={icono === 'cerrar' ? 'Cerrar' : 'Atrás'}>
        <Icono n={icono} tam={24} />
      </button>
      <div style={{ flex: 1 }}>{children}</div>
    </div>
  )
}

function Inicio({ total, onEmpezar, onCerrar, onVerAnterior }: {
  total: number; onEmpezar: () => void; onCerrar: () => void; onVerAnterior?: () => void
}) {
  return (
    <>
      <Encabezado onAtras={onCerrar} />
      <div className="contenido" style={{ paddingBottom: 32 }}>
        <div className="tarjeta" style={{ background: 'var(--degradado)', border: 0, color: '#fff', padding: '28px 22px', textAlign: 'center' }}>
          <div style={{ fontSize: 56 }}>🧭</div>
          <h1 style={{ margin: '8px 0 6px', fontSize: 28 }}>¿Qué debo estudiar?</h1>
          <p style={{ margin: 0, color: 'rgba(255,255,255,.85)', lineHeight: 1.5 }}>
            Responde {total} preguntas rápidas sobre lo que te gusta hacer. Al final te decimos qué carreras encajan
            contigo y en qué universidades de RD puedes estudiarlas.
          </p>
        </div>
        <div className="tarjeta lista-divisores">
          <Punto e="⏱️" t="Unos 5 minutos" d="Una pregunta a la vez. Puedes volver atrás." />
          <Punto e="🎯" t="No hay respuestas buenas ni malas" d="Piensa en lo que disfrutas, no en lo que se te da bien." />
          <Punto e="🔓" t="No necesitas cuenta" d="Tus respuestas se quedan en este dispositivo." />
        </div>
        <button className="boton ancho" onClick={onEmpezar}>Empezar el test</button>
        {onVerAnterior && <button className="boton-texto" onClick={onVerAnterior}>Ver mi último resultado</button>}
      </div>
    </>
  )
}

function Punto({ e, t, d }: { e: string; t: string; d: string }) {
  return (
    <div style={{ display: 'flex', gap: 14, padding: '14px 16px', alignItems: 'center' }}>
      <span style={{ fontSize: 24 }}>{e}</span>
      <span><strong style={{ display: 'block' }}>{t}</strong><span className="suave" style={{ fontSize: 14 }}>{d}</span></span>
    </div>
  )
}

function Pregunta({ pregunta, numero, total, elegida, onResponder, onAtras }: {
  pregunta: PreguntaVocacional; numero: number; total: number; elegida?: number; onResponder: (v: number) => void; onAtras: () => void
}) {
  return (
    <>
      <Encabezado onAtras={onAtras} icono="atras">
        <div className="suave" style={{ fontSize: 14, fontWeight: 600, marginBottom: 6 }}>Pregunta {numero} de {total}</div>
        <div style={{ height: 6, borderRadius: 99, background: 'var(--tarjeta-alta)', overflow: 'hidden', marginRight: 12 }}>
          <div style={{ width: `${((numero - 1) / total) * 100}%`, height: '100%', background: 'var(--acento)', transition: 'width .25s' }} />
        </div>
      </Encabezado>
      <div className="contenido" style={{ paddingBottom: 32, gap: 10 }}>
        <div style={{ padding: '28px 6px 20px' }} key={pregunta.id}>
          <div className="suave" style={{ fontSize: 15, fontWeight: 600 }}>¿Qué tanto te gustaría…</div>
          <h2 style={{ margin: '8px 0 0', fontSize: 26, lineHeight: 1.25 }}>{pregunta.texto}?</h2>
        </div>
        {RESPUESTAS.map((r, i) => {
          const sel = elegida === r.valor
          return (
            <button key={r.valor} className="tarjeta clic" onClick={() => onResponder(r.valor)}
              style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 18px', textAlign: 'left', fontSize: 17, fontWeight: 600,
                borderColor: sel ? 'var(--acento)' : undefined, background: sel ? 'var(--acento-suave)' : undefined }}>
              <span style={{ fontSize: 26 }}>{r.emoji}</span>
              <span style={{ flex: 1 }}>{r.texto}</span>
              <span className="suave mono tecla" style={{ fontSize: 12 }}>{i + 1}</span>
            </button>
          )
        })}
      </div>
    </>
  )
}

function Resultado({ datos, respuestas, onRepetir, onCerrar, onCrearCuenta }: {
  datos: Datos; respuestas: Record<number, number>; onRepetir: () => void; onCerrar: () => void; onCrearCuenta?: () => void
}) {
  const p = useMemo(() => puntajes(datos.preguntas, respuestas), [datos, respuestas])
  const orden = ranking(p)
  const [t1, t2] = orden
  const carreras = useMemo(() => recomendar(datos.carreras, p), [datos, p])
  const donde = universidadesTop(carreras)
  const [copiado, setCopiado] = useState(false)
  const uni = (s: string) => datos.universidades.get(s) ?? { id: s, siglas: s, nombre: s, logo_url: null }
  const nadaLeGusta = p[t1] === 0

  async function compartir() {
    const texto = `🧭 Hice el test vocacional de MiCarrera: soy ${TIPOS[t1].emoji} ${TIPOS[t1].nombre} + ${TIPOS[t2].emoji} ${TIPOS[t2].nombre}` +
      ` y me salió ${carreras[0]?.carrera.nombre}. ¿Y a ti?\n👉 https://micarrera-beta.vercel.app/?test`
    try {
      if (navigator.share) await navigator.share({ text: texto })
      else { await navigator.clipboard.writeText(texto); setCopiado(true) }
    } catch { /* canceló */ }
  }

  return (
    <>
      <Encabezado onAtras={onCerrar}><strong style={{ fontSize: 18 }}>Tu resultado</strong></Encabezado>
      <div className="contenido" style={{ paddingBottom: 32, gap: 14 }}>
        <div className="tarjeta" style={{ background: 'var(--degradado)', border: 0, color: '#fff', padding: '24px 22px' }}>
          <div style={{ fontSize: 44 }}>{TIPOS[t1].emoji}{TIPOS[t2].emoji}</div>
          <div style={{ color: 'rgba(255,255,255,.75)', fontSize: 14, fontWeight: 600, marginTop: 6 }}>Tu perfil</div>
          <h1 style={{ margin: '2px 0 10px', fontSize: 28 }}>{TIPOS[t1].nombre} + {TIPOS[t2].nombre}</h1>
          <p style={{ margin: 0, color: 'rgba(255,255,255,.88)', lineHeight: 1.5 }}>
            {nadaLeGusta ? 'Casi nada te llamó la atención. Repite el test con calma pensando en lo que disfrutas en tu tiempo libre.'
              : `${TIPOS[t1].descripcion} ${TIPOS[t2].descripcion.replace('Te gusta', 'También te gusta')}`}
          </p>
        </div>

        <div className="seccion">Tus intereses</div>
        <div className="tarjeta" style={{ padding: '16px 18px', display: 'flex', flexDirection: 'column', gap: 12 }}>
          {orden.map((t, i) => (
            <div key={t} style={{ display: 'grid', gridTemplateColumns: '132px 1fr 40px', alignItems: 'center', gap: 10, fontSize: 14 }}>
              <span style={{ fontWeight: 600, whiteSpace: 'nowrap' }}>{TIPOS[t].emoji} {TIPOS[t].nombre}</span>
              <div style={{ height: 10, borderRadius: 99, background: 'var(--tarjeta-alta)', overflow: 'hidden' }}>
                <div style={{ width: `${p[t] * 100}%`, height: '100%', borderRadius: 99,
                  background: i < 2 ? 'var(--acento)' : 'color-mix(in srgb, var(--acento) 45%, transparent)' }} />
              </div>
              <span className="mono suave" style={{ textAlign: 'right' }}>{Math.round(p[t] * 100)}%</span>
            </div>
          ))}
        </div>

        <div className="seccion">Carreras para ti</div>
        <div className="rejilla">
          {carreras.map(({ carrera: c, afinidad }) => (
            <div key={c.id} className="tarjeta" style={{ padding: 16 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'flex-start' }}>
                <span style={{ fontSize: 30, lineHeight: 1 }}>{c.emoji}</span>
                <span style={{ flex: 1, minWidth: 0 }}>
                  <strong style={{ display: 'block', fontSize: 17 }}>{c.nombre}</strong>
                  <span className="suave" style={{ fontSize: 14 }}>{c.descripcion}</span>
                </span>
                <span style={{ textAlign: 'right' }}>
                  <strong style={{ fontSize: 20, color: 'var(--acento)' }}>{afinidad}%</strong>
                  <span className="suave" style={{ display: 'block', fontSize: 11 }}>afinidad</span>
                </span>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginTop: 12 }}>
                {c.universidades.map((s) => (
                  <span key={s} className="chip-uni" title={uni(s).nombre} style={uni(s).logo_url ? undefined : { paddingLeft: 10 }}>
                    {uni(s).logo_url && <LogoUniversidad u={uni(s)} tam={22} />}{s}
                  </span>
                ))}
              </div>
            </div>
          ))}
        </div>

        {donde.length > 0 && (
          <>
            <div className="seccion">Dónde estudiar</div>
            <div className="tarjeta lista-divisores">
              {donde.map(({ siglas, cuantas }) => (
                <div key={siglas} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '12px 16px' }}>
                  <LogoUniversidad u={uni(siglas)} tam={44} />
                  <span style={{ flex: 1, minWidth: 0 }}>
                    <strong style={{ display: 'block' }}>{uni(siglas).nombre}</strong>
                    <span className="suave" style={{ fontSize: 13 }}>Ofrece {cuantas} de tus {carreras.length} carreras</span>
                  </span>
                </div>
              ))}
            </div>
          </>
        )}

        <p className="suave" style={{ fontSize: 12, margin: '0 4px', lineHeight: 1.5 }}>
          Este test es una guía, no una decisión final. Las carreras de cada universidad pueden cambiar: confírmalas en su página
          y, si puedes, habla con un orientador.
        </p>

        {onCrearCuenta && <button className="boton ancho" onClick={onCrearCuenta}>Crear mi cuenta en MiCarrera</button>}
        <div style={{ display: 'flex', gap: 10 }}>
          <button className="pastilla" style={{ flex: 1, justifyContent: 'center', height: 48 }} onClick={compartir}>
            <Icono n="compartir" tam={18} />{copiado ? '¡Copiado!' : 'Compartir'}
          </button>
          <button className="pastilla" style={{ flex: 1, justifyContent: 'center', height: 48 }} onClick={onRepetir}>
            <Icono n="refrescar" tam={18} />Repetir
          </button>
        </div>
      </div>
    </>
  )
}
