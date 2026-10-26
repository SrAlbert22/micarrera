import { useCallback, useEffect, useState } from 'react'
import { supabase } from './supabase'

// Avisos en el navegador (Web Push): antes de cada clase, entregas que vencen pronto, amigos…
// Este navegador queda guardado en "dispositivos" con plataforma 'web'; el servidor (funciones recordar-clases,
// enviar-notificacion y sincronizar-aula) le escribe aunque la web esté cerrada. Lo muestra public/avisos-sw.js.
// En Android, la app hace lo mismo con sus propias alarmas (Alertas.kt). La tarjeta y el diálogo están en avisosWeb.tsx.

export type EstadoAvisos = 'cargando' | 'no-soportado' | 'iphone-instalar' | 'bloqueado' | 'apagado' | 'activo'
export const MINUTOS = [5, 10, 15, 30] as const

const esIphone = /iphone|ipad|ipod/i.test(navigator.userAgent)
const instalada = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true

function soporte(): EstadoAvisos | null {
  if (esIphone && !instalada()) return 'iphone-instalar' // en iPhone solo funcionan con la web agregada a inicio
  if (!('serviceWorker' in navigator) || !('PushManager' in window) || !('Notification' in window)) return 'no-soportado'
  if (Notification.permission === 'denied') return 'bloqueado'
  return null
}

/** El service worker de la PWA (en `npm run dev` no hay, por eso el límite de tiempo). */
async function registro(): Promise<ServiceWorkerRegistration | null> {
  return Promise.race([navigator.serviceWorker.ready, new Promise<null>((r) => setTimeout(() => r(null), 6000))])
}

/** Lo que se guarda como "token": la suscripción del navegador (dirección + llaves para cifrar). */
function tokenDe(s: PushSubscription): string {
  const j = s.toJSON()
  return JSON.stringify({ endpoint: j.endpoint, keys: { p256dh: j.keys?.p256dh, auth: j.keys?.auth } })
}

function bytes(b64url: string): Uint8Array<ArrayBuffer> {
  const b = atob(b64url.replace(/-/g, '+').replace(/_/g, '/') + '==='.slice((b64url.length + 3) % 4))
  return Uint8Array.from(b, (c) => c.charCodeAt(0))
}

async function suscripcion() {
  const reg = await registro()
  return reg ? reg.pushManager.getSubscription() : null
}

async function leerEstado(): Promise<{ estado: EstadoAvisos; minutos: number }> {
  const falta = soporte()
  if (falta) return { estado: falta, minutos: 15 }
  const s = await suscripcion()
  if (!s || Notification.permission !== 'granted') return { estado: 'apagado', minutos: 15 }
  const { data } = await supabase.from('dispositivos').select('minutos_clase').eq('token', tokenDe(s)).maybeSingle()
  return data ? { estado: 'activo', minutos: data.minutos_clase ?? 15 } : { estado: 'apagado', minutos: 15 }
}

/** Pide permiso, suscribe este navegador y lo guarda en Supabase. Debe llamarse al tocar un botón. */
async function activar(minutos: number): Promise<EstadoAvisos> {
  const permiso = await Notification.requestPermission() // primero, mientras dura el toque (Safari lo exige)
  if (permiso !== 'granted') return permiso === 'denied' ? 'bloqueado' : 'apagado'
  const reg = await registro()
  if (!reg) throw new Error('Sin service worker')
  const { data, error } = await supabase.functions.invoke('recordar-clases', { body: { llave: true } })
  if (error || !data?.llave) throw new Error('Sin llave')
  const opciones = { userVisibleOnly: true, applicationServerKey: bytes(data.llave) }
  let s: PushSubscription
  try {
    s = await reg.pushManager.subscribe(opciones)
  } catch {
    // Suscripción vieja con otra llave: se borra y se crea de nuevo
    await (await reg.pushManager.getSubscription())?.unsubscribe()
    s = await reg.pushManager.subscribe(opciones)
  }
  const { data: sesion } = await supabase.auth.getSession()
  const { error: e2 } = await supabase.from('dispositivos').upsert({
    usuario_id: sesion.session?.user.id, token: tokenDe(s), plataforma: 'web', minutos_clase: minutos, actualizado: new Date().toISOString(),
  }, { onConflict: 'usuario_id,token' })
  if (e2) throw e2
  return 'activo'
}

/** Al cerrar sesión: este navegador deja de recibir avisos de esa cuenta. */
export async function olvidarNavegador() {
  try {
    if (soporte()) return
    const s = await Promise.race([suscripcion(), new Promise<null>((r) => setTimeout(() => r(null), 2000))])
    if (!s) return
    await supabase.from('dispositivos').delete().eq('token', tokenDe(s))
    await s.unsubscribe()
  } catch { /* que nunca impida salir */ }
}

/** Estado y acciones de los avisos de este navegador (lo usan Horario y Cuenta). */
export function useAvisos() {
  const [estado, setEstado] = useState<EstadoAvisos>('cargando')
  const [minutos, setMinutos] = useState(15)
  const [trabajando, setTrabajando] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [recienActivado, setRecienActivado] = useState(false)

  useEffect(() => {
    leerEstado().then((r) => { setEstado(r.estado); setMinutos(r.minutos) }).catch(() => setEstado('apagado'))
  }, [])

  const trabajar = useCallback(async (fn: () => Promise<void>) => {
    setTrabajando(true); setMensaje(null)
    try { await fn() } catch { setMensaje('No se pudo. Revisa tu internet e intenta otra vez.') }
    setTrabajando(false)
  }, [])

  const probar = () => trabajar(async () => {
    const { data, error } = await supabase.functions.invoke('recordar-clases', { body: { prueba: true } })
    if (error || !data?.enviados) throw new Error('No llegó')
    setMensaje('Te mandé un aviso de prueba. Debe llegarte en unos segundos.')
  })

  return {
    estado, minutos, trabajando, mensaje, recienActivado,
    activar: (m = minutos) => trabajar(async () => {
      const nuevo = await activar(m)
      setEstado(nuevo); setMinutos(m)
      if (nuevo === 'bloqueado') setMensaje('Bloqueaste los avisos. Para activarlos, permite las notificaciones de este sitio en la configuración del navegador.')
      if (nuevo === 'activo') {
        setRecienActivado(true)
        const { data } = await supabase.functions.invoke('recordar-clases', { body: { prueba: true } })
        setMensaje(data?.enviados ? '¡Listo! Te mandé un aviso de prueba.' : '¡Listo! Ya tienes los avisos activados.')
      }
    }),
    cambiarMinutos: (m: number) => trabajar(async () => {
      const s = await suscripcion()
      if (!s) throw new Error('Sin suscripción')
      const { error } = await supabase.from('dispositivos').update({ minutos_clase: m }).eq('token', tokenDe(s))
      if (error) throw error
      setMinutos(m)
    }),
    apagar: () => trabajar(async () => { await olvidarNavegador(); setEstado('apagado') }),
    probar,
  }
}

export function textoAvisos(estado: EstadoAvisos, minutos: number) {
  return estado === 'activo' ? `${minutos} min antes de cada clase y entregas que vencen`
    : estado === 'iphone-instalar' ? 'En iPhone, primero agrega MiCarrera a tu inicio'
    : estado === 'bloqueado' ? 'Bloqueados en este navegador'
    : estado === 'no-soportado' ? 'Este navegador no permite avisos'
    : estado === 'cargando' ? '…' : 'Apagados: toca para activarlos'
}
