// Avisos (Web Push): este código corre dentro del service worker de la PWA (vite.config.ts → workbox.importScripts),
// así llegan aunque la web esté cerrada. El servidor manda {titulo, texto, tipo, url, tag} cifrado (ver servidor/funciones).

self.addEventListener('push', (evento) => {
  let a = {}
  try { a = evento.data ? evento.data.json() : {} } catch { a = { texto: evento.data ? evento.data.text() : '' } }
  evento.waitUntil(self.registration.showNotification(a.titulo || 'MiCarrera', {
    body: a.texto || '',
    icon: '/pwa-192.png',
    badge: '/favicon.png',
    tag: a.tag || undefined,
    renotify: !!a.tag,
    data: { url: a.url || '/' },
  }))
})

// Al tocar el aviso: si la web ya está abierta, la trae al frente; si no, la abre.
self.addEventListener('notificationclick', (evento) => {
  evento.notification.close()
  const url = new URL(evento.notification.data?.url || '/', self.location.origin).href
  evento.waitUntil((async () => {
    const ventanas = await self.clients.matchAll({ type: 'window', includeUncontrolled: true })
    const abierta = ventanas.find((v) => v.url.startsWith(self.location.origin))
    if (abierta) { await abierta.focus(); return }
    await self.clients.openWindow(url)
  })())
})

// Si el navegador renueva la suscripción por su cuenta, aquí no hay sesión para avisarle a Supabase:
// la web mostrará los avisos como apagados y basta tocar Activar otra vez (Horario o Cuenta).
