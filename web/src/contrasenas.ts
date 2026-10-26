// Contraseñas seguras (lo mismo que Contrasenas.kt en Android).
//  - Reglas locales: mínimo 8, no solo números, no repetir un carácter, no las más comunes, no tu correo.
//  - Filtradas: Have I Been Pwned con "k-anonimato": solo sale del navegador el inicio (5 caracteres) del SHA-1,
//    nunca la contraseña. Es lo que Supabase cobra en el plan Pro.

export const MINIMO = 8

const COMUNES = new Set([
  '12345678', '123456789', '1234567890', '87654321', '11111111', '00000000', '12341234', '11223344',
  'password', 'password1', 'password123', 'contraseña', 'contrasena', 'contraseña1', 'contrasena1',
  'qwerty123', 'qwertyui', 'asdfghjk', 'abcd1234', 'abc12345', 'iloveyou', 'teamo123', 'micarrera',
  'micarrera1', 'micarrera123', 'itla2026', 'itla1234', 'uasd1234', 'dominicana', 'republica',
  '12345678a', 'a12345678', '1q2w3e4r', 'zaq12wsx', 'admin123', 'welcome1', 'estudiante', 'universidad',
])

/** Problema de la contraseña (sin internet), o null si pasa las reglas. */
export function problemaLocal(contrasena: string, correo?: string | null): string | null {
  const c = contrasena.toLowerCase()
  const usuario = (correo ?? '').split('@')[0].toLowerCase()
  if ([...contrasena].length < MINIMO) return `Usa al menos ${MINIMO} caracteres.`
  if (/^\d+$/.test(contrasena)) return 'No uses solo números: mezcla letras y números.'
  if (new Set(contrasena).size <= 2) return 'No repitas el mismo carácter.'
  if (COMUNES.has(c) || COMUNES.has(c.replace(/[^\p{L}]+$/u, ''))) return 'Esa contraseña es de las más usadas. Elige otra.'
  if (usuario.length >= 4 && c.includes(usuario)) return 'No uses tu correo dentro de la contraseña.'
  if (c.includes('micarrera')) return 'No uses el nombre de la app en tu contraseña.'
  return null
}

async function sha1(texto: string): Promise<string> {
  const h = await crypto.subtle.digest('SHA-1', new TextEncoder().encode(texto))
  return [...new Uint8Array(h)].map((b) => b.toString(16).padStart(2, '0')).join('').toUpperCase()
}

/** Veces que salió en filtraciones, o null si no se pudo revisar. */
export async function vecesFiltrada(contrasena: string): Promise<number | null> {
  try {
    const hash = await sha1(contrasena)
    const control = new AbortController(); const t = setTimeout(() => control.abort(), 5000)
    const r = await fetch(`https://api.pwnedpasswords.com/range/${hash.slice(0, 5)}`, {
      headers: { 'Add-Padding': 'true' }, signal: control.signal, referrerPolicy: 'no-referrer', credentials: 'omit',
    })
    clearTimeout(t)
    if (!r.ok) return null
    const sufijo = hash.slice(5)
    for (const linea of (await r.text()).split('\n')) {
      const [s, n] = linea.trim().split(':')
      if (s === sufijo) return Number(n) || 0
    }
    return 0
  } catch { return null }
}

/** Todas las revisiones: el problema a mostrar, o null si es buena (sin internet no bloquea). */
export async function revisarContrasena(contrasena: string, correo?: string | null): Promise<string | null> {
  const local = problemaLocal(contrasena, correo)
  if (local) return local
  const veces = await vecesFiltrada(contrasena)
  return veces ? 'Esa contraseña apareció en filtraciones de datos de otras páginas. Elige otra que no uses en ningún lado.' : null
}
