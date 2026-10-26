// Invitar amigos: cada quien comparte su enlace (…/?invita=usuario). Quien lo abre y se registra
// recibe la sugerencia de agregar como amigo a quien lo invitó.
import { useEffect, useState } from 'react'
import * as repo from './repo'
import { FORMATO_USUARIO, nombreVisible, type Persona } from './amigos'
import { Modal } from './comunes'
import { Avatar } from './pantallas/Amigos'

const WEB = 'https://micarrera-beta.vercel.app'
const CLAVE = 'invitadoPor'

/** Guarda quién invitó (si el enlace trae ?invita=usuario) y limpia la dirección. Se llama una vez al abrir. */
export function leerInvitacion() {
  try {
    const url = new URL(window.location.href)
    const u = url.searchParams.get('invita')?.trim().toLowerCase()
    if (u && FORMATO_USUARIO.test(u)) localStorage.setItem(CLAVE, u)
    if (url.searchParams.has('invita')) {
      url.searchParams.delete('invita')
      history.replaceState(null, '', url.pathname + url.search + url.hash)
    }
  } catch { /* sin almacenamiento */ }
}

export function quienInvito(): string | null {
  try { return localStorage.getItem(CLAVE) } catch { return null }
}
function olvidarInvitacion() {
  try { localStorage.removeItem(CLAVE) } catch { /* sin almacenamiento */ }
}

export function mensajeInvitacion(usuario: string | null) {
  const enlace = usuario ? `${WEB}/?invita=${usuario}` : WEB
  return {
    titulo: 'MiCarrera',
    texto: '🎓 Te invito a MiCarrera: tu pensum, tu horario y tus notas en orden. Así vemos qué clases tenemos juntos 👥',
    url: enlace,
  }
}

/** Botón "Invitar amigos": usa el menú de compartir del teléfono; en computadora copia el mensaje. */
export async function compartirInvitacion(usuario: string | null): Promise<'compartido' | 'copiado' | 'cancelado'> {
  const m = mensajeInvitacion(usuario)
  if (navigator.share) {
    try { await navigator.share({ title: m.titulo, text: m.texto, url: m.url }); return 'compartido' } catch { return 'cancelado' }
  }
  await navigator.clipboard.writeText(`${m.texto}\n👉 ${m.url}`)
  return 'copiado'
}

/** Después de registrarse con un enlace de invitación: "¿Agregar a X como amigo?" */
export function AvisoInvitacion() {
  const [persona, setPersona] = useState<Persona | null>(null)
  const [enviado, setEnviado] = useState(false)

  useEffect(() => {
    const u = quienInvito()
    if (!u) return
    Promise.all([repo.perfil(), repo.buscarUsuarios(u), repo.misAmigos()]).then(([yo, encontrados, gente]) => {
      const p = encontrados.find((x) => x.usuario === u)
      // No se muestra si es uno mismo, si no existe o si ya son amigos / hay solicitud.
      if (!p || yo?.usuario === u || gente.some((g) => g.id === p.id)) olvidarInvitacion()
      else setPersona(p)
    }).catch(() => {})
  }, [])

  if (!persona) return null
  const cerrar = () => { olvidarInvitacion(); setPersona(null) }

  return (
    <Modal onCerrar={cerrar}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 14 }}>
        <Avatar p={persona} tam={56} />
        <div>
          <h2>{enviado ? '¡Solicitud enviada! 🎉' : `${nombreVisible(persona)} te invitó 👋`}</h2>
          {persona.usuario && <div className="suave" style={{ fontSize: 14 }}>@{persona.usuario}</div>}
        </div>
      </div>
      <p className="suave" style={{ margin: 0 }}>
        {enviado ? 'Cuando acepte, verán las clases que tienen en común.'
          : '¿Quieres agregarle como amigo? Así ven qué clases tienen juntos y cuándo están libres.'}
      </p>
      <div className="acciones">
        {enviado ? <button className="boton" onClick={cerrar}>Listo</button> : (
          <>
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={cerrar}>Ahora no</button>
            <button className="boton" onClick={async () => {
              try { await repo.enviarSolicitud(persona.id); setEnviado(true) } catch { cerrar() }
              olvidarInvitacion()
            }}>Agregar amigo</button>
          </>
        )}
      </div>
    </Modal>
  )
}
