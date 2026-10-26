import { useEffect, useState } from 'react'
import * as repo from '../repo'
import { FORMATO_USUARIO, limpiarUsuario, sugerirUsuario } from '../amigos'
import { Icono } from '../comunes'

/**
 * Primera vez que entras: tu nombre (y un @usuario sugerido). Sin esto tus amigos no te encuentran.
 * Si ya tienes @usuario, se queda el tuyo.
 */
export default function CompletarPerfil({ usuarioActual, onListo }: { usuarioActual: string | null; onListo: () => void }) {
  const [nombre, setNombre] = useState('')
  const [usuario, setUsuario] = useState(usuarioActual ?? '')
  const [tocado, setTocado] = useState(!!usuarioActual) // si lo edita a mano, ya no lo cambiamos al escribir el nombre
  const [revisado, setRevisado] = useState<{ u: string; ok: boolean } | null>(null) // disponibilidad de cuál @usuario
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const limpio = limpiarUsuario(usuario)
  const formatoOk = FORMATO_USUARIO.test(limpio)
  const nuevoUsuario = !usuarioActual || limpio !== usuarioActual
  const disponible = revisado?.u === limpio ? revisado.ok : null

  useEffect(() => { if (!tocado) setUsuario(sugerirUsuario(nombre)) }, [nombre, tocado])
  useEffect(() => {
    if (!formatoOk || !nuevoUsuario) return
    const t = setTimeout(() => repo.usuarioDisponible(limpio).then((ok) => setRevisado({ u: limpio, ok })).catch(() => {}), 400)
    return () => clearTimeout(t)
  }, [limpio, formatoOk, nuevoUsuario])
  // Si el sugerido ya lo tiene otra persona, probamos con un número al final
  useEffect(() => {
    if (disponible === false && !tocado && formatoOk) setUsuario(limpio.slice(0, 17) + Math.floor(10 + Math.random() * 89))
  }, [disponible, tocado, formatoOk, limpio])

  const listo = nombre.trim().length >= 2 && (!usuario || !nuevoUsuario || (formatoOk && disponible === true))

  async function guardar() {
    setGuardando(true); setError(null)
    try {
      await repo.guardarNombre(nombre)
      if (usuario && nuevoUsuario) await repo.guardarUsuario(limpio)
      onListo()
    } catch {
      setError('No se pudo guardar. Revisa tu internet e intenta otra vez.')
      setGuardando(false)
    }
  }

  const aviso = (color: string, texto: string) => <div style={{ color, fontSize: 13, marginTop: 6 }}>{texto}</div>

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--degradado)', padding: '56px 20px 24px' }}>
      <div style={{ maxWidth: 440, margin: '0 auto' }}>
        <div style={{ fontSize: 56, textAlign: 'center' }}>👋</div>
        <h1 style={{ color: '#fff', textAlign: 'center', fontSize: 30, margin: '8px 0 6px' }}>¡Bienvenido a MiCarrera!</h1>
        <p style={{ color: 'rgba(255,255,255,.85)', textAlign: 'center', margin: '0 0 24px' }}>¿Cómo te llamas? Así tus amigos te encuentran.</p>

        <form className="tarjeta" style={{ padding: 22, display: 'flex', flexDirection: 'column', gap: 12 }}
          onSubmit={(e) => { e.preventDefault(); if (listo && !guardando) guardar() }}>
          <div>
            <label className="etiqueta-campo">Tu nombre</label>
            <input className="campo" value={nombre} autoFocus maxLength={60} autoComplete="name"
              placeholder="Ej. María Rodríguez" onChange={(e) => setNombre(e.target.value)} />
          </div>
          <div>
            <label className="etiqueta-campo">Tu @usuario</label>
            <div style={{ position: 'relative' }}>
              <span className="suave" style={{ position: 'absolute', left: 16, top: 14 }}>@</span>
              <input className="campo" style={{ paddingLeft: 32 }} value={usuario} autoCapitalize="none" autoCorrect="off" maxLength={20}
                placeholder="maria.rodriguez" onChange={(e) => { setTocado(true); setUsuario(e.target.value.toLowerCase().replace(/\s/g, '')) }} />
            </div>
            {usuario && nuevoUsuario && (
              !formatoOk ? aviso('var(--rojo)', 'Entre 3 y 20 caracteres: a-z, 0-9, punto o guion bajo.')
                : disponible === true ? aviso('var(--verde)', `✓ @${limpio} está disponible`)
                : disponible === false ? aviso('var(--rojo)', `@${limpio} ya lo tiene otra persona`)
                : aviso('var(--texto-suave)', 'Revisando…')
            )}
          </div>
          <div className="suave" style={{ fontSize: 13, display: 'flex', gap: 8, alignItems: 'flex-start' }}>
            <Icono n="candado" tam={16} /><span>Tus notas e índice nunca se comparten. Puedes cambiar quién te encuentra en Cuenta.</span>
          </div>
          {error && aviso('var(--rojo)', error)}
          <button className="boton ancho" disabled={!listo || guardando}>
            {guardando ? <div className="girando" style={{ width: 22, height: 22 }} /> : 'Continuar'}
          </button>
        </form>
        <button className="boton-texto" style={{ color: 'rgba(255,255,255,.7)', width: '100%', marginTop: 10 }} onClick={onListo}>Ahora no</button>
      </div>
    </div>
  )
}
