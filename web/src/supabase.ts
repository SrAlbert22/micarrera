import { createClient } from '@supabase/supabase-js'

// Si llega desde el enlace de una invitación (o de "restablecer contraseña"), al entrar le pedimos crear
// su contraseña. Se revisa antes de crear el cliente, porque el cliente borra el enlace de la barra de direcciones.
try {
  if (/type=(invite|recovery)/.test(window.location.hash)) localStorage.setItem('pedirContrasena', '1')
} catch { /* sin almacenamiento */ }

// El mismo proyecto que la app de Android. La clave "publishable" es pública por diseño:
// lo que protege los datos son las reglas RLS de cada tabla.
export const supabase = createClient(
  'https://ivtgsjnjpkcufjkrbvft.supabase.co',
  'sb_publishable_QRa8SoW9t5e46BYrXUeLag_qjtOqNSx',
)

/** Pon true cuando Google esté activado en Supabase (Authentication → Providers → Google). */
export const GOOGLE_ACTIVO = false
