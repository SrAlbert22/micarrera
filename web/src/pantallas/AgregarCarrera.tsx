import { useMemo, useRef, useState, type ChangeEvent } from 'react'
import * as repo from '../repo'
import type { MateriaLeida, Universidad } from '../tipos'
import { Icono, LogoUniversidad, Modal } from '../comunes'
import { archivoParaIA, ordinal } from '../util'

type Paso = 'subir' | 'leyendo' | 'revisar' | 'guardando'
type Archivo = { nombre: string; datos: string; tipo: string }

function mensajeIA(t: string) {
  if (t.includes('límite de lecturas')) return 'Llegaste al límite de lecturas con IA por hoy. Intenta mañana.'
  if (t.includes('8 MB')) return 'Los archivos son muy grandes (máx. 8 MB en total).'
  if (t.includes('Error de Gemini') || t.includes('Falta configurar')) return 'La IA está muy ocupada ahora mismo. Intenta otra vez en un minuto.'
  return 'No se pudo leer el pensum. Revisa tu internet e intenta otra vez.'
}

/**
 * "Agrega tu carrera": el estudiante sube su pensum (fotos, PDF o texto copiado), la IA lo organiza,
 * lo revisa y lo guarda para todos los estudiantes de su universidad. Así nadie se queda sin carrera.
 */
export default function AgregarCarrera({ universidad, onLista, onCancelar }: {
  universidad: Universidad; onLista: (id: string) => void; onCancelar: () => void
}) {
  const [paso, setPaso] = useState<Paso>('subir')
  const [archivos, setArchivos] = useState<Archivo[]>([])
  const [texto, setTexto] = useState('')
  const [verTexto, setVerTexto] = useState(false)
  const [nombre, setNombre] = useState('')
  const [materias, setMaterias] = useState<MateriaLeida[]>([])
  const [editando, setEditando] = useState<number | 'nueva' | null>(null)
  const [error, setError] = useState<string | null>(null)
  const entrada = useRef<HTMLInputElement>(null)
  const siglas = universidad.siglas ?? universidad.nombre

  async function agregarArchivos(e: ChangeEvent<HTMLInputElement>) {
    const lista = [...(e.target.files ?? [])]
    e.target.value = ''
    setError(null)
    try {
      const nuevos = await Promise.all(lista.map(async (f) => {
        const [datos, tipo] = await archivoParaIA(f, 2200) // pensums con letra chiquita: un poco más grande que el horario
        return { nombre: f.name, datos, tipo }
      }))
      setArchivos((a) => [...a, ...nuevos].slice(0, 8))
    } catch (err) {
      setError(String((err as Error).message).includes('8 MB') ? 'Ese PDF es muy grande (máx. 8 MB).' : 'No se pudo abrir ese archivo.')
    }
  }

  async function leer() {
    setPaso('leyendo'); setError(null)
    try {
      const r = await repo.leerPensumConIA(archivos.map(({ datos, tipo }) => ({ datos, tipo })), texto)
      if (r.materias.length < 5) {
        setError('No encontré el pensum ahí. Prueba con fotos más claras, el PDF de tu universidad o pega el texto.')
        setPaso('subir'); return
      }
      setMaterias(r.materias)
      if (r.carrera && !nombre) setNombre(r.carrera)
      setPaso('revisar'); window.scrollTo(0, 0)
    } catch (err) {
      setError(mensajeIA(String((err as Error).message ?? err)))
      setPaso('subir')
    }
  }

  async function guardar() {
    setPaso('guardando'); setError(null)
    try {
      const r = await repo.agregarCarrera(universidad.id, nombre.trim(), materias)
      onLista(r.id)
    } catch (err) {
      const m = String((err as { message?: string }).message ?? '')
      setError(m && !m.includes('fetch') ? m : 'No se pudo guardar. Revisa tu internet e intenta otra vez.')
      setPaso('revisar')
    }
  }

  const periodos = useMemo(() => {
    const g = new Map<number, { m: MateriaLeida; i: number }[]>()
    materias.forEach((m, i) => g.set(m.periodo, [...(g.get(m.periodo) ?? []), { m, i }]))
    return [...g.entries()].sort((a, b) => a[0] - b[0])
  }, [materias])
  const creditos = materias.reduce((s, m) => s + m.creditos, 0)
  const listo = archivos.length > 0 || texto.trim().length >= 30

  return (
    <div className="app ancha">
      <div className="titulo-pantalla" style={{ paddingLeft: 8 }}>
        <button className="boton-texto" style={{ color: 'var(--texto)' }} aria-label="Volver"
          onClick={() => (paso === 'revisar' ? setPaso('subir') : onCancelar())}><Icono n="atras" /></button>
        <LogoUniversidad u={universidad} tam={44} />
        <div style={{ flex: 1 }}>
          <h1 style={{ margin: 0, fontSize: 26 }}>{paso === 'revisar' || paso === 'guardando' ? 'Revisa tu pensum' : 'Agrega tu carrera'}</h1>
          <div className="suave" style={{ fontSize: 14 }}>{universidad.nombre}</div>
        </div>
      </div>

      <div className="contenido" style={{ paddingBottom: 40, gap: 14, maxWidth: 720, width: '100%', margin: '0 auto' }}>
        {(paso === 'subir' || paso === 'leyendo') && (
          <>
            <div className="tarjeta" style={{ padding: 20, background: 'var(--degradado)', border: 0, color: '#fff' }}>
              <div style={{ fontSize: 36 }}>📄✨</div>
              <h2 style={{ margin: '8px 0 6px', fontSize: 21 }}>Sube el pensum y la IA lo organiza</h2>
              <p style={{ margin: 0, color: 'rgba(255,255,255,.85)', fontSize: 15, lineHeight: 1.5 }}>
                Saca las materias, créditos, períodos y prerrequisitos. Tú lo revisas y queda guardado para todos los estudiantes de {siglas}.
              </p>
            </div>

            <div className="seccion">¿Dónde encuentro mi pensum?</div>
            <div className="tarjeta lista-divisores">
              <Pista e="🌐" t="En la página de tu universidad" d="Busca “pensum” o “plan de estudios” + tu carrera. Casi siempre es un PDF." />
              <Pista e="🎓" t="En el portal de estudiantes" d="Copia el texto de la página del pensum y pégalo aquí." />
              <Pista e="📸" t="En papel" d="Tómale fotos claras a todas las páginas." />
            </div>

            <input ref={entrada} type="file" accept="image/*,application/pdf" multiple hidden onChange={agregarArchivos} />
            <div style={{ display: 'flex', gap: 10 }}>
              <button className="pastilla" style={{ flex: 1, justifyContent: 'center', height: 50 }} disabled={paso === 'leyendo'}
                onClick={() => entrada.current?.click()}>📸 Fotos o PDF</button>
              <button className={`pastilla${verTexto ? ' destacada' : ''}`} style={{ flex: 1, justifyContent: 'center', height: 50 }}
                disabled={paso === 'leyendo'} onClick={() => setVerTexto(!verTexto)}>📋 Pegar texto</button>
            </div>

            {archivos.length > 0 && (
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
                {archivos.map((a, i) => (
                  <span key={i} className="chip-uni" style={{ padding: '6px 6px 6px 12px' }}>
                    {a.tipo === 'application/pdf' ? '📄' : '🖼️'} {a.nombre.length > 22 ? a.nombre.slice(0, 20) + '…' : a.nombre}
                    <button className="boton-texto" style={{ padding: '0 4px', color: 'var(--texto-suave)' }} aria-label="Quitar"
                      onClick={() => setArchivos((l) => l.filter((_, j) => j !== i))}><Icono n="cerrar" tam={16} /></button>
                  </span>
                ))}
              </div>
            )}
            {verTexto && (
              <textarea className="campo" rows={8} value={texto} onChange={(e) => setTexto(e.target.value)}
                placeholder="Pega aquí el texto del pensum (materias, códigos, créditos…)" style={{ resize: 'vertical' }} />
            )}

            {error && <div className="aviso">{error}</div>}
            {paso === 'leyendo' ? (
              <div className="tarjeta" style={{ padding: 18 }}>
                <strong>Leyendo tu pensum con IA…</strong>
                <div className="suave" style={{ fontSize: 14, margin: '4px 0 12px' }}>Si es largo puede tardar hasta un minuto.</div>
                <div className="barra-carga" />
              </div>
            ) : (
              <button className="boton ancho" disabled={!listo} onClick={leer}>Leer el pensum con IA</button>
            )}
          </>
        )}

        {(paso === 'revisar' || paso === 'guardando') && (
          <>
            <div>
              <label className="etiqueta-campo">Nombre de la carrera</label>
              <input className="campo" value={nombre} onChange={(e) => setNombre(e.target.value)} maxLength={120}
                placeholder="Ej. Ingeniería Industrial (Plan 2022)" />
            </div>
            <div className="tarjeta" style={{ display: 'flex', padding: '14px 0' }}>
              {[[materias.length, 'Materias'], [periodos.length, 'Períodos'], [creditos, 'Créditos']].map(([v, t], i) => (
                <div key={t} style={{ flex: 1, textAlign: 'center', borderLeft: i ? '1px solid var(--borde)' : 0 }}>
                  <strong style={{ fontSize: 24 }}>{v}</strong><div className="suave" style={{ fontSize: 13 }}>{t}</div>
                </div>
              ))}
            </div>
            <p className="suave" style={{ margin: '0 4px', fontSize: 14 }}>
              Revisa que no falte nada: toca una materia para corregirla. Se guardará para todos los estudiantes de {siglas}.
            </p>

            {periodos.map(([p, lista]) => (
              <div key={p} className="tarjeta">
                <div style={{ display: 'flex', alignItems: 'center', padding: '12px 16px', borderBottom: '1px solid var(--borde)' }}>
                  <strong style={{ flex: 1, fontSize: 16 }}>{ordinal(p)} período</strong>
                  <span className="suave" style={{ fontSize: 13 }}>{lista.length} mat. · {lista.reduce((s, x) => s + x.m.creditos, 0)} cr.</span>
                </div>
                <div className="lista-divisores">
                  {lista.map(({ m, i }) => (
                    <button key={i} onClick={() => setEditando(i)}
                      style={{ display: 'block', width: '100%', textAlign: 'left', background: 'none', border: 0, padding: '11px 16px' }}>
                      <span style={{ display: 'block', fontWeight: 600, fontSize: 15 }}>{m.nombre}</span>
                      <span style={{ display: 'flex', flexWrap: 'wrap', gap: '2px 8px', fontSize: 13, marginTop: 3 }}>
                        <span className="mono suave">{m.codigo}</span>
                        <span className="suave">· {m.creditos} cr.</span>
                        {m.prerequisitos.map((r) => <span key={r} className="mono" style={{ color: 'var(--acento)' }}>🔗 {r}</span>)}
                      </span>
                    </button>
                  ))}
                </div>
              </div>
            ))}
            <button className="pastilla" style={{ justifyContent: 'center', height: 46 }} onClick={() => setEditando('nueva')}>
              <Icono n="mas" tam={18} />Agregar una materia
            </button>

            {error && <div className="aviso">{error}</div>}
            <button className="boton ancho" disabled={paso === 'guardando' || nombre.trim().length < 3 || materias.length < 5} onClick={guardar}>
              {paso === 'guardando' ? <div className="girando" style={{ width: 22, height: 22 }} /> : 'Guardar mi carrera'}
            </button>
            {nombre.trim().length < 3 && <div className="suave" style={{ fontSize: 13, textAlign: 'center' }}>Escribe el nombre de la carrera arriba.</div>}
          </>
        )}
      </div>

      {editando !== null && (
        <DialogoMateria materia={editando === 'nueva' ? null : materias[editando]}
          periodoSugerido={periodos.at(-1)?.[0] ?? 1}
          onCerrar={() => setEditando(null)}
          onBorrar={editando === 'nueva' ? undefined : () => { setMaterias((l) => l.filter((_, j) => j !== editando)); setEditando(null) }}
          onGuardar={(m) => {
            setMaterias((l) => (editando === 'nueva' ? [...l, m] : l.map((x, j) => (j === editando ? m : x))))
            setEditando(null)
          }} />
      )}
    </div>
  )
}

function Pista({ e, t, d }: { e: string; t: string; d: string }) {
  return (
    <div style={{ display: 'flex', gap: 14, padding: '13px 16px', alignItems: 'center' }}>
      <span style={{ fontSize: 24 }}>{e}</span>
      <span><strong style={{ display: 'block', fontSize: 15 }}>{t}</strong><span className="suave" style={{ fontSize: 13 }}>{d}</span></span>
    </div>
  )
}

function DialogoMateria({ materia, periodoSugerido, onGuardar, onBorrar, onCerrar }: {
  materia: MateriaLeida | null; periodoSugerido: number
  onGuardar: (m: MateriaLeida) => void; onBorrar?: () => void; onCerrar: () => void
}) {
  const [codigo, setCodigo] = useState(materia?.codigo ?? '')
  const [nombre, setNombre] = useState(materia?.nombre ?? '')
  const [creditos, setCreditos] = useState(String(materia?.creditos ?? 3))
  const [periodo, setPeriodo] = useState(String(materia?.periodo ?? periodoSugerido))
  const [prereq, setPrereq] = useState((materia?.prerequisitos ?? []).join(', '))
  const valido = codigo.trim() && nombre.trim().length >= 2
  const numero = (v: string, min: number, max: number) => Math.min(max, Math.max(min, parseInt(v, 10) || min))
  return (
    <Modal onCerrar={onCerrar}>
      <h2>{materia ? 'Corregir materia' : 'Agregar materia'}</h2>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
        <div><label className="etiqueta-campo">Código</label><input className="campo mono" value={codigo} maxLength={20} onChange={(e) => setCodigo(e.target.value.toUpperCase())} /></div>
        <div><label className="etiqueta-campo">Período</label><input className="campo" inputMode="numeric" value={periodo} onChange={(e) => setPeriodo(e.target.value.replace(/\D/g, ''))} /></div>
      </div>
      <div><label className="etiqueta-campo">Nombre</label><input className="campo" value={nombre} maxLength={120} onChange={(e) => setNombre(e.target.value)} /></div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 2fr', gap: 10 }}>
        <div><label className="etiqueta-campo">Créditos</label><input className="campo" inputMode="numeric" value={creditos} onChange={(e) => setCreditos(e.target.value.replace(/\D/g, ''))} /></div>
        <div><label className="etiqueta-campo">Prerrequisitos (códigos)</label><input className="campo mono" value={prereq} placeholder="MAT-101, ESP-101" onChange={(e) => setPrereq(e.target.value.toUpperCase())} /></div>
      </div>
      <div className="acciones" style={{ justifyContent: 'space-between' }}>
        {onBorrar ? <button className="boton-texto" style={{ color: 'var(--rojo)' }} onClick={onBorrar}>Quitar</button> : <span />}
        <span>
          <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={onCerrar}>Cancelar</button>
          <button className="boton" disabled={!valido} onClick={() => onGuardar({
            codigo: codigo.trim(), nombre: nombre.trim(), creditos: numero(creditos, 0, 30), periodo: numero(periodo, 1, 20),
            prerequisitos: prereq.split(/[,;]+/).map((p) => p.trim()).filter(Boolean).slice(0, 10),
          })}>Guardar</button>
        </span>
      </div>
    </Modal>
  )
}
