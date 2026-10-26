import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'
import { leerInvitacion } from './invitar'

leerInvitacion() // ¿llegó con un enlace ?invita=usuario?

// Cuando publicamos una versión nueva, su service worker toma el control: recargamos una vez para usarla
// ya, en vez de seguir con la vieja guardada en el navegador hasta la próxima vez que se abra.
if ('serviceWorker' in navigator && navigator.serviceWorker.controller) {
  let recargada = false
  navigator.serviceWorker.addEventListener('controllerchange', () => {
    if (!recargada) { recargada = true; window.location.reload() }
  })
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
