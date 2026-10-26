export const DIAS = ['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']

/** Día de hoy con lunes = 1 … domingo = 7 (como en la base de datos). */
export const diaDeHoy = (d = new Date()) => ((d.getDay() + 6) % 7) + 1

/** "14:00" o "14:00:00" → minutos desde medianoche. */
export function minutos(h: string): number {
  const [hh, mm] = h.slice(0, 5).split(':').map(Number)
  return (hh || 0) * 60 + (mm || 0)
}

/** "14:00" → "2:00 pm" */
export function hora12(h: string): string {
  const m = minutos(h)
  const hh = Math.floor(m / 60)
  const h12 = hh === 0 ? 12 : hh > 12 ? hh - 12 : hh
  return `${h12}:${String(m % 60).padStart(2, '0')} ${hh < 12 ? 'am' : 'pm'}`
}

export function enCuanto(min: number): string {
  if (min < 60) return `en ${min} min`
  if (min % 60 === 0) return `en ${min / 60} h`
  return `en ${Math.floor(min / 60)} h ${min % 60} min`
}

/** Códigos comparables: "tci-101" y "TCI 101" → "TCI101". */
export const norm = (codigo: string) => codigo.toUpperCase().replace(/[-\s]/g, '')

export const ordinal = (n: number) => `${n}.º`

export function textoNota(nota: number | null): string | null {
  if (nota == null) return null
  return Number.isInteger(nota) ? String(nota) : nota.toFixed(1)
}

/** Reduce la foto (máx. [lado] px) y la pasa a JPEG base64; los PDF van tal cual (máx. 8 MB). Para las lecturas con IA. */
export async function archivoParaIA(archivo: File, lado = 1600): Promise<[string, string]> {
  const aBase64 = (b: Blob) => new Promise<string>((ok, mal) => {
    const r = new FileReader()
    r.onload = () => ok(String(r.result).split(',')[1])
    r.onerror = mal
    r.readAsDataURL(b)
  })
  if (archivo.type === 'application/pdf') {
    if (archivo.size > 8 * 1024 * 1024) throw new Error('8 MB')
    return [await aBase64(archivo), 'application/pdf']
  }
  const img = await createImageBitmap(archivo)
  const escala = Math.min(1, lado / Math.max(img.width, img.height))
  const lienzo = document.createElement('canvas')
  lienzo.width = Math.round(img.width * escala)
  lienzo.height = Math.round(img.height * escala)
  lienzo.getContext('2d')!.drawImage(img, 0, 0, lienzo.width, lienzo.height)
  const jpeg = await new Promise<Blob>((ok) => lienzo.toBlob((b) => ok(b!), 'image/jpeg', 0.85))
  return [await aBase64(jpeg), 'image/jpeg']
}

// Errores comunes al escribir el correo (un código a "gamil.com" nunca llega)
const DOMINIOS: Record<string, string> = {
  'gamil.com': 'gmail.com', 'gmial.com': 'gmail.com', 'gmai.com': 'gmail.com', 'gmal.com': 'gmail.com', 'gnail.com': 'gmail.com',
  'gmaill.com': 'gmail.com', 'gmail.co': 'gmail.com', 'gmail.con': 'gmail.com', 'gmail.cm': 'gmail.com', 'gmail.om': 'gmail.com',
  'gmail.comm': 'gmail.com', 'gimail.com': 'gmail.com', 'hotmial.com': 'hotmail.com', 'hotmal.com': 'hotmail.com',
  'hotmil.com': 'hotmail.com', 'hotmail.co': 'hotmail.com', 'hotmail.con': 'hotmail.com', 'hormail.com': 'hotmail.com',
  'outlok.com': 'outlook.com', 'outllok.com': 'outlook.com', 'outlook.co': 'outlook.com', 'outlook.con': 'outlook.com',
  'yaho.com': 'yahoo.com', 'yahoo.co': 'yahoo.com', 'yahoo.con': 'yahoo.com', 'iclod.com': 'icloud.com', 'icloud.co': 'icloud.com',
}

/** Si el correo parece mal escrito ("ana@gamil.com"), devuelve la versión corregida; si no, null. */
export function correccionCorreo(correo: string): string | null {
  const [usuario, dominio] = correo.trim().toLowerCase().split('@')
  const bueno = dominio ? DOMINIOS[dominio] : undefined
  return usuario && bueno ? `${usuario}@${bueno}` : null
}
