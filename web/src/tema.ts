// Apariencia: automático (como el sistema), oscuro o claro. Se guarda en este navegador; public/tema.js la aplica.

export type Tema = 'sistema' | 'oscuro' | 'claro'

export const OPCIONES_TEMA = [
  ['sistema', 'Automático', 'Igual que tu teléfono o computadora'],
  ['oscuro', 'Oscuro', 'Fondo azul noche, ideal de noche'],
  ['claro', 'Claro', 'Fondo blanco, se lee mejor al sol'],
] as const

declare global { interface Window { aplicarTema?: () => void } }

export function leerTema(): Tema {
  try { return (localStorage.getItem('tema') as Tema | null) ?? 'sistema' } catch { return 'sistema' }
}

export function guardarTema(tema: Tema) {
  try { localStorage.setItem('tema', tema) } catch { /* sin almacenamiento: solo dura esta visita */ }
  window.aplicarTema?.()
}
