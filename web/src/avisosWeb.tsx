import { useState } from 'react'
import { Icono, Modal } from './comunes'
import { MINUTOS, useAvisos } from './avisos'

// Tarjeta (Horario) y diálogo (Cuenta) de los avisos del navegador. La lógica está en avisos.ts.

function leer(clave: string) { try { return localStorage.getItem(clave) === '1' } catch { return false } }
function guardar(clave: string) { try { localStorage.setItem(clave, '1') } catch { /* sin almacenamiento */ } }

/** Tarjeta en Horario: invita a activar los avisos (una vez; se puede cerrar). */
export function TarjetaAvisos() {
  const a = useAvisos()
  const [oculta, setOculta] = useState(() => leer('avisosOcultos'))

  if (oculta || a.estado === 'cargando' || a.estado === 'no-soportado') return null
  if (a.estado === 'activo' && !a.recienActivado) return null
  const cerrar = () => { guardar('avisosOcultos'); setOculta(true) }

  return (
    <div className="tarjeta" style={{ display: 'flex', alignItems: 'center', gap: 12, padding: '12px 8px 12px 16px',
      background: 'color-mix(in srgb, var(--acento-suave) 45%, var(--tarjeta))' }}>
      <span style={{ color: 'var(--acento)' }}><Icono n="campana" tam={30} /></span>
      <span style={{ flex: 1 }}>
        {a.estado === 'activo' ? (
          <>
            <strong style={{ display: 'block', fontSize: 15 }}>¡Avisos activados! 🔔</strong>
            <span className="suave" style={{ fontSize: 13 }}>Te aviso {a.minutos} min antes de cada clase. Lo cambias en Cuenta.</span>
          </>
        ) : (
          <>
            <strong style={{ display: 'block', fontSize: 15 }}>¿Te aviso antes de cada clase?</strong>
            <span className="suave" style={{ fontSize: 13 }}>
              {a.estado === 'iphone-instalar'
                ? 'En iPhone, primero agrega MiCarrera a tu inicio: Compartir → Agregar a inicio, y ábrela desde ahí.'
                : a.estado === 'bloqueado'
                  ? 'Las notificaciones están bloqueadas: permítelas para este sitio en la configuración del navegador.'
                  : a.mensaje ?? 'Y de las entregas que vencen pronto. Llegan aunque tengas la web cerrada.'}
            </span>
          </>
        )}
      </span>
      {a.estado === 'apagado' && (
        <button className="boton" style={{ padding: '8px 14px' }} disabled={a.trabajando} onClick={() => a.activar()}>
          {a.trabajando ? 'Activando…' : 'Activar'}
        </button>
      )}
      <button className="boton-texto" aria-label="Cerrar" style={{ color: 'var(--texto-suave)', padding: 6 }} onClick={cerrar}><Icono n="cerrar" /></button>
    </div>
  )
}

/** Diálogo en Cuenta: activar, elegir cuántos minutos antes, probar o apagar. */
export function DialogoAvisos({ avisos: a, onCerrar }: { avisos: ReturnType<typeof useAvisos>; onCerrar: () => void }) {
  return (
    <Modal onCerrar={onCerrar}>
      <h2>Avisos 🔔</h2>
      {a.estado === 'activo' || a.estado === 'apagado' ? (
        <>
          <p className="suave" style={{ margin: 0 }}>
            Te aviso antes de cada clase y cuando una entrega vence en menos de un día, aunque tengas la web cerrada.
          </p>
          <div>
            <label className="etiqueta-campo">Avisarme antes de clase</label>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {MINUTOS.map((m) => (
                <button key={m} className="pastilla chica" disabled={a.trabajando}
                  onClick={() => (a.estado === 'activo' ? a.cambiarMinutos(m) : a.activar(m))}
                  style={a.estado === 'activo' && a.minutos === m ? { background: 'var(--acento-suave)', color: 'var(--acento)', borderColor: 'var(--acento)' } : undefined}>
                  {m} min
                </button>
              ))}
            </div>
          </div>
        </>
      ) : (
        <p className="suave" style={{ margin: 0 }}>
          {a.estado === 'iphone-instalar'
            ? 'En iPhone los avisos solo funcionan con MiCarrera en tu pantalla de inicio: abre esta página en Safari, toca Compartir → Agregar a inicio, y entra desde el ícono nuevo.'
            : a.estado === 'bloqueado'
              ? 'Bloqueaste las notificaciones de este sitio. Toca el candado junto a la dirección (o la configuración del navegador) → Notificaciones → Permitir, y vuelve aquí.'
              : 'Este navegador no permite avisos. Prueba con Chrome, Edge, Firefox o Safari.'}
        </p>
      )}
      {a.mensaje && <div className="aviso">{a.mensaje}</div>}
      <div className="acciones">
        {a.estado === 'activo' && (
          <button className="boton-texto" style={{ color: 'var(--rojo)' }} disabled={a.trabajando} onClick={a.apagar}>Apagar</button>
        )}
        <span style={{ flex: 1 }} />
        {a.estado === 'activo' && <button className="boton-texto" disabled={a.trabajando} onClick={a.probar}>Probar</button>}
        {a.estado === 'apagado'
          ? <button className="boton" disabled={a.trabajando} onClick={() => a.activar(15)}>{a.trabajando ? 'Activando…' : 'Activar (15 min)'}</button>
          : <button className="boton" onClick={onCerrar}>Listo</button>}
      </div>
    </Modal>
  )
}
