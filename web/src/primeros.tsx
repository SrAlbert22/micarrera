import { useEffect, useState } from 'react'
import * as repo from './repo'
import { Icono, Modal } from './comunes'

// "Primeros pasos" (igual que en Android, PrimerosPasos.kt) y el aviso para bajar la app en teléfonos Android.

type Destino = 'pensum' | 'horario' | 'estudio'
interface Paso { titulo: string; detalle: string; hecho: boolean; ir: Destino }

function pasos(materiasMarcadas: number, clases: number, tareasOAula: boolean): Paso[] {
  return [
    { titulo: 'Elige tu carrera', detalle: 'Ya está ✓', hecho: true, ir: 'pensum' },
    { titulo: 'Marca las materias que ya aprobaste', detalle: 'Así ves cuánto te falta para graduarte', hecho: materiasMarcadas > 0, ir: 'pensum' },
    { titulo: 'Agrega tu horario', detalle: 'Sube una foto o PDF y la IA lo arma por ti', hecho: clases > 0, ir: 'horario' },
    { titulo: 'Anota una tarea o conecta tu aula virtual', detalle: 'Para que no se te pase ninguna entrega', hecho: tareasOAula, ir: 'estudio' },
  ]
}

function leer(clave: string) { try { return localStorage.getItem(clave) === '1' } catch { return false } }
function guardar(clave: string) { try { localStorage.setItem(clave, '1') } catch { /* sin almacenamiento */ } }

/** [clases] viene de Horario (se actualiza al agregar). "horario" en un paso = abrir el selector de foto o PDF. */
export function PrimerosPasos({ clases, irA, subirHorario }: { clases: number; irA: (p: 'pensum' | 'estudio') => void; subirHorario: () => void }) {
  const [oculta, setOculta] = useState(() => leer('pasosOcultos'))
  const [datos, setDatos] = useState<{ marcadas: number; tareasOAula: boolean } | null>(null)
  useEffect(() => {
    if (oculta) return
    Promise.all([repo.progreso(), repo.tareas(), repo.estadoAula()])
      .then(([p, t, a]) => setDatos({ marcadas: p.length, tareasOAula: t.length > 0 || a != null }))
      .catch(() => {})
  }, [oculta])

  if (oculta || !datos) return null
  const lista = pasos(datos.marcadas, clases, datos.tareasOAula)
  const listos = lista.filter((p) => p.hecho).length
  if (listos === lista.length) return null

  return (
    <div className="tarjeta" style={{ borderColor: 'color-mix(in srgb, var(--acento) 50%, transparent)', padding: '14px 0 8px' }}>
      <div style={{ display: 'flex', alignItems: 'center', padding: '0 10px 0 18px' }}>
        <div style={{ flex: 1 }}>
          <strong style={{ fontSize: 17 }}>Primeros pasos</strong>
          <div className="suave" style={{ fontSize: 13 }}>{listos} de {lista.length} listos</div>
        </div>
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => { guardar('pasosOcultos'); setOculta(true) }}>Ocultar</button>
      </div>
      <div style={{ margin: '10px 18px', height: 6, borderRadius: 3, background: 'var(--acento-suave)', overflow: 'hidden' }}>
        <div style={{ width: `${(listos / lista.length) * 100}%`, height: '100%', background: 'var(--verde)', transition: 'width .3s' }} />
      </div>
      {lista.map((p) => (
        <button key={p.titulo} disabled={p.hecho} onClick={() => (p.ir === 'horario' ? subirHorario() : irA(p.ir))}
          style={{ display: 'flex', alignItems: 'center', gap: 12, width: '100%', padding: '10px 18px', background: 'none', border: 0,
            color: 'inherit', font: 'inherit', textAlign: 'left', cursor: p.hecho ? 'default' : 'pointer' }}>
          <span style={{ width: 26, height: 26, borderRadius: 13, flex: 'none', display: 'grid', placeItems: 'center', color: '#fff',
            background: p.hecho ? 'var(--verde)' : 'var(--acento-suave)' }}>{p.hecho && <Icono n="check" tam={16} />}</span>
          <span style={{ flex: 1, opacity: p.hecho ? 0.6 : 1 }}>
            <span style={{ display: 'block', fontWeight: 600, fontSize: 15 }}>{p.titulo}</span>
            {!p.hecho && <span className="suave" style={{ display: 'block', fontSize: 13 }}>{p.detalle}</span>}
          </span>
          {!p.hecho && <span style={{ color: 'var(--acento)' }}><Icono n="derecha" tam={22} /></span>}
        </button>
      ))}
    </div>
  )
}

const ES_ANDROID = typeof navigator !== 'undefined' && /Android/i.test(navigator.userAgent)

/** En el navegador de un Android: invita a bajar la app (importa el horario del portal y avisa sin internet). */
export function AvisoApp() {
  const [oculto, setOculto] = useState(() => !ES_ANDROID || leer('avisoAppOculto'))
  const [ayuda, setAyuda] = useState(false)
  if (oculto) return null
  const cerrar = () => { guardar('avisoAppOculto'); setOculto(true) }

  return (
    <>
      <div className="tarjeta" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 8px 12px 16px',
        background: 'color-mix(in srgb, var(--acento-suave) 45%, var(--tarjeta))' }}>
        <span style={{ color: 'var(--verde)' }}><Icono n="android" tam={30} /></span>
        <span style={{ flex: 1 }}>
          <strong style={{ display: 'block', fontSize: 15 }}>Bájate la app para Android</strong>
          <span className="suave" style={{ fontSize: 13 }}>Te avisa antes de cada clase y trae tu horario del portal.</span>
        </span>
        <button className="boton" style={{ padding: '8px 14px' }} onClick={() => { setAyuda(true); window.location.href = '/descargas/MiCarrera.apk' }}>Bajar</button>
        <button className="boton-texto" aria-label="Cerrar" style={{ color: 'var(--texto-suave)', padding: 6 }} onClick={cerrar}><Icono n="cerrar" /></button>
      </div>

      {ayuda && (
        <Modal onCerrar={() => setAyuda(false)}>
          <h2>Cómo instalarla 📲</h2>
          <ol style={{ margin: 0, paddingLeft: 20, display: 'flex', flexDirection: 'column', gap: 10, fontSize: 15 }}>
            <li>Si Chrome pregunta si quieres conservar el archivo, toca <strong>Descargar de todas formas</strong>.</li>
            <li>Cuando termine, toca <strong>Abrir</strong> (o abre <strong>MiCarrera.apk</strong> en Descargas).</li>
            <li>Si dice que no se permiten apps de esta fuente, toca <strong>Configuración → Permitir de esta fuente</strong> y vuelve atrás.</li>
            <li>Toca <strong>Instalar</strong> y entra con el mismo correo. Tus clases y materias ya estarán ahí.</li>
          </ol>
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={cerrar}>Ya la tengo</button>
            <button className="boton" onClick={() => setAyuda(false)}>Entendido</button>
          </div>
        </Modal>
      )}
    </>
  )
}
