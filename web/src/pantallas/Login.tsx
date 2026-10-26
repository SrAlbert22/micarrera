import { useState } from 'react'
import { GOOGLE_ACTIVO, supabase } from '../supabase'
import { Icono } from '../comunes'
import { MIN_CONTRASENA, marcarPedirContrasena } from '../contrasena'
import { quienInvito } from '../invitar'
import TestVocacional, { abiertoConEnlace } from './TestVocacional'
import { correccionCorreo } from '../util'
import { revisarContrasena } from '../contrasenas'

/**
 * Pasos del inicio de sesión (igual que en Android):
 *  - entrar: correo + contraseña (lo normal; no llega ningún correo).
 *  - crear: correo + contraseña nueva → llega un código UNA vez para confirmar el correo (confirmar).
 *  - codigo: entrar con un código al correo (olvidé mi contraseña, o cuentas viejas sin contraseña).
 */
type Paso = 'entrar' | 'crear' | 'confirmar' | 'codigo' | 'codigo-enviado'

function mensajeDeError(mensaje: string, paso: Paso): string {
  const m = mensaje.toLowerCase()
  if (m.includes('invalid login credentials'))
    return 'Correo o contraseña incorrectos. Si antes entrabas con un código, usa "Entrar con un código" y crea tu contraseña.'
  if (m.includes('signups not allowed') || m.includes('otp_disabled') || m.includes('user not found'))
    return 'No hay ninguna cuenta con ese correo. Revisa que esté bien escrito o toca "Crear cuenta".'
  if (m.includes('email not confirmed')) return 'Todavía no confirmas tu correo. Te enviamos el código otra vez.'
  if (m.includes('already registered') || m.includes('already been registered')) return 'Ya existe una cuenta con ese correo. Toca "Entrar".'
  if (m.includes('password') && (m.includes('weak') || m.includes('short') || m.includes('at least')))
    return `La contraseña es muy débil. Usa al menos ${MIN_CONTRASENA} caracteres.`
  if (m.includes('rate limit') || m.includes('security purposes')) return 'Pediste muchos códigos seguidos. Espera un minuto e intenta otra vez.'
  if (paso === 'confirmar' || paso === 'codigo-enviado') return 'Código incorrecto o vencido. Intenta de nuevo.'
  return 'No se pudo completar. Revisa tu internet e intenta otra vez.'
}

export default function Login() {
  // Recuerda el último correo con el que entraste (solo en este navegador).
  const [correo, setCorreo] = useState(() => { try { return localStorage.getItem('ultimoCorreo') ?? '' } catch { return '' } })
  const invito = quienInvito()
  const [paso, setPaso] = useState<Paso>(invito ? 'crear' : 'entrar')
  const [contrasena, setContrasena] = useState('')
  const [confirmar, setConfirmar] = useState('')
  const [codigo, setCodigo] = useState('')
  const [ver, setVer] = useState(false)
  const [cargando, setCargando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [aviso, setAviso] = useState<string | null>(null)
  const [test, setTest] = useState(abiertoConEnlace)

  const ir = (p: Paso) => { setPaso(p); setError(null); setAviso(null); setCodigo('') }
  const recordar = () => { try { localStorage.setItem('ultimoCorreo', correo) } catch { /* sin almacenamiento */ } }

  async function continuar() {
    setCargando(true); setError(null); setAviso(null)
    let fallo: { message: string } | null = null
    if (paso === 'entrar') {
      fallo = (await supabase.auth.signInWithPassword({ email: correo, password: contrasena })).error
      if (fallo?.message.toLowerCase().includes('email not confirmed')) {
        await supabase.auth.resend({ type: 'signup', email: correo })
        setPaso('confirmar')
      }
    } else if (paso === 'crear') {
      const problema = await revisarContrasena(contrasena, correo)
      if (problema) { setError(problema); setCargando(false); return }
      const r = await supabase.auth.signUp({ email: correo, password: contrasena })
      fallo = r.error
      if (!fallo && !r.data.session) { setPaso('confirmar'); setCodigo('') } // hay que confirmar el correo con el código
    } else if (paso === 'confirmar') {
      fallo = (await supabase.auth.verifyOtp({ email: correo, token: codigo, type: 'signup' })).error
    } else if (paso === 'codigo') {
      fallo = (await supabase.auth.signInWithOtp({ email: correo, options: { shouldCreateUser: false } })).error
      if (!fallo) setPaso('codigo-enviado')
    } else {
      marcarPedirContrasena(true) // entró con código: que cree su contraseña
      fallo = (await supabase.auth.verifyOtp({ email: correo, token: codigo, type: 'email' })).error
      if (fallo) marcarPedirContrasena(false)
    }
    if (fallo) setError(mensajeDeError(fallo.message, paso))
    else recordar()
    setCargando(false)
  }

  async function reenviar() {
    const { error } = await supabase.auth.resend({ type: 'signup', email: correo })
    if (error) setError(mensajeDeError(error.message, paso))
    else setAviso('Te enviamos otro código.')
  }

  async function conGoogle() {
    setCargando(true); setError(null)
    const { error } = await supabase.auth.signInWithOAuth({ provider: 'google', options: { redirectTo: window.location.origin } })
    if (error) { setError('No se pudo entrar con Google. Intenta otra vez o usa tu correo.'); setCargando(false) }
  }

  const correoOk = correo.includes('@') && correo.includes('.')
  const listo = !cargando && ({
    entrar: correoOk && contrasena.length > 0,
    crear: correoOk && contrasena.length >= MIN_CONTRASENA && contrasena === confirmar,
    codigo: correoOk,
    confirmar: codigo.length >= 6,
    'codigo-enviado': codigo.length >= 6,
  })[paso]

  const [titulo, subtitulo] = ({
    entrar: ['Entra a tu cuenta', 'Con tu correo y tu contraseña.'],
    crear: ['Crea tu cuenta', 'Te enviaremos un código una sola vez para confirmar tu correo.'],
    confirmar: ['Confirma tu correo 📩', `Escribe el código que te enviamos a ${correo}. Si no llega en un minuto, revisa Spam o Promociones.`],
    codigo: ['Entrar con un código', 'Te enviamos un código a tu correo. Luego podrás crear tu contraseña.'],
    'codigo-enviado': ['Revisa tu correo 📩', `Escribe el código que te enviamos a ${correo}. Si no llega en un minuto, revisa Spam o Promociones.`],
  } as const)[paso]

  const campoContrasena = (valor: string, cambiar: (v: string) => void, placeholder: string, auto: string) => (
    <div style={{ position: 'relative' }}>
      <span style={{ position: 'absolute', left: 14, top: 15, color: 'var(--acento)' }}><Icono n="candado" /></span>
      <input className="campo" style={{ paddingLeft: 44, paddingRight: 80 }} type={ver ? 'text' : 'password'} autoComplete={auto}
        placeholder={placeholder} value={valor} onChange={(e) => cambiar(e.target.value)} />
      <button type="button" className="boton-texto" onClick={() => setVer(!ver)}
        style={{ position: 'absolute', right: 6, top: 6, fontSize: 13 }}>{ver ? 'Ocultar' : 'Ver'}</button>
    </div>
  )

  if (test) return <TestVocacional onCerrar={() => setTest(false)} onCrearCuenta={() => { setTest(false); ir('crear') }} />

  return (
    <div style={{ minHeight: '100dvh', background: 'var(--degradado)', padding: '48px 24px 24px' }}>
      <div style={{ maxWidth: 440, margin: '0 auto', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
        <img src="/pwa-192.png" alt="MiCarrera" width={96} height={96} style={{ borderRadius: 28 }} />
        <h1 style={{ color: '#fff', fontSize: 34, fontWeight: 800, margin: '16px 0 0' }}>MiCarrera</h1>
        <p style={{ color: 'rgba(255,255,255,.8)', margin: '4px 0 20px' }}>Tu pensum y tu horario, en orden</p>

        {/* Test vocacional: arriba y a la vista, se hace sin cuenta */}
        <button type="button" onClick={() => setTest(true)} className="boton-test">
          <span style={{ fontSize: 28 }}>🧭</span>
          <span style={{ flex: 1, textAlign: 'left' }}>
            <strong style={{ display: 'block', fontSize: 16 }}>¿Qué debo estudiar?</strong>
            <span style={{ fontSize: 13, opacity: .85 }}>Haz el test gratis, sin crear cuenta</span>
          </span>
          <Icono n="derecha" />
        </button>
        {invito && (
          <div style={{ background: 'rgba(255,255,255,.14)', color: '#fff', borderRadius: 16, padding: '10px 16px', marginBottom: 16, fontSize: 15 }}>
            👋 <strong>@{invito}</strong> te invitó a MiCarrera
          </div>
        )}

        <form className="tarjeta" style={{ width: '100%', padding: 24, display: 'flex', flexDirection: 'column', gap: 12 }}
          onSubmit={(e) => { e.preventDefault(); if (listo) continuar() }}>
          {(paso === 'entrar' || paso === 'crear') && (
            <div style={{ display: 'flex', background: 'var(--tarjeta-alta)', borderRadius: 14, padding: 4 }}>
              {([['entrar', 'Entrar'], ['crear', 'Crear cuenta']] as const).map(([p, t]) => (
                <button key={p} type="button" onClick={() => ir(p)}
                  style={{ flex: 1, border: 0, borderRadius: 10, padding: '10px 0', fontWeight: 600,
                    background: paso === p ? 'var(--tarjeta)' : 'transparent', color: paso === p ? 'var(--texto)' : 'var(--texto-suave)' }}>
                  {t}
                </button>
              ))}
            </div>
          )}

          <div>
            <h2 style={{ margin: 0, fontSize: 22 }}>{titulo}</h2>
            <p className="suave" style={{ margin: '6px 0 0', fontSize: 14 }}>{subtitulo}</p>
          </div>

          {paso === 'entrar' && GOOGLE_ACTIVO && (
            <button type="button" onClick={conGoogle} disabled={cargando} className="boton ancho"
              style={{ background: 'var(--tarjeta)', color: 'var(--texto)', border: '1px solid var(--borde)' }}>
              <strong style={{ color: '#4285F4', fontSize: 20 }}>G</strong> Continuar con Google
            </button>
          )}

          {(paso === 'entrar' || paso === 'crear' || paso === 'codigo') && (
            <div style={{ position: 'relative' }}>
              <span style={{ position: 'absolute', left: 14, top: 15, color: 'var(--acento)' }}><Icono n="correo" /></span>
              <input className="campo" style={{ paddingLeft: 44 }} type="email" autoComplete="email" inputMode="email"
                placeholder="tucorreo@gmail.com" value={correo} onChange={(e) => setCorreo(e.target.value.trim())} />
            </div>
          )}
          {(paso === 'entrar' || paso === 'crear' || paso === 'codigo') && correccionCorreo(correo) && (
            <button type="button" className="boton-texto" onClick={() => setCorreo(correccionCorreo(correo)!)}
              style={{ padding: 0, textAlign: 'left', color: 'var(--ambar)', fontSize: 14 }}>
              ⚠️ ¿Quisiste decir <strong>{correccionCorreo(correo)}</strong>? Toca para corregir
            </button>
          )}
          {paso === 'entrar' && campoContrasena(contrasena, setContrasena, 'Contraseña', 'current-password')}
          {paso === 'crear' && campoContrasena(contrasena, setContrasena, `Contraseña (mín. ${MIN_CONTRASENA})`, 'new-password')}
          {paso === 'crear' && campoContrasena(confirmar, setConfirmar, 'Repite la contraseña', 'new-password')}
          {paso === 'crear' && confirmar && contrasena !== confirmar && (
            <div style={{ color: 'var(--rojo)', fontSize: 13 }}>Las contraseñas no coinciden.</div>
          )}

          {(paso === 'confirmar' || paso === 'codigo-enviado') && (
            <input className="campo mono" inputMode="numeric" autoComplete="one-time-code" placeholder="000000"
              style={{ textAlign: 'center', fontSize: 26, letterSpacing: 8, fontWeight: 700 }}
              value={codigo} onChange={(e) => setCodigo(e.target.value.replace(/\D/g, '').slice(0, 8))} autoFocus />
          )}

          {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
          {aviso && <div style={{ color: 'var(--verde)', fontSize: 13 }}>{aviso}</div>}

          <button className="boton ancho" disabled={!listo}>
            {cargando ? <div className="girando" style={{ width: 22, height: 22 }} />
              : ({ entrar: 'Entrar', crear: 'Crear cuenta', codigo: 'Enviarme el código', confirmar: 'Confirmar', 'codigo-enviado': 'Confirmar' })[paso]}
          </button>

          {paso === 'entrar' && (
            <button type="button" className="boton-texto" onClick={() => ir('codigo')}>¿Olvidaste tu contraseña? Entra con un código</button>
          )}
          {paso === 'confirmar' && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <button type="button" className="boton-texto" onClick={reenviar}>Reenviar código</button>
              <button type="button" className="boton-texto" onClick={() => ir('crear')}>Cambiar correo</button>
            </div>
          )}
          {(paso === 'codigo' || paso === 'codigo-enviado') && (
            <button type="button" className="boton-texto" onClick={() => ir('entrar')}>Volver a entrar con contraseña</button>
          )}
        </form>

        <p style={{ color: 'rgba(255,255,255,.7)', fontSize: 13, marginTop: 18, textAlign: 'center', lineHeight: 1.5 }}>
          Al usar MiCarrera aceptas los <a href="/terminos.html" target="_blank" rel="noopener" style={{ color: '#fff' }}>Términos y condiciones</a>
          {' '}y la <a href="/privacidad.html" target="_blank" rel="noopener" style={{ color: '#fff' }}>Política de privacidad</a>.
        </p>
        <p style={{ color: 'rgba(255,255,255,.6)', fontSize: 13, marginTop: 4 }}>Creado por Albert Silvestre</p>
      </div>
    </div>
  )
}
