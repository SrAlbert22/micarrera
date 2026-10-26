import { useEffect, useState, type ReactNode } from 'react'
import * as repo from '../repo'
import type { Progreso } from '../tipos'
import { Cargando, TituloPantalla } from '../comunes'
import { DIAS, diaDeHoy } from '../util'
import { calcular, type Estadisticas as Datos, type PeriodoStats } from '../estadisticas'

const num = (x: number) => (Number.isInteger(x) ? String(x) : x.toFixed(1))

export default function Estadisticas({ carreraId }: { carreraId: string }) {
  const [e, setE] = useState<Datos | null>(null)

  useEffect(() => {
    Promise.all([
      repo.materias(carreraId).catch(() => []),
      repo.progreso().catch(() => [] as Progreso[]),
      repo.perfil().catch(() => null),
      repo.horario().catch(() => []),
    ]).then(([materias, progreso, perfil, clases]) => {
      setE(calcular(
        materias,
        Object.fromEntries(progreso.map((p) => [p.materia_id, p.estado])),
        Object.fromEntries(progreso.filter((p) => p.nota != null || p.letra != null).map((p) => [p.materia_id, p])),
        perfil?.indice ?? null,
        clases,
      ))
    })
  }, [carreraId])

  if (!e) return (<><TituloPantalla titulo="Estadísticas" /><Cargando /></>)
  return <VistaEstadisticas e={e} />
}

/** La vista, separada de la carga de datos (así se puede ver con datos de ejemplo en demo-estadisticas.html). */
export function VistaEstadisticas({ e }: { e: Datos }) {
  const pct = e.credTotal ? Math.floor((e.credAprobados * 100) / e.credTotal) : 0
  const hoy = diaDeHoy()

  return (
    <>
      <TituloPantalla titulo="Estadísticas" />
      <div className="contenido" style={{ gap: 14 }}>
        <div className="cifras">
          <Cifra etiqueta="Promedio de notas" valor={e.promedio != null ? num(Math.round(e.promedio * 10) / 10) : '—'}
            detalle={e.materiasConNota ? `de 100 · ${e.materiasConNota} materias` : 'Sin notas todavía'} />
          <Cifra etiqueta="Índice" valor={e.indice != null ? e.indice.toFixed(2) : '—'} detalle={e.indice != null ? 'de 4.00' : 'Se trae del portal'} />
          <Cifra etiqueta="Créditos aprobados" valor={String(e.credAprobados)} detalle={`de ${e.credTotal} · ${pct} %`} />
          <Cifra etiqueta="Clases por semana" valor={`${num(e.horasSemana)} h`}
            detalle={e.horasSemana ? `en ${e.horasPorDia.filter(([, h]) => h > 0).length} días` : 'Sin horario todavía'} />
        </div>

        <div className="rejilla">
          <Tarjeta titulo="Tus notas" subtitulo={e.letras.some(([, n]) => n > 0)
            ? `Cuántas materias tienes con cada letra${e.letrasDesdeNota ? ' (algunas según tu nota)' : ''}`
            : 'Importa tu récord del portal (en la app de Android) para ver tus notas.'}>
            {e.letras.some(([, n]) => n > 0) && (
              <Columnas datos={e.letras.map(([l, n]) => ({ etiqueta: l, valor: n, color: 'var(--acento)', tip: `${l}: ${n} ${n === 1 ? 'materia' : 'materias'}` }))}
                formato={(v) => String(v)} />
            )}
          </Tarjeta>

          {e.horasSemana > 0 && (
            <Tarjeta titulo="Horas de clase por día" subtitulo={`Total: ${num(e.horasSemana)} h a la semana · hoy en color`}>
              <Columnas datos={e.horasPorDia.map(([d, h]) => ({
                etiqueta: DIAS[d - 1].slice(0, 2), valor: h, color: d === hoy ? 'var(--acento)' : 'var(--gris)', tip: `${DIAS[d - 1]}: ${num(h)} h`,
              }))} formato={(v) => (v > 0 ? `${num(v)} h` : '')} />
            </Tarjeta>
          )}

          {e.periodos.length > 0 && (
            <Tarjeta titulo="Créditos por período" subtitulo="Aprobados y en curso de cada período">
              <div style={{ display: 'flex', gap: 16, marginBottom: 12, flexWrap: 'wrap' }}>
                <Leyenda color="var(--g-aprobado)" texto="Aprobados" />
                <Leyenda color="var(--g-curso)" texto="En curso" />
                <Leyenda color="var(--tarjeta-alta)" texto="Pendientes" />
              </div>
              {e.periodos.map((p) => <FilaPeriodo key={p.numero} p={p} />)}
            </Tarjeta>
          )}

          {e.mejores.length > 0 && (
            <Tarjeta titulo="Tus mejores notas">
              {e.mejores.map(([m, n], i) => <FilaNota key={m} marca={String(i + 1)} materia={m} nota={n} />)}
            </Tarjeta>
          )}
          {e.reforzar.length > 0 && (
            <Tarjeta titulo="Para reforzar" subtitulo="Materias con menos de 80">
              {e.reforzar.map(([m, n]) => <FilaNota key={m} marca="•" materia={m} nota={n} />)}
            </Tarjeta>
          )}
          {e.graduacion && (
            <Tarjeta titulo="Proyección">
              <strong style={{ fontSize: 17 }}>{e.periodosFaltan === 1 ? 'Te falta 1 período.' : `Te faltan ${e.periodosFaltan} períodos.`}</strong>
              <p className="suave" style={{ margin: '4px 0 0', fontSize: 14 }}>
                Si tomas un período completo cada cuatrimestre, terminarías cerca de{' '}
                {e.graduacion.toLocaleDateString('es-DO', { month: 'long', year: 'numeric' })}.
              </p>
            </Tarjeta>
          )}
        </div>
      </div>
    </>
  )
}

function Cifra({ etiqueta, valor, detalle }: { etiqueta: string; valor: string; detalle: string }) {
  return (
    <div className="tarjeta" style={{ padding: 16 }}>
      <div className="suave" style={{ fontSize: 13 }}>{etiqueta}</div>
      <div style={{ fontSize: 30, fontWeight: 600 }}>{valor}</div>
      <div className="suave" style={{ fontSize: 12, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{detalle}</div>
    </div>
  )
}

function Tarjeta({ titulo, subtitulo, children }: { titulo: string; subtitulo?: string; children?: ReactNode }) {
  return (
    <section className="tarjeta" style={{ padding: 18 }}>
      <h2 style={{ margin: 0, fontSize: 17 }}>{titulo}</h2>
      {subtitulo && <div className="suave" style={{ fontSize: 13 }}>{subtitulo}</div>}
      {children && <div style={{ marginTop: 14 }}>{children}</div>}
    </section>
  )
}

/** Columnas de una sola serie. Al pasar el mouse (o al tocar) cada columna muestra su valor. */
function Columnas({ datos, formato }: { datos: { etiqueta: string; valor: number; color: string; tip: string }[]; formato: (v: number) => string }) {
  const max = Math.max(...datos.map((d) => d.valor), 0) || 1
  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'flex-end', height: 140 }}>
        {datos.map((d) => (
          <div key={d.etiqueta} className="columna" data-tip={d.tip} tabIndex={0} aria-label={d.tip}>
            <span style={{ fontSize: 12, fontWeight: 600 }}>{formato(d.valor)}</span>
            <span style={{ width: 24, height: `${(d.valor / max) * 120}px`, background: d.color, borderRadius: '4px 4px 0 0', marginTop: 4 }} />
          </div>
        ))}
      </div>
      <div style={{ height: 1, background: 'var(--borde)' }} />
      <div style={{ display: 'flex', marginTop: 6 }}>
        {datos.map((d) => <span key={d.etiqueta} className="suave" style={{ flex: 1, textAlign: 'center', fontSize: 13 }}>{d.etiqueta}</span>)}
      </div>
    </div>
  )
}

function FilaPeriodo({ p }: { p: PeriodoStats }) {
  const pendientes = Math.max(p.total - p.aprobados - p.enCurso, 0)
  const tip = `Período ${p.numero}: ${p.aprobados} aprobados, ${p.enCurso} en curso, ${pendientes} pendientes (de ${p.total} créditos)`
  const segmentos: [number, string][] = [[p.aprobados, 'var(--g-aprobado)'], [p.enCurso, 'var(--g-curso)'], [pendientes, 'var(--tarjeta-alta)']]
  return (
    <div className="columna-fila" data-tip={tip} tabIndex={0} aria-label={tip}
      style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '5px 0' }}>
      <span className="suave" style={{ width: 30, fontSize: 13 }}>{p.numero}.º</span>
      <span style={{ flex: 1, display: 'flex', gap: 2, height: 14 }}>
        {segmentos.filter(([n]) => n > 0).map(([n, c], i) => (
          <span key={i} style={{ flex: n, background: c, borderRadius: 4 }} />
        ))}
      </span>
      <strong style={{ width: 52, textAlign: 'right', fontSize: 13 }}>{p.aprobados}/{p.total}</strong>
    </div>
  )
}

function Leyenda({ color, texto }: { color: string; texto: string }) {
  return (
    <span className="suave" style={{ display: 'inline-flex', alignItems: 'center', gap: 6, fontSize: 12 }}>
      <span className="punto" style={{ background: color, width: 10, height: 10, boxShadow: 'inset 0 0 0 1px var(--gris)' }} />{texto}
    </span>
  )
}

function FilaNota({ marca, materia, nota }: { marca: string; materia: string; nota: number }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', padding: '6px 0', gap: 8 }}>
      <span className="suave" style={{ width: 18 }}>{marca}</span>
      <span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{materia}</span>
      <strong style={{ fontSize: 18 }}>{num(nota)}</strong>
    </div>
  )
}
