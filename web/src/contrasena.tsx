import { useState } from 'react'
import { supabase } from './supabase'
import { Modal } from './comunes'
import { revisarContrasena } from './contrasenas'

export const MIN_CONTRASENA = 8

/** Marca que, al entrar, hay que pedirle al usuario que cree su contraseña (entró con un código). */
export function marcarPedirContrasena(pedir: boolean) {
  try { if (pedir) localStorage.setItem('pedirContrasena', '1'); else localStorage.removeItem('pedirContrasena') } catch { /* sin almacenamiento */ }
}
export function debePedirContrasena(): boolean {
  try { return localStorage.getItem('pedirContrasena') === '1' } catch { return false }
}

/** Crear o cambiar la contraseña (ya con sesión). [primeraVez]: viene de entrar con un código; se puede dejar para después. */
export function DialogoContrasena({ primeraVez, onCerrar }: { primeraVez: boolean; onCerrar: () => void }) {
  const [nueva, setNueva] = useState('')
  const [repetir, setRepetir] = useState('')
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [listo, setListo] = useState(false)
  const valida = nueva.length >= MIN_CONTRASENA && nueva === repetir

  async function guardar() {
    setGuardando(true); setError(null)
    const problema = await revisarContrasena(nueva, (await supabase.auth.getUser()).data.user?.email)
    if (problema) { setError(problema); setGuardando(false); return }
    const { error } = await supabase.auth.updateUser({ password: nueva })
    if (error) setError(error.message.includes('different from the old') ? 'Esa ya es tu contraseña actual.' : 'No se pudo guardar. Intenta otra vez.')
    else setListo(true)
    setGuardando(false)
  }

  return (
    <Modal onCerrar={() => { if (!guardando) onCerrar() }}>
      <h2>{listo ? '¡Contraseña guardada! 🔒' : primeraVez ? 'Crea tu contraseña' : 'Cambiar contraseña'}</h2>
      {listo ? (
        <p className="suave" style={{ margin: 0 }}>La próxima vez entra con tu correo y esta contraseña. Ya no te llegarán códigos.</p>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); if (valida && !guardando) guardar() }} style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          {primeraVez && <p className="suave" style={{ margin: 0, fontSize: 14 }}>Así la próxima vez entras con tu contraseña, sin esperar un código por correo.</p>}
          <input className="campo" type="password" autoComplete="new-password" placeholder={`Contraseña (mín. ${MIN_CONTRASENA})`}
            value={nueva} onChange={(e) => setNueva(e.target.value)} autoFocus />
          <input className="campo" type="password" autoComplete="new-password" placeholder="Repite la contraseña"
            value={repetir} onChange={(e) => setRepetir(e.target.value)} />
          {repetir && nueva !== repetir && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>Las contraseñas no coinciden.</div>}
          {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
          <div className="acciones">
            <button type="button" className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={onCerrar} disabled={guardando}>
              {primeraVez ? 'Después' : 'Cancelar'}
            </button>
            <button className="boton" disabled={!valida || guardando}>{guardando ? 'Guardando…' : 'Guardar'}</button>
          </div>
        </form>
      )}
      {listo && <div className="acciones"><button className="boton" onClick={onCerrar}>Listo</button></div>}
    </Modal>
  )
}
