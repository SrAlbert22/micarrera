import { useEffect, useMemo, useRef, useState } from 'react'
import * as repo from '../repo'
import type { EstadoAula, Posicion, Tarea, Universidad } from '../tipos'
import { Cargando, Icono, Modal, Pastilla, TituloPantalla } from '../comunes'

/**
 * Estudio: modo concentración (Pomodoro), reto de la semana con tus amigos, tus pendientes (a mano o del aula virtual)
 * y la conexión con el aula virtual (enlace del calendario de Moodle, Classroom o Canvas).
 */
export default function Estudio({ universidad }: { universidad: Universidad | null }) {
  const [tareas, setTareas] = useState<Tarea[]>([])
  const [ranking, setRanking] = useState<Posicion[]>([])
  const [aula, setAula] = useState<EstadoAula | null>(null)
  const [materias, setMaterias] = useState<string[]>([])
  const [cargando, setCargando] = useState(true)
  const [editando, setEditando] = useState<Tarea | 'nueva' | null>(null)
  const [viendo, setViendo] = useState<Tarea | null>(null)
  const [conectando, setConectando] = useState(false)
  const [verHechas, setVerHechas] = useState(false)

  const recargar = () => Promise.allSettled([
    repo.tareas().then(setTareas),
    repo.ranking().then(setRanking),
    repo.estadoAula().then(setAula),
  ])
  useEffect(() => {
    Promise.allSettled([recargar(), repo.horario().then((c) => setMaterias([...new Set(c.map((x) => x.materia))].sort()))])
      .finally(() => setCargando(false))
  }, [])

  async function marcar(t: Tarea) {
    setTareas((l) => l.map((x) => (x.id === t.id ? { ...x, hecha: !x.hecha } : x)))
    await repo.marcarTarea(t.id, !t.hecha).catch(() => {})
  }

  const grupos = useMemo(() => agrupar(tareas), [tareas])

  return (
    <>
      <TituloPantalla titulo="Estudio">
        <Pastilla texto={aula ? (aula.estado === 'error' ? 'Aula: revisar' : 'Aula virtual ✓') : 'Conectar aula'} icono="estudio"
          destacada={!aula} onClick={() => setConectando(true)} />
      </TituloPantalla>
      {cargando ? <Cargando /> : (
        <div className="contenido" style={{ gap: 14 }}>
          <div className="rejilla">
            <Concentracion materias={materias} onTerminada={() => repo.ranking().then(setRanking).catch(() => {})} />
            <Reto ranking={ranking} />
          </div>

          {!aula && (
            <button className="tarjeta clic" onClick={() => setConectando(true)} style={{ display: 'flex', gap: 14, alignItems: 'center', padding: 16, textAlign: 'left',
              borderColor: 'color-mix(in srgb, var(--acento) 55%, transparent)', background: 'color-mix(in srgb, var(--acento-suave) 35%, var(--tarjeta))' }}>
              <span style={{ fontSize: 30 }}>🎓</span>
              <span style={{ flex: 1 }}>
                <strong style={{ display: 'block', color: 'var(--acento)' }}>Conecta tu aula virtual</strong>
                <span className="suave" style={{ fontSize: 13 }}>Tus tareas llegan solas, con su explicación, y te aviso antes de que venzan.</span>
              </span>
              <Icono n="derecha" color="var(--gris)" />
            </button>
          )}

          <div style={{ display: 'flex', alignItems: 'center' }}>
            <div className="seccion" style={{ flex: 1 }}>Pendientes</div>
            <Pastilla texto="Agregar" icono="mas" chica onClick={() => setEditando('nueva')} />
          </div>
          {tareas.filter((t) => !t.hecha).length === 0 && (
            <div className="tarjeta" style={{ padding: 24, textAlign: 'center' }}>
              <div style={{ fontSize: 36 }}>🎉</div>
              <strong>¡Nada pendiente!</strong>
              <p className="suave" style={{ margin: '4px 0 0', fontSize: 14 }}>Agrega tus tareas o conecta tu aula virtual para que lleguen solas.</p>
            </div>
          )}
          {grupos.map(([titulo, lista, color]) => lista.length > 0 && (
            <div key={titulo}>
              <div style={{ fontSize: 13, fontWeight: 700, color, margin: '0 4px 6px' }}>{titulo} · {lista.length}</div>
              <div className="tarjeta lista-divisores">
                {lista.map((t) => <FilaTarea key={t.id} t={t} onMarcar={() => marcar(t)} onAbrir={() => setViendo(t)} />)}
              </div>
            </div>
          ))}
          {tareas.some((t) => t.hecha) && (
            <button className="boton-texto" onClick={() => setVerHechas(!verHechas)}>
              {verHechas ? 'Ocultar las hechas' : `Ver las hechas (${tareas.filter((t) => t.hecha).length})`}
            </button>
          )}
          {verHechas && (
            <div className="tarjeta lista-divisores">
              {tareas.filter((t) => t.hecha).map((t) => <FilaTarea key={t.id} t={t} onMarcar={() => marcar(t)} onAbrir={() => setViendo(t)} />)}
            </div>
          )}
        </div>
      )}

      {viendo && (
        <DetalleTarea t={viendo} onCerrar={() => setViendo(null)} onEditar={() => { setEditando(viendo); setViendo(null) }}
          onBorrar={async () => { await repo.borrarTarea(viendo.id).catch(() => {}); setViendo(null); recargar() }}
          onMarcar={() => { marcar(viendo); setViendo(null) }} />
      )}
      {editando && (
        <DialogoTarea t={editando === 'nueva' ? null : editando} materias={materias} onCerrar={(cambio) => { setEditando(null); if (cambio) recargar() }} />
      )}
      {conectando && <DialogoAula universidad={universidad} estado={aula} onCerrar={() => { setConectando(false); recargar() }} />}
    </>
  )
}

// ---------- Pendientes ----------

function agrupar(tareas: Tarea[]): [string, Tarea[], string][] {
  const ahora = Date.now(), hoyFin = new Date(); hoyFin.setHours(23, 59, 59, 999)
  const semana = ahora + 7 * 86400_000
  const pend = tareas.filter((t) => !t.hecha)
  const ms = (t: Tarea) => (t.entrega ? new Date(t.entrega).getTime() : Infinity)
  return [
    ['Atrasadas', pend.filter((t) => ms(t) < ahora), 'var(--rojo)'],
    ['Hoy', pend.filter((t) => ms(t) >= ahora && ms(t) <= hoyFin.getTime()), 'var(--ambar)'],
    ['Esta semana', pend.filter((t) => ms(t) > hoyFin.getTime() && ms(t) <= semana), 'var(--acento)'],
    ['Más adelante', pend.filter((t) => ms(t) > semana), 'var(--texto-suave)'],
  ]
}

export function cuandoEntrega(iso: string | null): string {
  if (!iso) return 'Sin fecha'
  const d = new Date(iso), hoy = new Date(), manana = new Date(); manana.setDate(hoy.getDate() + 1)
  const hora = d.toLocaleTimeString('es-DO', { hour: 'numeric', minute: '2-digit' })
  if (d.toDateString() === hoy.toDateString()) return `Hoy ${hora}`
  if (d.toDateString() === manana.toDateString()) return `Mañana ${hora}`
  return d.toLocaleDateString('es-DO', { weekday: 'short', day: 'numeric', month: 'short' }) + ` · ${hora}`
}

function FilaTarea({ t, onMarcar, onAbrir }: { t: Tarea; onMarcar: () => void; onAbrir: () => void }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 14px' }}>
      <button onClick={onMarcar} aria-label={t.hecha ? 'Desmarcar' : 'Marcar como hecha'}
        style={{ width: 26, height: 26, borderRadius: '50%', flex: 'none', display: 'grid', placeItems: 'center', padding: 0,
          border: t.hecha ? 0 : '2px solid var(--acento)', background: t.hecha ? 'var(--verde)' : 'none' }}>
        {t.hecha && <Icono n="check" tam={16} color="#fff" />}
      </button>
      <button onClick={onAbrir} style={{ flex: 1, minWidth: 0, textAlign: 'left', background: 'none', border: 0, padding: 0 }}>
        <span style={{ display: 'block', fontWeight: 600, fontSize: 15, textDecoration: t.hecha ? 'line-through' : undefined,
          color: t.hecha ? 'var(--texto-suave)' : undefined }}>{t.titulo}</span>
        <span className="suave" style={{ fontSize: 12.5 }}>
          {t.origen === 'aula' ? '🎓 ' : ''}{[t.materia, cuandoEntrega(t.entrega)].filter(Boolean).join(' · ')}
        </span>
      </button>
    </div>
  )
}

function DetalleTarea({ t, onCerrar, onEditar, onBorrar, onMarcar }: { t: Tarea; onCerrar: () => void; onEditar: () => void; onBorrar: () => void; onMarcar: () => void }) {
  return (
    <Modal onCerrar={onCerrar}>
      <div className="suave" style={{ fontSize: 13 }}>{t.origen === 'aula' ? '🎓 Del aula virtual' : '✏️ Agregada por ti'}{t.materia ? ` · ${t.materia}` : ''}</div>
      <h2>{t.titulo}</h2>
      <div style={{ fontWeight: 600, color: 'var(--ambar)' }}>⏰ {cuandoEntrega(t.entrega)}</div>
      {t.detalle ? <p style={{ margin: 0, whiteSpace: 'pre-wrap', lineHeight: 1.5, fontSize: 15 }}>{t.detalle}</p>
        : <p className="suave" style={{ margin: 0, fontSize: 14 }}>Sin explicación.</p>}
      <div className="acciones" style={{ justifyContent: 'space-between', flexWrap: 'wrap' }}>
        <span>
          {t.origen === 'manual' && <button className="boton-texto" style={{ color: 'var(--rojo)' }} onClick={onBorrar}>Borrar</button>}
          {t.origen === 'manual' && <button className="boton-texto" onClick={onEditar}>Editar</button>}
        </span>
        <button className="boton" onClick={onMarcar}>{t.hecha ? 'Marcar pendiente' : '✓ Hecha'}</button>
      </div>
    </Modal>
  )
}

function DialogoTarea({ t, materias, onCerrar }: { t: Tarea | null; materias: string[]; onCerrar: (cambio: boolean) => void }) {
  const local = (iso: string | null) => { if (!iso) return ''; const d = new Date(iso); d.setMinutes(d.getMinutes() - d.getTimezoneOffset()); return d.toISOString().slice(0, 16) }
  const [titulo, setTitulo] = useState(t?.titulo ?? '')
  const [materia, setMateria] = useState(t?.materia ?? '')
  const [entrega, setEntrega] = useState(local(t?.entrega ?? null))
  const [detalle, setDetalle] = useState(t?.detalle ?? '')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  async function guardar() {
    setGuardando(true)
    try {
      await repo.guardarTarea({ titulo: titulo.trim(), materia: materia.trim() || null, detalle: detalle.trim() || null,
        entrega: entrega ? new Date(entrega).toISOString() : null }, t?.id)
      onCerrar(true)
    } catch (e) { setError(String((e as Error).message ?? '').includes('límite') ? 'Llegaste al límite de tareas de hoy.' : 'No se pudo guardar.'); setGuardando(false) }
  }
  return (
    <Modal onCerrar={() => onCerrar(false)}>
      <h2>{t ? 'Editar tarea' : 'Nueva tarea'}</h2>
      <div><label className="etiqueta-campo">¿Qué hay que hacer?</label>
        <input className="campo" value={titulo} maxLength={200} autoFocus placeholder="Ej. Práctica 3 de redes" onChange={(e) => setTitulo(e.target.value)} /></div>
      <div><label className="etiqueta-campo">Materia</label>
        <input className="campo" list="materias-tarea" value={materia} maxLength={120} onChange={(e) => setMateria(e.target.value)} />
        <datalist id="materias-tarea">{materias.map((m) => <option key={m} value={m} />)}</datalist></div>
      <div><label className="etiqueta-campo">Entrega</label>
        <input className="campo" type="datetime-local" value={entrega} onChange={(e) => setEntrega(e.target.value)} /></div>
      <div><label className="etiqueta-campo">Explicación o notas</label>
        <textarea className="campo" rows={4} value={detalle} maxLength={5000} onChange={(e) => setDetalle(e.target.value)} /></div>
      {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => onCerrar(false)}>Cancelar</button>
        <button className="boton" disabled={!titulo.trim() || guardando} onClick={guardar}>Guardar</button>
      </div>
    </Modal>
  )
}

// ---------- Modo concentración (Pomodoro) ----------

const MODOS = [{ foco: 25, descanso: 5 }, { foco: 50, descanso: 10 }]

function Concentracion({ materias, onTerminada }: { materias: string[]; onTerminada: () => void }) {
  const [modo, setModo] = useState(0)
  const [materia, setMateria] = useState('')
  const [fase, setFase] = useState<'foco' | 'descanso'>('foco')
  const [fin, setFin] = useState<number | null>(null)       // hora en que termina (corriendo)
  const [pausa, setPausa] = useState<number | null>(null)   // ms que faltaban al pausar
  const [ahora, setAhora] = useState(Date.now())
  const [mensaje, setMensaje] = useState<string | null>(null)
  const guardada = useRef(false)
  const total = (fase === 'foco' ? MODOS[modo].foco : MODOS[modo].descanso) * 60_000
  const falta = fin ? Math.max(0, fin - ahora) : pausa ?? total

  useEffect(() => {
    if (!fin) return
    const t = setInterval(() => setAhora(Date.now()), 250)
    return () => clearInterval(t)
  }, [fin])

  useEffect(() => {
    if (!fin || falta > 0) return
    if (fase === 'foco' && !guardada.current) {
      guardada.current = true
      repo.guardarSesion(MODOS[modo].foco, materia || null).then(onTerminada).catch(() => {})
      setMensaje(`¡Bien hecho! 🎉 ${MODOS[modo].foco} minutos de concentración. Toma un descanso.`)
      avisar('¡Terminaste! 🎉', 'Toma un descanso de ' + MODOS[modo].descanso + ' minutos.')
      setFase('descanso'); setFin(Date.now() + MODOS[modo].descanso * 60_000)
    } else if (fase === 'descanso') {
      avisar('Se acabó el descanso', '¿Otra ronda de concentración?')
      setFase('foco'); setFin(null); setPausa(null); setMensaje('Descanso terminado. ¿Otra ronda? 💪')
    }
  }, [falta, fin, fase, modo, materia, onTerminada])

  useEffect(() => {
    document.title = fin ? `${reloj(falta)} · ${fase === 'foco' ? 'Concentración' : 'Descanso'}` : 'MiCarrera'
    return () => { document.title = 'MiCarrera' }
  }, [falta, fin, fase])

  function empezar() {
    if ('Notification' in window && Notification.permission === 'default') Notification.requestPermission().catch(() => {})
    guardada.current = false; setMensaje(null)
    setFin(Date.now() + (pausa ?? total)); setPausa(null)
  }
  const corriendo = fin !== null
  const progreso = 1 - falta / total
  const R = 88, C = 2 * Math.PI * R

  return (
    <div className="tarjeta" style={{ padding: 18, display: 'flex', flexDirection: 'column', gap: 12, alignItems: 'center' }}>
      <div style={{ display: 'flex', width: '100%', alignItems: 'center' }}>
        <strong style={{ flex: 1, fontSize: 17 }}>🧘 Modo concentración</strong>
        {!corriendo && pausa === null && MODOS.map((m, i) => (
          <button key={i} className="pastilla chica" style={{ marginLeft: 6, background: modo === i ? 'var(--acento-suave)' : undefined }}
            onClick={() => { setModo(i); setFase('foco') }}>{m.foco}/{m.descanso}</button>
        ))}
      </div>
      <svg width="210" height="210" viewBox="0 0 210 210" role="img" aria-label={`Faltan ${reloj(falta)}`}>
        <circle cx="105" cy="105" r={R} fill="none" stroke="var(--tarjeta-alta)" strokeWidth="12" />
        <circle cx="105" cy="105" r={R} fill="none" stroke={fase === 'foco' ? 'var(--acento)' : 'var(--verde)'} strokeWidth="12" strokeLinecap="round"
          strokeDasharray={C} strokeDashoffset={C * (1 - progreso)} transform="rotate(-90 105 105)" style={{ transition: 'stroke-dashoffset .25s linear' }} />
        <text x="105" y="104" textAnchor="middle" fontSize="42" fontWeight="800" fill="var(--texto)" fontFamily="ui-monospace, Consolas, monospace">{reloj(falta)}</text>
        <text x="105" y="134" textAnchor="middle" fontSize="14" fill="var(--texto-suave)">{fase === 'foco' ? 'Concentración' : 'Descanso ☕'}</text>
      </svg>
      {!corriendo && fase === 'foco' && (
        <select className="campo" value={materia} onChange={(e) => setMateria(e.target.value)} style={{ maxWidth: 320 }}>
          <option value="">¿Qué vas a estudiar? (opcional)</option>
          {materias.map((m) => <option key={m} value={m}>{m}</option>)}
        </select>
      )}
      <div style={{ display: 'flex', gap: 10 }}>
        {corriendo
          ? <button className="boton" onClick={() => { setPausa(falta); setFin(null) }}>Pausar</button>
          : <button className="boton" onClick={empezar}>{pausa !== null ? 'Seguir' : fase === 'foco' ? 'Empezar' : 'Seguir descanso'}</button>}
        {(corriendo || pausa !== null || fase === 'descanso') && (
          <button className="pastilla" style={{ height: 52 }} onClick={() => { setFin(null); setPausa(null); setFase('foco') }}>Reiniciar</button>
        )}
      </div>
      {mensaje && <div style={{ color: 'var(--verde)', fontSize: 14, textAlign: 'center' }}>{mensaje}</div>}
      <div className="suave" style={{ fontSize: 12, textAlign: 'center' }}>Guarda el teléfono lejos. Cada ronda terminada suma a tu reto semanal.</div>
    </div>
  )
}

const reloj = (ms: number) => { const s = Math.ceil(ms / 1000); return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}` }
function avisar(titulo: string, texto: string) {
  try { if ('Notification' in window && Notification.permission === 'granted') new Notification(titulo, { body: texto, icon: '/pwa-192.png' }) } catch { /* sin avisos */ }
}

// ---------- Reto de la semana ----------

function Reto({ ranking }: { ranking: Posicion[] }) {
  const maximo = Math.max(60, ...ranking.map((p) => p.minutos))
  const nombre = (p: Posicion) => (p.soy_yo ? 'Tú' : p.nombre || (p.usuario ? `@${p.usuario}` : 'Amigo'))
  return (
    <div className="tarjeta" style={{ padding: 18 }}>
      <strong style={{ fontSize: 17 }}>🏆 Reto de la semana</strong>
      <div className="suave" style={{ fontSize: 13, margin: '2px 0 12px' }}>Minutos de concentración (lunes a domingo)</div>
      {ranking.length <= 1 && <p className="suave" style={{ fontSize: 14, margin: '0 0 10px' }}>Agrega amigos en Horario → Amigos para competir con ellos.</p>}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
        {ranking.slice(0, 8).map((p, i) => (
          <div key={p.id} style={{ display: 'grid', gridTemplateColumns: '26px 1fr 60px', gap: 10, alignItems: 'center' }}>
            <span style={{ fontWeight: 800, textAlign: 'center' }}>{['🥇', '🥈', '🥉'][i] ?? i + 1}</span>
            <div>
              <div style={{ fontSize: 14, fontWeight: p.soy_yo ? 800 : 600, color: p.soy_yo ? 'var(--acento)' : undefined }}>{nombre(p)}</div>
              <div style={{ height: 8, borderRadius: 99, background: 'var(--tarjeta-alta)', overflow: 'hidden', marginTop: 4 }}>
                <div style={{ width: `${(p.minutos / maximo) * 100}%`, height: '100%', borderRadius: 99, background: 'var(--acento)' }} />
              </div>
            </div>
            <span className="mono" style={{ textAlign: 'right', fontSize: 13 }}>{p.minutos} min</span>
          </div>
        ))}
      </div>
    </div>
  )
}

// ---------- Aula virtual ----------

function DialogoAula({ universidad, estado, onCerrar }: { universidad: Universidad | null; estado: EstadoAula | null; onCerrar: () => void }) {
  const [url, setUrl] = useState('')
  const [trabajando, setTrabajando] = useState(false)
  const [mensaje, setMensaje] = useState<{ ok: boolean; texto: string } | null>(null)
  const valido = /^https:\/\/\S+/.test(url.trim())
  async function hacer(accion: () => Promise<{ nuevas?: number; total?: number } | void>, exito: (r: { nuevas?: number; total?: number } | void) => string) {
    setTrabajando(true); setMensaje(null)
    try { const r = await accion(); setMensaje({ ok: true, texto: exito(r) }) } catch (e) { setMensaje({ ok: false, texto: String((e as Error).message) }) }
    setTrabajando(false)
  }
  return (
    <Modal onCerrar={onCerrar}>
      <h2>🎓 Aula virtual</h2>
      {estado && (
        <div className="tarjeta" style={{ padding: 12, background: 'var(--tarjeta-alta)' }}>
          <strong style={{ color: estado.estado === 'error' ? 'var(--rojo)' : 'var(--verde)' }}>
            {estado.estado === 'error' ? '⚠️ ' + (estado.ultimo_error ?? 'Hubo un problema') : '✓ Conectada'}
          </strong>
          {estado.sincronizada && <div className="suave" style={{ fontSize: 13 }}>Última revisión: {new Date(estado.sincronizada).toLocaleString('es-DO', { dateStyle: 'medium', timeStyle: 'short' })}</div>}
          <div className="suave" style={{ fontSize: 13 }}>Reviso tu aula cada 20 minutos y te aviso de tareas nuevas y de las que vencen en 24 horas.</div>
        </div>
      )}
      <p style={{ margin: 0, fontSize: 14, lineHeight: 1.5 }}>
        Pega el <strong>enlace del calendario</strong> de tu aula virtual. Ese enlace solo sirve para ver tus fechas y tareas:
        <strong> no me das tu contraseña</strong>.
      </p>
      <ol style={{ margin: 0, paddingLeft: 20, fontSize: 14, lineHeight: 1.6 }}>
        <li>Entra a tu aula virtual{universidad?.aula_url ? <> (<a href={universidad.aula_url} target="_blank" rel="noopener">abrir la de {universidad.siglas}</a>)</> : ''}.</li>
        <li>Ve a <strong>Calendario → Exportar calendario</strong> (en Moodle).</li>
        <li>Elige <em>Todos los cursos</em> y <em>Eventos recientes y próximos</em>, toca <strong>Obtener URL del calendario</strong> y cópiala.</li>
      </ol>
      <p className="suave" style={{ margin: 0, fontSize: 13 }}>¿Usas Google Classroom o Canvas? Sirve la "dirección secreta en formato iCal" de tu calendario.</p>
      <input className="campo" value={url} placeholder="https://…/calendar/export_execute.php?…" onChange={(e) => setUrl(e.target.value)} autoCapitalize="none" autoCorrect="off" />
      {mensaje && <div style={{ color: mensaje.ok ? 'var(--verde)' : 'var(--rojo)', fontSize: 14 }}>{mensaje.texto}</div>}
      <div className="acciones" style={{ flexWrap: 'wrap', justifyContent: 'space-between' }}>
        <span>
          {estado && <button className="boton-texto" style={{ color: 'var(--rojo)' }} disabled={trabajando}
            onClick={() => hacer(repo.desconectarAula, () => 'Desconectada. Tus tareas se quedan.')}>Desconectar</button>}
          {estado && <button className="boton-texto" disabled={trabajando}
            onClick={() => hacer(repo.sincronizarAula, (r) => `Listo: ${(r as { total?: number })?.total ?? 0} tareas revisadas.`)}>Actualizar ahora</button>}
        </span>
        <button className="boton" disabled={!valido || trabajando} onClick={() => hacer(() => repo.conectarAula(url),
          (r) => `¡Conectada! Encontré ${(r as { total?: number })?.total ?? 0} tareas y fechas.`)}>
          {trabajando ? <div className="girando" style={{ width: 22, height: 22 }} /> : estado ? 'Cambiar enlace' : 'Conectar'}
        </button>
      </div>
    </Modal>
  )
}
