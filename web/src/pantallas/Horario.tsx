import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import * as repo from '../repo'
import type { Clase } from '../tipos'
import { Cargando, Icono, Modal, Pastilla, TituloPantalla } from '../comunes'
import { DIAS, archivoParaIA, diaDeHoy, enCuanto, hora12, minutos } from '../util'
import { amigosEnClase, nombreVisible, type Persona } from '../amigos'
import { AvisoApp, PrimerosPasos } from '../primeros'
import { TarjetaAvisos } from '../avisosWeb'

export default function Horario({ onAmigos, irA }: { onAmigos: () => void; irA: (p: 'pensum' | 'estudio') => void }) {
  const [clases, setClases] = useState<Clase[]>([])
  const [cargando, setCargando] = useState(true)
  const [leyendo, setLeyendo] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [propuestas, setPropuestas] = useState<Clase[] | null>(null)
  const [editando, setEditando] = useState<Clase | 'nueva' | null>(null)
  const [apunteDe, setApunteDe] = useState<Clase | null>(null)
  const [ahora, setAhora] = useState(new Date())
  const entrada = useRef<HTMLInputElement>(null)
  const refsDia = useRef<Record<number, HTMLDivElement | null>>({})

  const recargar = () => repo.horario().then(setClases).catch(() => {})
  // Amigos que comparten su horario: para mostrar quién va contigo a cada clase.
  const [horariosAmigos, setHorariosAmigos] = useState<Map<Persona, Clase[]>>(new Map())
  useEffect(() => {
    repo.misAmigos().then(async (gente) => {
      const amigos = gente.filter((p) => p.estado === 'aceptada')
      const porId = await repo.horariosDe(amigos.map((p) => p.id))
      setHorariosAmigos(new Map(amigos.filter((p) => porId[p.id]?.length).map((p) => [p, porId[p.id]])))
    }).catch(() => {})
  }, [])
  useEffect(() => { recargar().finally(() => setCargando(false)) }, [])
  useEffect(() => { const t = setInterval(() => setAhora(new Date()), 60_000); return () => clearInterval(t) }, [])

  const hoy = diaDeHoy(ahora)
  const porDia = new Map<number, Clase[]>()
  for (const c of clases) porDia.set(c.dia, [...(porDia.get(c.dia) ?? []), c])
  const dias = [...porDia.keys()].sort((a, b) => a - b)

  // Al abrir, baja hasta hoy (o el siguiente día con clases).
  useEffect(() => {
    if (cargando) return
    const d = dias.find((x) => x >= hoy)
    if (d && d !== dias[0]) refsDia.current[d]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [cargando])

  async function subir(archivo: File) {
    setLeyendo(true); setMensaje(null)
    try {
      const [base64, tipo] = await archivoParaIA(archivo)
      const leidas = await repo.leerHorarioConIA(base64, tipo)
      if (!leidas.length) setMensaje('No encontré clases en ese archivo. Prueba con una foto más clara.')
      else setPropuestas(leidas)
    } catch (e) {
      const t = String((e as Error).message ?? e)
      setMensaje(t.includes('Falta configurar') || t.includes('Error de la IA') || t.includes('Error de Gemini')
        ? 'La lectura con IA todavía no está disponible. Mientras tanto, agrega tus clases con el botón "Agregar".'
        : t.includes('8 MB') ? 'Ese PDF es muy grande (máx. 8 MB).'
        : t.includes('límite de lecturas') ? 'Llegaste al límite de lecturas con IA por hoy. Mañana puedes seguir, o agrega tus clases con "Agregar".'
        : 'No se pudo leer el horario. Revisa tu internet e intenta otra vez.')
    }
    setLeyendo(false)
  }

  return (
    <>
      <TituloPantalla titulo="Horario"><Pastilla texto="Amigos" icono="persona" destacada onClick={onAmigos} /></TituloPantalla>
      <div className="fila-pastillas">
        <Pastilla texto={leyendo ? 'Leyendo…' : 'Foto o PDF'} icono="estrella" disabled={leyendo} onClick={() => entrada.current?.click()} />
        <Pastilla texto="Agregar" icono="mas" onClick={() => setEditando('nueva')} />
      </div>
      <input ref={entrada} type="file" accept="image/*,application/pdf" hidden
        onChange={(e) => { const f = e.target.files?.[0]; e.target.value = ''; if (f) subir(f) }} />

      {cargando ? <Cargando /> : (
        <div className="contenido" style={{ gap: 14 }}>
          {mensaje && <div className="aviso">{mensaje}</div>}
          {leyendo && <div className="barra-carga" />}
          <AvisoApp />
          <ProximaClase clases={clases} ahora={ahora} />
          {clases.length > 0 && <TarjetaAvisos />}
          <PrimerosPasos clases={clases.length} irA={irA} subirHorario={() => entrada.current?.click()} />

          {!clases.length && (
            <div className="tarjeta" style={{ padding: 28, textAlign: 'center', marginTop: 8 }}>
              <div style={{ fontSize: 44 }}>🗓️</div>
              <h3 style={{ margin: '12px 0 6px' }}>Aún no tienes clases</h3>
              <p className="suave" style={{ margin: '0 0 16px' }}>Sube una foto o PDF de tu horario y la IA lo arma por ti, o agrega tus clases a mano.</p>
              <button className="boton" onClick={() => entrada.current?.click()}>Subir foto o PDF</button>
            </div>
          )}

          <div className="rejilla">
            {dias.map((d) => (
              <div key={d} ref={(el) => { refsDia.current[d] = el }} style={{ scrollMarginTop: 12 }}>
                <TarjetaDia dia={d} clases={[...porDia.get(d)!].sort((a, b) => minutos(a.hora_inicio) - minutos(b.hora_inicio))}
                  esHoy={d === hoy} yaPaso={d < hoy} onEditar={setEditando} onApunte={setApunteDe}
                  conQuien={(c) => amigosEnClase(c, horariosAmigos)} />
              </div>
            ))}
          </div>
        </div>
      )}

      {propuestas && (
        <Modal onCerrar={() => setPropuestas(null)}>
          <h2>La IA encontró {propuestas.length} clases ✨</h2>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {propuestas.map((c, i) => (
              <div key={i} style={{ display: 'flex', gap: 10 }}>
                <strong style={{ color: 'var(--acento)', width: 40, fontSize: 13 }}>{DIAS[c.dia - 1]?.slice(0, 3)}</strong>
                <div>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>{c.materia}</div>
                  <div className="suave" style={{ fontSize: 12 }}>{hora12(c.hora_inicio)} – {hora12(c.hora_fin)}{c.aula ? ` · Aula ${c.aula}` : ''}</div>
                </div>
              </div>
            ))}
          </div>
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setPropuestas(null)}>Descartar</button>
            <button className="boton" onClick={async () => {
              await repo.guardarHorarioImportado(propuestas).catch(() => {})
              setPropuestas(null); recargar()
            }}>Guardar</button>
          </div>
        </Modal>
      )}

      {editando && (
        <DialogoClase inicial={editando === 'nueva' ? null : editando} onCerrar={() => setEditando(null)}
          onGuardar={async (c) => {
            setEditando(null)
            await (c.id ? repo.actualizarClase(c) : repo.agregarClases([c])).catch(() => {})
            recargar()
          }}
          onBorrar={async (c) => { setEditando(null); if (c.id) await repo.borrarClase(c.id).catch(() => {}); recargar() }} />
      )}

      {apunteDe && (
        <DialogoApunte clase={apunteDe} onCerrar={() => setApunteDe(null)} onGuardar={async (t) => {
          const c = apunteDe
          setApunteDe(null)
          await repo.actualizarClase({ ...c, apunte: t.trim() || null }).catch(() => {})
          recargar()
        }} />
      )}
    </>
  )
}

function ProximaClase({ clases, ahora }: { clases: Clase[]; ahora: Date }) {
  if (!clases.length) return null
  const hoy = diaDeHoy(ahora)
  const min = ahora.getHours() * 60 + ahora.getMinutes()
  const deHoy = clases.filter((c) => c.dia === hoy).sort((a, b) => minutos(a.hora_inicio) - minutos(b.hora_inicio))
  const actual = deHoy.find((c) => minutos(c.hora_inicio) <= min && min < minutos(c.hora_fin))
  const siguiente = deHoy.find((c) => minutos(c.hora_inicio) > min)

  let titulo = 'Próxima clase', clase: Clase | undefined, detalle = ''
  if (actual) { titulo = 'Ahora'; clase = actual; detalle = `hasta las ${hora12(actual.hora_fin)}` }
  else if (siguiente) { clase = siguiente; detalle = `${hora12(siguiente.hora_inicio)} · ${enCuanto(minutos(siguiente.hora_inicio) - min)}` }
  else {
    for (let i = 1; i <= 7 && !clase; i++) {
      const d = ((hoy - 1 + i) % 7) + 1
      clase = clases.filter((c) => c.dia === d).sort((a, b) => minutos(a.hora_inicio) - minutos(b.hora_inicio))[0]
      if (clase) detalle = `${i === 1 ? 'Mañana' : DIAS[d - 1]} · ${hora12(clase.hora_inicio)}`
    }
  }
  if (!clase) return null
  const color = actual ? 'var(--verde)' : 'var(--acento)'
  return (
    <div className="tarjeta" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16,
      borderColor: 'color-mix(in srgb, var(--acento) 50%, transparent)', background: 'color-mix(in srgb, var(--acento-suave) 35%, var(--tarjeta))' }}>
      <span style={{ width: 44, height: 44, borderRadius: 14, background: color, color: '#fff', display: 'grid', placeItems: 'center', fontSize: 18 }}>{actual ? '●' : '⏰'}</span>
      <span>
        <span style={{ display: 'block', color, fontSize: 12, fontWeight: 700, letterSpacing: '.08em' }}>{titulo.toUpperCase()}</span>
        <strong style={{ display: 'block', fontSize: 17 }}>{clase.materia}</strong>
        <span className="suave" style={{ fontSize: 13 }}>{detalle}{clase.aula ? ` · Aula ${clase.aula}` : ''}</span>
      </span>
    </div>
  )
}

function TarjetaDia({ dia, clases, esHoy, yaPaso, onEditar, onApunte, conQuien }: {
  dia: number; clases: Clase[]; esHoy: boolean; yaPaso: boolean; onEditar: (c: Clase) => void; onApunte: (c: Clase) => void
  conQuien: (c: Clase) => Persona[]
}) {
  return (
    <div className="tarjeta" style={{ opacity: yaPaso ? 0.6 : 1, borderColor: esHoy ? 'var(--acento)' : undefined }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '14px 18px' }}>
        <strong style={{ fontSize: 19 }}>{DIAS[dia - 1]}</strong>
        {esHoy && <span style={{ background: 'var(--acento)', color: '#fff', fontSize: 12, fontWeight: 700, padding: '3px 8px', borderRadius: 8 }}>HOY</span>}
        <span style={{ flex: 1 }} />
        <span className="suave" style={{ fontSize: 14 }}>{clases.length === 1 ? '1 clase' : `${clases.length} clases`}</span>
      </div>
      <hr className="divisor" />
      {clases.map((c, i) => (
        <div key={c.id ?? i}>
          {i > 0 && <hr className="divisor" style={{ marginLeft: 86 }} />}
          <FilaClase c={c} onEditar={() => onEditar(c)} onApunte={() => onApunte(c)} amigos={conQuien(c)} />
        </div>
      ))}
    </div>
  )
}

function FilaClase({ c, onEditar, onApunte, amigos = [] }: { c: Clase; onEditar: () => void; onApunte: () => void; amigos?: Persona[] }) {
  const nombres = amigos.map((p) => nombreVisible(p).split(' ')[0])
  const [h, sufijo] = hora12(c.hora_inicio).split(' ')
  return (
    <div style={{ display: 'flex', padding: '14px 0' }}>
      <div style={{ width: 72, paddingLeft: 18, display: 'flex', flexDirection: 'column', justifyContent: 'space-between', flex: 'none' }}>
        <div><strong style={{ fontSize: 18, display: 'block' }}>{h}</strong><span style={{ fontSize: 15, fontWeight: 600 }}>{sufijo}</span></div>
        <span className="suave" style={{ fontSize: 12 }}>{hora12(c.hora_fin)}</span>
      </div>
      <div style={{ width: 14, display: 'flex', flexDirection: 'column', alignItems: 'center', flex: 'none' }}>
        <span style={{ width: 10, height: 10, borderRadius: '50%', border: '2px solid var(--acento)', marginTop: 6 }} />
        <span style={{ width: 2, flex: 1, background: 'var(--borde)' }} />
      </div>
      <div style={{ flex: 1, minWidth: 0, padding: '0 14px 0 12px', display: 'flex', flexDirection: 'column', gap: 4 }}>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
          <strong style={{ flex: 1, fontSize: 17 }}>{c.materia}</strong>
          <Pastilla texto="Editar" icono="editar" chica onClick={onEditar} />
        </div>
        {c.codigo && <span className="mono suave" style={{ fontSize: 13 }}>{c.codigo}</span>}
        {c.profesor && <span className="suave" style={{ fontSize: 14 }}>{c.profesor}</span>}
        {c.grupo && <span className="suave" style={{ fontSize: 14 }}>Grupo {c.grupo}</span>}
        {c.aula && <span style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 13 }} className="mono"><Icono n="lugar" tam={15} color="var(--texto-suave)" />Aula {c.aula}</span>}
        {nombres.length > 0 && (
          <span style={{ color: 'var(--acento)', fontSize: 13, fontWeight: 600 }}>
            👥 Con {nombres.length <= 2 ? nombres.join(' y ') : `${nombres.slice(0, 2).join(', ')} y ${nombres.length - 2} más`}
          </span>
        )}
        {c.apunte && <div style={{ background: 'var(--tarjeta-alta)', borderRadius: 12, padding: 10, fontSize: 13, marginTop: 4, whiteSpace: 'pre-wrap' }}>📝 {c.apunte}</div>}
        <div style={{ marginTop: 2 }}><Pastilla texto={c.apunte ? 'Editar apunte' : 'Agregar apunte'} chica onClick={onApunte} /></div>
      </div>
    </div>
  )
}

function DialogoClase({ inicial, onCerrar, onGuardar, onBorrar }: {
  inicial: Clase | null; onCerrar: () => void; onGuardar: (c: Clase) => void; onBorrar: (c: Clase) => void
}) {
  const [c, setC] = useState<Clase>(inicial ?? { materia: '', dia: diaDeHoy() > 6 ? 1 : diaDeHoy(), hora_inicio: '08:00', hora_fin: '10:00' })
  const [confirmar, setConfirmar] = useState(false)
  const campo = (k: keyof Clase) => ({
    value: (c[k] as string | null | undefined) ?? '',
    onChange: (e: ChangeEvent<HTMLInputElement>) => setC({ ...c, [k]: e.target.value }),
  })
  const valido = c.materia.trim() && minutos(c.hora_inicio) < minutos(c.hora_fin)
  const limpio = (s?: string | null) => s?.trim() || null

  return (
    <Modal onCerrar={onCerrar}>
      <h2>{inicial ? 'Editar clase' : 'Agregar clase'}</h2>
      <div><label className="etiqueta-campo">Materia</label><input className="campo" {...campo('materia')} autoFocus={!inicial} /></div>
      <div style={{ display: 'flex', gap: 8 }}>
        <div style={{ flex: 1 }}><label className="etiqueta-campo">Código</label><input className="campo mono" {...campo('codigo')} /></div>
        <div style={{ width: 110 }}><label className="etiqueta-campo">Grupo</label><input className="campo" {...campo('grupo')} /></div>
      </div>
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
        {DIAS.slice(0, 6).map((d, i) => (
          <button key={d} className="pastilla chica" onClick={() => setC({ ...c, dia: i + 1 })}
            style={c.dia === i + 1 ? { background: 'var(--acento-suave)', color: 'var(--acento)', borderColor: 'var(--acento)' } : undefined}>
            {d.slice(0, 2)}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: 8 }}>
        <div style={{ flex: 1 }}><label className="etiqueta-campo">Inicio</label><input className="campo" type="time" {...campo('hora_inicio')} /></div>
        <div style={{ flex: 1 }}><label className="etiqueta-campo">Fin</label><input className="campo" type="time" {...campo('hora_fin')} /></div>
      </div>
      <div><label className="etiqueta-campo">Aula (opcional)</label><input className="campo" {...campo('aula')} /></div>
      <div><label className="etiqueta-campo">Profesor (opcional)</label><input className="campo" {...campo('profesor')} /></div>
      {inicial && (
        <button className="boton-texto" style={{ color: 'var(--rojo)' }} onClick={() => (confirmar ? onBorrar(inicial) : setConfirmar(true))}>
          {confirmar ? 'Toca otra vez para borrar' : 'Borrar esta clase'}
        </button>
      )}
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={onCerrar}>Cancelar</button>
        <button className="boton" disabled={!valido} onClick={() => onGuardar({
          ...c, materia: c.materia.trim(), hora_inicio: c.hora_inicio.slice(0, 5), hora_fin: c.hora_fin.slice(0, 5),
          aula: limpio(c.aula), profesor: limpio(c.profesor), codigo: limpio(c.codigo)?.toUpperCase() ?? null, grupo: limpio(c.grupo),
        })}>Guardar</button>
      </div>
    </Modal>
  )
}

function DialogoApunte({ clase, onCerrar, onGuardar }: { clase: Clase; onCerrar: () => void; onGuardar: (t: string) => void }) {
  const [texto, setTexto] = useState(clase.apunte ?? '')
  return (
    <Modal onCerrar={onCerrar}>
      <h2>Apunte · {clase.materia}</h2>
      <textarea className="campo" rows={5} placeholder="Tarea, examen, qué llevar…" value={texto}
        onChange={(e) => setTexto(e.target.value)} autoFocus />
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={onCerrar}>Cancelar</button>
        <button className="boton" onClick={() => onGuardar(texto)}>Guardar</button>
      </div>
    </Modal>
  )
}
