// Cinco: la mascota de MiCarrera (un libro con carita). Hace el recorrido la primera vez y luego da consejos.
// Lo mismo que Cinco.kt en Android: mismo dibujo, mismas frases.
import { useEffect, useLayoutEffect, useRef, useState } from 'react'
import { Icono } from './comunes'

type Pestana = 'horario' | 'estudio' | 'pensum' | 'estadisticas' | 'cuenta'

/** El dibujo de Cinco. [hablando] mueve la boca; [saludando] mueve el brazo. */
export function Cinco({ tam = 96, hablando = false, saludando = false }: { tam?: number; hablando?: boolean; saludando?: boolean }) {
  return (
    <svg className={`cinco${hablando ? ' hablando' : ''}${saludando ? ' saludando' : ''}`} width={tam} height={tam} viewBox="0 0 120 120" aria-label="Cinco">
      <ellipse cx="60" cy="112" rx="34" ry="5" fill="rgba(0,0,0,.18)" />
      <g className="cinco-cuerpo">
        {/* pies */}
        <ellipse cx="45" cy="103" rx="9" ry="5" fill="#3b41a8" />
        <ellipse cx="75" cy="103" rx="9" ry="5" fill="#3b41a8" />
        {/* brazo izquierdo (saluda) */}
        <g className="cinco-brazo">
          <path d="M16 64 Q4 58 6 46" stroke="#4f56c9" strokeWidth="6" fill="none" strokeLinecap="round" />
          <circle cx="6" cy="44" r="5.5" fill="#4f56c9" />
        </g>
        <path d="M104 64 Q114 70 112 80" stroke="#4f56c9" strokeWidth="6" fill="none" strokeLinecap="round" />
        <circle cx="112" cy="82" r="5.5" fill="#4f56c9" />
        {/* tapa y páginas */}
        <rect x="12" y="30" width="96" height="70" rx="12" fill="#4f56c9" />
        <path d="M60 34 C45 26 28 26 18 30 L18 92 C28 88 45 88 60 96 Z" fill="#fffdf6" />
        <path d="M60 34 C75 26 92 26 102 30 L102 92 C92 88 75 88 60 96 Z" fill="#fffdf6" />
        <path d="M60 34 L60 96" stroke="#d6d9f2" strokeWidth="2" />
        <path d="M26 38 H44 M76 38 H94" stroke="#e3e6f7" strokeWidth="2.5" strokeLinecap="round" />
        {/* marcador con el 5 */}
        <path d="M84 16 H96 V40 L90 35 L84 40 Z" fill="#f5b83d" />
        <text x="90" y="30" textAnchor="middle" fontSize="11" fontWeight="800" fill="#fff" fontFamily="system-ui, sans-serif">5</text>
        {/* cara */}
        <g className="cinco-ojos">
          <ellipse cx="40" cy="57" rx="6" ry="7.5" fill="#1f2244" />
          <ellipse cx="80" cy="57" rx="6" ry="7.5" fill="#1f2244" />
          <circle cx="42.2" cy="54.2" r="2.2" fill="#fff" />
          <circle cx="82.2" cy="54.2" r="2.2" fill="#fff" />
        </g>
        <ellipse cx="30" cy="70" rx="6" ry="3.6" fill="#ff9db5" opacity=".65" />
        <ellipse cx="90" cy="70" rx="6" ry="3.6" fill="#ff9db5" opacity=".65" />
        <path className="cinco-sonrisa" d="M50 71 Q60 81 70 71" stroke="#1f2244" strokeWidth="3.5" fill="none" strokeLinecap="round" />
        <g className="cinco-boca">
          <ellipse cx="60" cy="75" rx="8" ry="6.5" fill="#1f2244" />
          <ellipse cx="60" cy="78.5" rx="4.5" ry="2.6" fill="#ff7a93" />
        </g>
      </g>
    </svg>
  )
}

// ---------- Lo que dice Cinco ----------

type Paso = { texto: string; pestana?: Pestana }
const RECORRIDO: Paso[] = [
  { texto: '¡Hola! Mi nombre es 5inco 👋 Soy una mascota creada por Albert Silvestre y estaré aquí para ayudarte en todo lo que necesites. Te enseño a usar la app en un minuto.' },
  { texto: '📅 Tu horario: toca «Agregar» para poner tus clases una por una, o «Foto o PDF» y yo leo tu horario solito. Te aviso antes de cada clase ⏰', pestana: 'horario' },
  { texto: '👥 El botón «Amigos» (arriba) sirve para buscar a tus panas por su nombre o @usuario y ver qué clases tienen juntos.', pestana: 'horario' },
  { texto: '📖 En Estudio tienes tus tareas pendientes, el modo concentración y el reto con tus amigos. Conecta tu aula virtual y las tareas te llegan solas.', pestana: 'estudio' },
  { texto: '📚 Tu pensum: toca una materia para marcarla. Un toque = en curso, otro toque = aprobada ✅. El filtro «Disponibles» te dice qué ya puedes inscribir.', pestana: 'pensum' },
  { texto: '📄 ¿Tu carrera no estaba? Toca «Cambiar» → «¿No está tu carrera?», sube tu pensum y yo lo organizo para ti.', pestana: 'pensum' },
  { texto: '📈 En Estadísticas ves tu índice, tus mejores materias, tus horas de clase y cuándo te gradúas 🎓', pestana: 'estadisticas' },
  { texto: '⚙️ En Cuenta creas tu @usuario, invitas amigos, cambias el tema a claro u oscuro y ajustas tus alertas.', pestana: 'cuenta' },
  { texto: '¡Listo! Ya sabes usar MiCarrera. Si me necesitas, tócame aquí en la esquina y te ayudo 😉' },
]

export const CONSEJOS: Record<Pestana, string[]> = {
  horario: [
    '¿Sabías que puedes subir una foto o PDF de tu horario y yo lo lleno por ti? 📸',
    'Toca una clase para agregarle un apunte, como “traer calculadora” ✏️',
    'Te aviso antes de cada clase. En Cuenta eliges cuántos minutos antes ⏰',
    'Toca “Amigos” para ver qué clases tienes con tus panas 👥',
  ],
  estudio: [
    'Conecta tu aula virtual y te aviso cuando te dejen una tarea nueva 🎓',
    'Prueba el modo concentración: 25 minutos sin el teléfono y 5 de descanso 🧘',
    'Cada ronda de concentración suma al reto de la semana con tus amigos 🏆',
  ],
  pensum: [
    'Toca una materia para cambiarla: pendiente → en curso → aprobada ✅',
    'El filtro “Disponibles” te dice qué materias ya puedes inscribir ✨',
    'En la app de Android puedes traer tus notas desde el portal de tu universidad 🎓',
  ],
  estadisticas: [
    'Mientras más materias marques, mejor calculo cuándo te gradúas 🎓',
    'Las materias con menos de 80 te las pongo en “Para reforzar”. ¡Tú puedes! 💪',
  ],
  cuenta: [
    'Crea tu @usuario para que tus amigos te encuentren 👥',
    '¿Te gusta más la app clara? Cámbiala en Apariencia 🌗',
    '¿Tienes un pana que no sabe qué estudiar? Pásale el test “¿Qué debo estudiar?” 🧭',
  ],
}

// ---------- Voz (la del teléfono o la computadora) ----------

function decir(texto: string, alEmpezar: () => void, alTerminar: () => void) {
  const s = window.speechSynthesis
  if (!s) return
  s.cancel()
  const u = new SpeechSynthesisUtterance(texto.replace(/5inco/g, 'Cinco').replace(/\p{Extended_Pictographic}|️/gu, '').replace(/[“”«»]/g, '').replace(/→/g, ', luego'))
  const voces = s.getVoices()
  const voz = voces.find((v) => /^es-(DO|US|MX|419)/.test(v.lang)) ?? voces.find((v) => v.lang.startsWith('es'))
  if (voz) u.voice = voz
  u.lang = voz?.lang ?? 'es-ES'
  u.rate = 1.05
  u.pitch = 1.25 // un poquito más aguda: es un libro chiquito
  u.onstart = alEmpezar
  u.onend = alTerminar
  u.onerror = alTerminar
  s.speak(u)
}

// ---------- Preferencia local: ocultar a 5inco (el tutorial visto se guarda en la cuenta) ----------

function oculto(): boolean { try { return localStorage.getItem('cinco-oculto') === '1' } catch { return false } }
function guardarOculto(v: boolean) { try { v ? localStorage.setItem('cinco-oculto', '1') : localStorage.removeItem('cinco-oculto') } catch { /* sin almacenamiento */ } }

/** Para "5inco, tu guía" en Cuenta: vuelve a mostrarlo. */
export function reiniciarCinco() { guardarOculto(false) }

/** Texto que aparece letra por letra, mientras Cinco mueve la boca. */
function useEscribiendo(texto: string) {
  const [n, setN] = useState(0)
  useEffect(() => {
    setN(0)
    const t = setInterval(() => setN((x) => { if (x >= texto.length) { clearInterval(t); return x } return x + 2 }), 28)
    return () => clearInterval(t)
  }, [texto])
  return { visible: texto.slice(0, n), escribiendo: n < texto.length }
}

type Modo =
  | { tipo: 'nada' } | { tipo: 'menu' } | { tipo: 'consejo'; texto: string }
  | { tipo: 'escribir'; clase: 'comentario' | 'problema' } | { tipo: 'gracias' }
  | { tipo: 'borrar' } | { tipo: 'eliminar' }

const MENU = '¿En qué te ayudo? 😊'

/**
 * 5inco en pantalla:
 *  - La PRIMERA vez de cada cuenta hace el recorrido (se marca como visto apenas empieza, en todos los dispositivos).
 *  - Después se queda en la esquina: al tocarlo abre el menú de ayuda (consejos, comentarios, problemas, borrar datos…).
 */
export function GuiaCinco({ pestana, irA, reinicio, tutorialVisto, onTutorialVisto }: {
  pestana: Pestana; irA: (p: Pestana) => void; reinicio: number; tutorialVisto: boolean; onTutorialVisto: () => void
}) {
  const [escondido, setEscondido] = useState(oculto)
  const [paso, setPaso] = useState<number | null>(tutorialVisto ? null : 0)
  const [modo, setModo] = useState<Modo>({ tipo: 'nada' })
  const [voz, setVoz] = useState(false)
  const [foco, setFoco] = useState<DOMRect | null>(null)
  const [mensaje, setMensaje] = useState('')
  const [trabajando, setTrabajando] = useState(false)
  const [confirmacion, setConfirmacion] = useState('')
  const [error, setError] = useState<string | null>(null)
  const indice = useRef(0)
  const avisado = useRef(false)

  useEffect(() => { if (!tutorialVisto && paso === null && !avisado.current) setPaso(0) }, [tutorialVisto]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (reinicio) { setEscondido(false); setPaso(0); setModo({ tipo: 'nada' }) } }, [reinicio])
  // El recorrido cuenta como visto apenas empieza: aunque cierren la app a mitad, no se repite
  useEffect(() => { if (paso === 0 && !tutorialVisto && !avisado.current) { avisado.current = true; onTutorialVisto() } }, [paso, tutorialVisto, onTutorialVisto])

  const enRecorrido = paso !== null
  const texto = enRecorrido ? RECORRIDO[paso].texto
    : modo.tipo === 'consejo' ? modo.texto
    : modo.tipo === 'menu' ? MENU
    : modo.tipo === 'escribir' ? (modo.clase === 'problema' ? 'Cuéntame qué pasó y dónde. Albert lo recibe al instante 🐞' : '¿Qué te gustaría que mejoremos o agreguemos? 💬')
    : modo.tipo === 'gracias' ? '¡Gracias! Ya le llegó a Albert. Si hace falta, te escribe a tu correo 💚'
    : modo.tipo === 'borrar' ? '¿Qué quieres borrar? Esto no se puede deshacer 🗑️'
    : modo.tipo === 'eliminar' ? 'Se borrará tu cuenta con TODO: notas, horario, tareas y amigos. Escribe ELIMINAR para confirmar.'
    : null
  const { visible, escribiendo } = useEscribiendo(texto ?? '')

  useEffect(() => { if (paso !== null && RECORRIDO[paso].pestana) irA(RECORRIDO[paso].pestana!) }, [paso, irA])
  useLayoutEffect(() => {
    const p = paso !== null ? RECORRIDO[paso].pestana : undefined
    const medir = () => setFoco(p ? document.querySelector(`[data-guia="${p}"]`)?.getBoundingClientRect() ?? null : null)
    medir()
    window.addEventListener('resize', medir)
    return () => window.removeEventListener('resize', medir)
  }, [paso])
  useEffect(() => () => window.speechSynthesis?.cancel(), [])

  const callar = () => window.speechSynthesis?.cancel()
  function cerrarRecorrido() { callar(); setPaso(null); setFoco(null); setModo({ tipo: 'nada' }) }
  function siguiente() {
    callar()
    if (paso === null) return
    if (paso + 1 < RECORRIDO.length) setPaso(paso + 1)
    else cerrarRecorrido()
  }
  function consejo() {
    const lista = CONSEJOS[pestana]
    indice.current = (indice.current + 1) % lista.length
    setModo({ tipo: 'consejo', texto: lista[indice.current] })
  }
  async function enviar(clase: 'comentario' | 'problema') {
    setTrabajando(true); setError(null)
    try {
      await (await import('./repo')).enviarSoporte(clase, mensaje, pestana)
      setMensaje(''); setModo({ tipo: 'gracias' })
    } catch (e) {
      setError(String((e as Error).message ?? '').includes('límite') ? 'Ya enviaste muchos mensajes hoy. Intenta mañana.' : 'No se pudo enviar. Revisa tu internet.')
    }
    setTrabajando(false)
  }
  async function borrar(que: 'horario' | 'progreso') {
    setTrabajando(true); setError(null)
    try {
      const repo = await import('./repo')
      await (que === 'horario' ? repo.borrarMiHorario() : repo.reiniciarMiProgreso())
      window.location.reload()
    } catch { setError('No se pudo borrar. Intenta otra vez.'); setTrabajando(false) }
  }
  async function eliminarCuenta() {
    setTrabajando(true); setError(null)
    try { await (await import('./repo')).eliminarMiCuenta() } catch { setError('No se pudo eliminar la cuenta. Intenta otra vez.'); setTrabajando(false) }
  }

  if (escondido && !enRecorrido) return null
  const hablando = escribiendo || voz
  const opcion = (emoji: string, t: string, accion: () => void, color?: string) => (
    <button key={t} className="cinco-opcion" style={color ? { color } : undefined} onClick={accion}><span>{emoji}</span>{t}</button>
  )

  return (
    <>
      {enRecorrido && <div className="cinco-velo" />}
      {enRecorrido && foco && (
        <div className="cinco-foco" style={{ left: foco.left - 6, top: foco.top - 6, width: foco.width + 12, height: foco.height + 12 }} />
      )}
      <div className={`cinco-lugar${enRecorrido ? ' grande' : ''}`}>
        {texto && (
          <div className="cinco-globo" role="dialog" aria-live="polite">
            <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 6 }}>
              <strong style={{ flex: 1, color: 'var(--acento)' }}>5inco</strong>
              {'speechSynthesis' in window && (
                <button className="boton-texto" style={{ padding: 4, fontSize: 18 }} aria-label="Escuchar"
                  onClick={() => decir(texto, () => setVoz(true), () => setVoz(false))}>🔊</button>
              )}
              {!enRecorrido && (
                <button className="boton-texto" style={{ padding: 4, color: 'var(--texto-suave)' }} aria-label="Cerrar"
                  onClick={() => { callar(); setModo({ tipo: 'nada' }); setError(null) }}><Icono n="cerrar" tam={18} /></button>
              )}
            </div>
            <p style={{ margin: 0, fontSize: 15, lineHeight: 1.45, minHeight: 22 }}>{visible}</p>

            {enRecorrido && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 12 }}>
                <span className="suave" style={{ fontSize: 12, flex: 1 }}>{paso! + 1} de {RECORRIDO.length}</span>
                {paso! + 1 < RECORRIDO.length && <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={cerrarRecorrido}>Saltar</button>}
                <button className="boton" style={{ height: 40 }} onClick={siguiente}>{paso! + 1 < RECORRIDO.length ? 'Siguiente' : '¡Listo!'}</button>
              </div>
            )}
            {modo.tipo === 'menu' && (
              <div className="cinco-menu">
                {opcion('💡', 'Un consejo', consejo)}
                {opcion('💬', 'Enviar un comentario', () => setModo({ tipo: 'escribir', clase: 'comentario' }))}
                {opcion('🐞', 'Reportar un problema', () => setModo({ tipo: 'escribir', clase: 'problema' }))}
                {opcion('🗑️', 'Borrar mis datos', () => setModo({ tipo: 'borrar' }))}
                {opcion('📖', 'Ver el tutorial otra vez', () => { setModo({ tipo: 'nada' }); setPaso(0) })}
                {opcion('🙈', 'Ocultar a 5inco', () => { guardarOculto(true); setEscondido(true); setModo({ tipo: 'nada' }) }, 'var(--texto-suave)')}
              </div>
            )}
            {modo.tipo === 'consejo' && (
              <div style={{ display: 'flex', gap: 8, marginTop: 12, justifyContent: 'flex-end' }}>
                <button className="boton-texto" onClick={() => setModo({ tipo: 'menu' })}>Menú</button>
                <button className="pastilla chica" onClick={consejo}>Otro consejo</button>
              </div>
            )}
            {modo.tipo === 'escribir' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 10 }}>
                <textarea className="campo" rows={4} maxLength={2000} value={mensaje} autoFocus onChange={(e) => setMensaje(e.target.value)}
                  placeholder={modo.clase === 'problema' ? 'Ej. En Horario, al subir la foto sale un error…' : 'Ej. Me gustaría que…'} />
                {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setModo({ tipo: 'menu' })}>Volver</button>
                  <button className="boton" style={{ height: 40 }} disabled={mensaje.trim().length < 3 || trabajando} onClick={() => enviar(modo.clase)}>Enviar</button>
                </div>
              </div>
            )}
            {modo.tipo === 'gracias' && (
              <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 10 }}>
                <button className="boton-texto" onClick={() => setModo({ tipo: 'menu' })}>Volver al menú</button>
              </div>
            )}
            {modo.tipo === 'borrar' && (
              <div className="cinco-menu">
                {opcion('📅', trabajando ? 'Borrando…' : 'Borrar todo mi horario', () => { if (!trabajando && confirm('¿Borrar todas tus clases?')) borrar('horario') })}
                {opcion('📚', 'Reiniciar mi pensum (notas y estados)', () => { if (!trabajando && confirm('¿Borrar tus notas y el estado de todas tus materias?')) borrar('progreso') })}
                {opcion('⚠️', 'Eliminar mi cuenta', () => setModo({ tipo: 'eliminar' }), 'var(--rojo)')}
                {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
                <button className="boton-texto" style={{ alignSelf: 'flex-end' }} onClick={() => setModo({ tipo: 'menu' })}>Volver</button>
              </div>
            )}
            {modo.tipo === 'eliminar' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8, marginTop: 10 }}>
                <input className="campo" value={confirmacion} placeholder="ELIMINAR" onChange={(e) => setConfirmacion(e.target.value.toUpperCase())} />
                {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
                <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                  <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setModo({ tipo: 'borrar' })}>Cancelar</button>
                  <button className="boton" style={{ height: 40, background: 'var(--rojo)' }} disabled={confirmacion !== 'ELIMINAR' || trabajando} onClick={eliminarCuenta}>
                    Eliminar para siempre
                  </button>
                </div>
              </div>
            )}
          </div>
        )}
        <button className="cinco-boton" aria-label="5inco, tu guía"
          onClick={() => { if (!enRecorrido) { callar(); setError(null); setModo(modo.tipo === 'nada' ? { tipo: 'menu' } : { tipo: 'nada' }) } }}>
          <Cinco tam={enRecorrido ? 110 : 64} hablando={hablando} saludando={enRecorrido && paso === 0} />
        </button>
      </div>
    </>
  )
}
