import { useEffect, useMemo, useState } from 'react'
import * as repo from '../repo'
import type { CarreraDetalle, Estado, Materia, Progreso } from '../tipos'
import { Cargando, Etiqueta, Icono, LogoUniversidad, Pastilla, TituloPantalla } from '../comunes'
import { norm, ordinal, textoNota } from '../util'

const SIGUIENTE: Record<Estado, Estado> = { pendiente: 'en_curso', en_curso: 'aprobada', aprobada: 'pendiente' }

type Filtro = 'todas' | 'disponibles' | 'en_curso' | 'aprobadas' | 'pendientes'
const FILTROS: { id: Filtro; nombre: string; color: string; fondo: string }[] = [
  { id: 'todas', nombre: 'Todas', color: 'var(--texto-suave)', fondo: 'var(--tarjeta)' },
  { id: 'disponibles', nombre: 'Disponibles', color: 'var(--acento)', fondo: 'var(--acento-suave)' },
  { id: 'en_curso', nombre: 'En curso', color: 'var(--ambar)', fondo: 'var(--ambar-fondo)' },
  { id: 'aprobadas', nombre: 'Aprobadas', color: 'var(--verde)', fondo: 'var(--verde-fondo)' },
  { id: 'pendientes', nombre: 'Pendientes', color: 'var(--texto-suave)', fondo: 'var(--tarjeta)' },
]

export default function Pensum({ carreraId, onCambiarCarrera }: { carreraId: string; onCambiarCarrera: () => void }) {
  const [materias, setMaterias] = useState<Materia[]>([])
  const [estados, setEstados] = useState<Record<string, Estado>>({})
  const [notas, setNotas] = useState<Record<string, Progreso>>({})
  const [carrera, setCarrera] = useState<CarreraDetalle | null>(null)
  const [indice, setIndice] = useState<number | null>(null)
  const [conectado, setConectado] = useState(false)
  const [cargando, setCargando] = useState(true)
  const [filtro, setFiltro] = useState<Filtro>('todas')
  const [abiertos, setAbiertos] = useState<Record<number, boolean>>({})

  useEffect(() => {
    Promise.allSettled([
      repo.materias(carreraId).then(setMaterias),
      repo.progreso().then((lista) => {
        setEstados(Object.fromEntries(lista.map((p) => [p.materia_id, p.estado])))
        setNotas(Object.fromEntries(lista.filter((p) => p.nota != null || p.letra != null).map((p) => [p.materia_id, p])))
      }),
      repo.carreraDetalle(carreraId).then(setCarrera),
      repo.perfil().then((p) => { setIndice(p?.indice ?? null); setConectado(!!p?.portal_actualizado) }),
    ]).finally(() => setCargando(false))
  }, [carreraId])

  const estadoDe = (m: Materia): Estado => estados[m.id] ?? 'pendiente'
  const porCodigo = useMemo(() => new Map(materias.map((m) => [norm(m.codigo), m])), [materias])
  const estadoDeCodigo = (c: string): Estado | null => {
    const m = porCodigo.get(norm(c))
    return m ? estadoDe(m) : null
  }
  const disponible = (m: Materia) =>
    estadoDe(m) === 'pendiente' && m.prerequisitos.every((p) => estadoDeCodigo(p) === 'aprobada' || !porCodigo.has(norm(p)))

  const pasa = (m: Materia) => ({
    todas: true,
    disponibles: disponible(m),
    en_curso: estadoDe(m) === 'en_curso',
    aprobadas: estadoDe(m) === 'aprobada',
    pendientes: estadoDe(m) === 'pendiente',
  })[filtro]

  const cuenta: Record<Filtro, number> = {
    todas: materias.length,
    disponibles: materias.filter(disponible).length,
    en_curso: materias.filter((m) => estadoDe(m) === 'en_curso').length,
    aprobadas: materias.filter((m) => estadoDe(m) === 'aprobada').length,
    pendientes: materias.filter((m) => estadoDe(m) === 'pendiente').length,
  }

  function cambiar(m: Materia) {
    const nuevo = SIGUIENTE[estadoDe(m)]
    setEstados((e) => ({ ...e, [m.id]: nuevo }))
    repo.marcar(m.id, nuevo).catch(() => {})
  }

  const grupos = useMemo(() => {
    const g = new Map<number, Materia[]>()
    for (const m of materias) g.set(m.cuatrimestre, [...(g.get(m.cuatrimestre) ?? []), m])
    return [...g.entries()].sort((a, b) => a[0] - b[0])
  }, [materias])

  return (
    <>
      <TituloPantalla titulo="Pensum">
        <Pastilla texto="Cambiar" iconoFinal="abajo" onClick={onCambiarCarrera} />
      </TituloPantalla>

      {cargando ? <Cargando /> : (
        <div className="contenido">
          <Resumen carrera={carrera} materias={materias} estadoDe={estadoDe} indice={indice} conectado={conectado} />

          <div className="fila-pastillas" style={{ margin: '0 -16px', alignItems: 'center' }}>
            <span className="seccion" style={{ margin: 0 }}>Filtro</span>
            {FILTROS.map((f) => {
              const sel = filtro === f.id
              return (
                <button key={f.id} onClick={() => setFiltro(sel ? 'todas' : f.id)} className="pastilla"
                  style={{ borderRadius: 14, background: sel ? f.fondo : 'var(--tarjeta)', borderColor: sel ? f.color : 'var(--borde)', color: sel ? f.color : 'var(--texto)' }}>
                  <span className="punto" style={{ background: f.color }} />{f.nombre} {cuenta[f.id]}
                </button>
              )
            })}
          </div>

          {filtro === 'todas' && cuenta.disponibles > 0 && (
            <button className="tarjeta clic" onClick={() => setFiltro('disponibles')}
              style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 16, textAlign: 'left',
                borderColor: 'color-mix(in srgb, var(--acento) 60%, transparent)', background: 'color-mix(in srgb, var(--acento-suave) 35%, var(--tarjeta))' }}>
              <span style={{ fontSize: 22 }}>✨</span>
              <span>
                <strong style={{ color: 'var(--acento)', display: 'block', fontSize: 16 }}>Puedes inscribir {cuenta.disponibles} materias</strong>
                <span className="suave" style={{ fontSize: 13 }}>Ya cumples sus requisitos. Toca para verlas.</span>
              </span>
            </button>
          )}

          <div className="rejilla">
          {grupos.map(([periodo, lista]) => {
            const visibles = lista.filter(pasa)
            if (!visibles.length) return null
            const completo = lista.every((m) => estadoDe(m) === 'aprobada')
            const abierto = filtro !== 'todas' ? true : abiertos[periodo] ?? !completo
            const toggle = () => setAbiertos((a) => ({ ...a, [periodo]: !abierto }))

            if (completo && !abierto) return (
              <button key={periodo} className="tarjeta clic" onClick={toggle}
                style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16, textAlign: 'left', background: 'var(--verde-fondo)', borderColor: 'color-mix(in srgb, var(--verde) 30%, transparent)' }}>
                <span style={{ width: 40, height: 40, borderRadius: 12, background: 'var(--verde)', display: 'grid', placeItems: 'center' }}><Icono n="check" color="#fff" /></span>
                <span style={{ flex: 1 }}>
                  <strong style={{ color: 'var(--verde)', display: 'block', fontSize: 17 }}>{ordinal(periodo)} período</strong>
                  <span style={{ color: 'var(--verde)', opacity: .8, fontSize: 13 }}>{lista.length} materias aprobadas · {lista.reduce((s, m) => s + m.creditos, 0)} cr.</span>
                </span>
                <Icono n="abajo" color="var(--verde)" />
              </button>
            )

            const enCurso = lista.filter((m) => estadoDe(m) === 'en_curso').length
            const hechas = lista.filter((m) => estadoDe(m) === 'aprobada').length
            return (
              <section key={periodo} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                <button onClick={toggle} style={{ display: 'flex', alignItems: 'center', gap: 8, background: 'none', border: 0, padding: '8px 4px 2px' }}>
                  <strong style={{ fontSize: 18 }}>{ordinal(periodo)} período</strong>
                  {enCurso > 0 && <Etiqueta texto={`${enCurso} en curso`} color="var(--ambar)" fondo="var(--ambar-fondo)" />}
                  <span style={{ flex: 1 }} />
                  <span className="suave" style={{ fontSize: 14 }}>{hechas}/{lista.length} mat.</span>
                  <Icono n={abierto ? 'arriba' : 'abajo'} color="var(--texto-suave)" />
                </button>
                {abierto && (
                  <div className="tarjeta">
                    {visibles.map((m, i) => (
                      <div key={m.id}>
                        {i > 0 && <hr className="divisor" />}
                        <FilaMateria m={m} estado={estadoDe(m)} disponible={disponible(m)} nota={notas[m.id]}
                          estadoDeCodigo={estadoDeCodigo} onClick={() => cambiar(m)} />
                      </div>
                    ))}
                  </div>
                )}
              </section>
            )
          })}
          </div>

          <p className="suave" style={{ fontSize: 12, margin: '0 4px' }}>
            {materias.length ? 'Toca una materia para cambiar su estado: pendiente → en curso → aprobada.' : 'Esta carrera todavía no tiene materias cargadas.'}
          </p>
        </div>
      )}
    </>
  )
}

function Resumen({ carrera, materias, estadoDe, indice, conectado }: {
  carrera: CarreraDetalle | null; materias: Materia[]; estadoDe: (m: Materia) => Estado; indice: number | null; conectado: boolean
}) {
  const suma = (f: (m: Materia) => boolean) => materias.filter(f).reduce((s, m) => s + m.creditos, 0)
  const credTotal = materias.reduce((s, m) => s + m.creditos, 0)
  const uni = carrera?.universidades
  return (
    <div className="tarjeta">
      <div style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 18 }}>
        <LogoUniversidad u={uni} tam={56} />
        <span style={{ minWidth: 0 }}>
          <strong style={{ fontSize: 19, display: 'block' }}>{carrera?.nombre ?? 'Mi carrera'}</strong>
          {uni && <span className="suave" style={{ fontSize: 13 }}>{uni.nombre}</span>}
        </span>
      </div>
      <hr className="divisor" />
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 16, padding: 18 }}>
        <Estadistica titulo="Créditos" hechos={suma((m) => estadoDe(m) === 'aprobada')} curso={suma((m) => estadoDe(m) === 'en_curso')} de={credTotal} />
        <Estadistica titulo="Materias" hechos={materias.filter((m) => estadoDe(m) === 'aprobada').length}
          curso={materias.filter((m) => estadoDe(m) === 'en_curso').length} de={materias.length} />
        <div>
          <div className="seccion" style={{ margin: 0 }}>Índice</div>
          {indice != null ? (
            <>
              <div style={{ marginTop: 6 }}><strong style={{ fontSize: 26, color: 'var(--acento)' }}>{indice.toFixed(2)}</strong><span className="suave"> / 4.00</span></div>
              <Barra hecho={indice / 4} curso={0} />
            </>
          ) : (
            <>
              <div style={{ marginTop: 6, fontSize: 26, fontWeight: 700 }} className="suave">—</div>
              <div className="suave" style={{ fontSize: 12 }}>{conectado ? 'Sin índice' : 'Se trae del portal en Android'}</div>
            </>
          )}
        </div>
      </div>
    </div>
  )
}

function Estadistica({ titulo, hechos, curso, de }: { titulo: string; hechos: number; curso: number; de: number }) {
  const base = Math.max(de, 1)
  return (
    <div>
      <div className="seccion" style={{ margin: 0 }}>{titulo}</div>
      <div style={{ marginTop: 6 }}><strong style={{ fontSize: 26 }}>{hechos}</strong><span className="suave"> / {de}</span></div>
      <Barra hecho={hechos / base} curso={curso / base} />
      <div style={{ marginTop: 8 }}>
        <strong style={{ color: 'var(--acento)', fontSize: 17 }}>{Math.floor((hechos * 100) / base)}%</strong>
        {curso > 0 && <span className="suave" style={{ fontSize: 11 }}> +{Math.floor((curso * 100) / base)}% en curso</span>}
      </div>
    </div>
  )
}

function Barra({ hecho, curso }: { hecho: number; curso: number }) {
  const p = (x: number) => `${Math.min(Math.max(x, 0), 1) * 100}%`
  return (
    <div style={{ position: 'relative', height: 8, borderRadius: 99, background: 'var(--tarjeta-alta)', overflow: 'hidden', marginTop: 8 }}>
      <div style={{ position: 'absolute', inset: 0, width: p(hecho + curso), background: 'color-mix(in srgb, var(--acento) 40%, transparent)' }} />
      <div style={{ position: 'absolute', inset: 0, width: p(hecho), background: 'var(--acento)' }} />
    </div>
  )
}

function FilaMateria({ m, estado, disponible, nota, estadoDeCodigo, onClick }: {
  m: Materia; estado: Estado; disponible: boolean; nota?: Progreso; estadoDeCodigo: (c: string) => Estado | null; onClick: () => void
}) {
  const aprobada = estado === 'aprobada'
  const colorReq = (e: Estado | null) =>
    e === 'aprobada' ? 'var(--verde)' : e === 'en_curso' ? 'var(--ambar)' : e == null ? 'var(--texto-suave)' : 'var(--rojo)'
  return (
    <button onClick={onClick} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: '14px 16px', width: '100%',
      background: 'none', border: 0, textAlign: 'left' }}>
      <IconoEstado estado={estado} disponible={disponible} />
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontSize: 16, fontWeight: 600, color: aprobada ? 'var(--texto-suave)' : undefined,
          textDecoration: aprobada ? 'line-through' : undefined }}>{m.nombre}</span>
        <span style={{ display: 'flex', flexWrap: 'wrap', gap: '2px 8px', marginTop: 4, fontSize: 13 }}>
          <span className="mono suave">{m.codigo}</span>
          {m.creditos > 0 && <span className="suave">· {m.creditos} cr.</span>}
          {m.prerequisitos.map((p) => <span key={p} className="mono" style={{ color: colorReq(estadoDeCodigo(p)) }}>🔗 {p}</span>)}
        </span>
      </span>
      {nota && (nota.nota != null || nota.letra) ? (
        <span style={{ whiteSpace: 'nowrap' }}>
          <strong style={{ fontSize: 24 }}>{textoNota(nota.nota)}</strong>
          {nota.letra && <span className="suave" style={{ fontSize: 16, fontWeight: 600 }}> {nota.letra}</span>}
        </span>
      ) : estado === 'en_curso' ? <Etiqueta texto="En curso" color="var(--ambar)" fondo="var(--ambar-fondo)" />
        : disponible ? <Etiqueta texto="Disponible" color="var(--acento)" fondo="var(--acento-suave)" /> : null}
    </button>
  )
}

function IconoEstado({ estado, disponible }: { estado: Estado; disponible: boolean }) {
  const base = { width: 28, height: 28, borderRadius: '50%', flex: 'none' as const, display: 'grid', placeItems: 'center' }
  if (estado === 'aprobada') return <span style={{ ...base, background: 'var(--verde)' }}><Icono n="check" tam={18} color="#fff" /></span>
  if (estado === 'en_curso') return (
    <span style={{ ...base, background: 'conic-gradient(var(--ambar) 0 65%, transparent 65%)',
      WebkitMask: 'radial-gradient(circle, transparent 10px, #000 11px)', mask: 'radial-gradient(circle, transparent 10px, #000 11px)' }} />
  )
  return <span style={{ ...base, border: `2px solid ${disponible ? 'var(--acento)' : 'var(--gris)'}` }} />
}
