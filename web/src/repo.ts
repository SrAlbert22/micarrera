// Acceso a datos: lo mismo que Repo.kt en Android.
import { supabase } from './supabase'
import type {
  Carrera, CarreraDetalle, CarreraVocacional, Clase, Estado, EstadoAula, Interaccion, Logro, Materia, MateriaLeida, Perfil, Posicion,
  PreguntaVocacional, Progreso, Tarea, Universidad,
} from './tipos'
import { limpiarUsuario, type Persona } from './amigos'
import { olvidarNavegador } from './avisos'

function ok<T>(r: { data: T | null; error: unknown }): T {
  if (r.error) throw r.error
  return r.data as T
}

async function uid(): Promise<string> {
  const { data } = await supabase.auth.getSession()
  const id = data.session?.user.id
  if (!id) throw new Error('No hay sesión iniciada')
  return id
}

export async function correo(): Promise<string> {
  const { data } = await supabase.auth.getSession()
  return data.session?.user.email ?? ''
}

export const universidades = async () =>
  ok<Universidad[]>(await supabase.from('universidades').select().order('nombre'))

export const carreras = async (universidadId: string) =>
  ok<Carrera[]>(await supabase.from('carreras').select().eq('universidad_id', universidadId).order('nombre'))

export const carreraDetalle = async (carreraId: string) =>
  ok<CarreraDetalle | null>(
    await supabase.from('carreras').select('id,nombre,universidades(id,nombre,siglas,logo_url,aula_url)').eq('id', carreraId).maybeSingle(),
  )

export const perfil = async () =>
  ok<Perfil | null>(await supabase.from('perfiles').select().eq('id', await uid()).maybeSingle())

export async function guardarCarrera(carreraId: string | null) {
  ok(await supabase.from('perfiles').upsert({ id: await uid(), carrera_id: carreraId }))
}

export async function guardarNombre(nombre: string) {
  ok(await supabase.from('perfiles').update({ nombre: nombre.trim() }).eq('id', await uid()))
}

export const materias = async (carreraId: string) =>
  ok<Materia[]>(await supabase.from('materias').select().eq('carrera_id', carreraId).order('cuatrimestre'))

export const progreso = async () =>
  ok<Progreso[]>(await supabase.from('progreso').select().eq('usuario_id', await uid()))

/** Cambio manual de estado. No toca la nota que vino del portal. */
export async function marcar(materiaId: string, estado: Estado) {
  ok(await supabase.from('progreso').upsert(
    { usuario_id: await uid(), materia_id: materiaId, estado, origen: 'manual' },
    { onConflict: 'usuario_id,materia_id' },
  ))
}

export const horario = async () =>
  ok<Clase[]>(await supabase.from('horario').select().eq('usuario_id', await uid()).order('hora_inicio'))

export async function agregarClases(clases: Clase[]) {
  if (!clases.length) return
  const yo = await uid()
  ok(await supabase.from('horario').insert(clases.map(({ id: _id, ...c }) => ({ ...c, usuario_id: yo }))))
}

export async function actualizarClase(c: Clase) {
  if (!c.id) return
  const { id, usuario_id: _u, ...campos } = c
  ok(await supabase.from('horario').update({
    aula: null, profesor: null, codigo: null, grupo: null, apunte: null, ...campos,
  }).eq('id', id))
}

export async function borrarClase(id: string) {
  ok(await supabase.from('horario').delete().eq('id', id))
}

/** Agrega las clases que todavía no estén (misma materia, día y hora). Devuelve [agregadas, repetidas]. */
export async function guardarHorarioImportado(clases: Clase[]): Promise<[number, number]> {
  const clave = (c: Clase) => `${c.materia.trim().toLowerCase()}|${c.dia}|${c.hora_inicio.slice(0, 5)}`
  const existentes = new Set((await horario()).map(clave))
  const nuevas = clases.filter((c) => !existentes.has(clave(c)))
  await agregarClases(nuevas)
  return [nuevas.length, clases.length - nuevas.length]
}

/** Manda la foto o el PDF a la Edge Function, que lo lee con IA y devuelve las clases. */
export async function leerHorarioConIA(base64: string, tipo: string): Promise<Clase[]> {
  const { data, error } = await supabase.functions.invoke('leer-horario', { body: { imagen: base64, tipo } })
  if (error) {
    const detalle = await (error as { context?: Response }).context?.text?.().catch(() => '')
    throw new Error(detalle || String(error))
  }
  return (data ?? []) as Clase[]
}

// ---------- Amigos ----------

export const misAmigos = async () => ok<Persona[]>(await supabase.rpc('mis_amigos'))
export const sugerencias = async () => ok<Persona[]>(await supabase.rpc('sugerencias_amigos'))
export const buscarUsuarios = async (texto: string) => ok<Persona[]>(await supabase.rpc('buscar_usuarios', { texto }))
export const usuarioDisponible = async (u: string) => ok<boolean>(await supabase.rpc('usuario_disponible', { u }))

export async function guardarUsuario(usuario: string) {
  ok(await supabase.from('perfiles').update({ usuario: limpiarUsuario(usuario) }).eq('id', await uid()))
}

export async function guardarPrivacidad(cambios: { buscable?: string; horario_visible?: string }) {
  ok(await supabase.from('perfiles').update(cambios).eq('id', await uid()))
}

/** Le avisa a la otra persona por notificación (si falla, la solicitud queda igual). */
const avisar = (amistad: string, evento: 'solicitud' | 'aceptada') =>
  supabase.functions.invoke('enviar-notificacion', { body: { amistad, evento } }).catch(() => {})

export async function enviarSolicitud(para: string) {
  const fila = ok<{ id: string }>(await supabase.from('amistades').insert({ para }).select('id').single())
  await avisar(fila.id, 'solicitud')
}

export async function aceptarSolicitud(amistad: string) {
  ok(await supabase.from('amistades').update({ estado: 'aceptada' }).eq('id', amistad))
  await avisar(amistad, 'aceptada')
}

/** Rechazar, cancelar o eliminar amigo. */
export async function quitarAmistad(amistad: string) {
  ok(await supabase.from('amistades').delete().eq('id', amistad))
}

export async function bloquear(persona: string) {
  ok(await supabase.from('bloqueos').insert({ bloqueado: persona }))
}

/** Horarios de amigos que lo comparten (las reglas de Supabase ocultan el resto). */
export async function horariosDe(ids: string[]): Promise<Record<string, Clase[]>> {
  if (!ids.length) return {}
  // horarios_de: solo amigos que dejan ver su horario, y sin los apuntes privados.
  const filas = ok<Clase[]>(await supabase.rpc('horarios_de', { ids }))
  const r: Record<string, Clase[]> = {}
  for (const c of filas) (r[c.usuario_id ?? ''] ??= []).push(c)
  return r
}

// Al salir, este navegador deja de recibir los avisos de esa cuenta.
export const salir = async () => { await olvidarNavegador(); return supabase.auth.signOut() }
/** Seguridad: cierra la sesión en TODOS los teléfonos y computadoras. */
export const salirDeTodo = async () => { await olvidarNavegador(); return supabase.auth.signOut({ scope: 'global' }) }

// Test vocacional: se puede hacer sin iniciar sesión.
export const preguntasVocacionales = async () =>
  ok<PreguntaVocacional[]>(await supabase.from('vocacional_preguntas').select().order('id'))

export const carrerasVocacionales = async () =>
  ok<CarreraVocacional[]>(await supabase.from('vocacional_carreras').select().order('nombre'))

// ---------- Carreras que agregan los estudiantes ----------

/** Lee el pensum (fotos, PDF o texto copiado) con IA y devuelve las materias para revisarlas. */
export async function leerPensumConIA(archivos: { datos: string; tipo: string }[], texto: string) {
  const { data, error } = await supabase.functions.invoke('leer-pensum', { body: { archivos, texto } })
  if (error) {
    const detalle = await (error as { context?: Response }).context?.text?.().catch(() => '')
    throw new Error(detalle || String(error))
  }
  return data as { carrera: string | null; materias: MateriaLeida[] }
}

/** Guarda la carrera con su pensum para todos (la base de datos lo valida). Si ya existía con ese nombre, devuelve esa. */
export async function agregarCarrera(universidadId: string, nombre: string, materias: MateriaLeida[]) {
  return ok<{ id: string; existente: boolean }>(await supabase.rpc('agregar_carrera', { universidad: universidadId, nombre, materias }))
}

// ---------- Perfil: tutorial y privacidad nueva ----------
export async function marcarTutorialVisto() {
  ok(await supabase.from('perfiles').update({ tutorial_visto: true }).eq('id', await uid()))
}
export async function guardarLogrosVisibles(visibles: boolean) {
  ok(await supabase.from('perfiles').update({ logros_visibles: visibles }).eq('id', await uid()))
}

// ---------- Soporte (5inco) ----------
export async function enviarSoporte(tipo: 'comentario' | 'problema' | 'ayuda', mensaje: string, pantalla: string) {
  const fila = ok<{ id: string }>(await supabase.from('soporte')
    .insert({ tipo, mensaje: mensaje.trim(), pantalla, plataforma: 'web', version: 'web' }).select('id').single())
  await supabase.functions.invoke('enviar-notificacion', { body: { soporte: fila.id } }).catch(() => {})
}
export async function borrarMiHorario() { ok(await supabase.from('horario').delete().eq('usuario_id', await uid())) }
export async function reiniciarMiProgreso() { ok(await supabase.from('progreso').delete().eq('usuario_id', await uid())) }
export async function eliminarMiCuenta() {
  await olvidarNavegador()
  const { error } = await supabase.functions.invoke('eliminar-cuenta', { body: { confirmar: 'ELIMINAR' } })
  if (error) throw error
  await supabase.auth.signOut()
}

// ---------- Estudio: tareas, concentración y aula virtual ----------
export const tareas = async () =>
  ok<Tarea[]>(await supabase.from('tareas').select('id,titulo,materia,detalle,entrega,hecha,origen').order('entrega', { ascending: true, nullsFirst: false }))
export async function guardarTarea(t: { titulo: string; materia: string | null; detalle: string | null; entrega: string | null }, id?: string) {
  if (id) ok(await supabase.from('tareas').update(t).eq('id', id))
  else ok(await supabase.from('tareas').insert(t))
}
export async function marcarTarea(id: string, hecha: boolean) { ok(await supabase.from('tareas').update({ hecha }).eq('id', id)) }
export async function borrarTarea(id: string) { ok(await supabase.from('tareas').delete().eq('id', id)) }

export async function guardarSesion(minutos: number, materia: string | null) {
  ok(await supabase.from('sesiones_estudio').insert({ minutos, materia }))
}
export const ranking = async () => ok<Posicion[]>(await supabase.rpc('ranking_concentracion'))

export const estadoAula = async () =>
  ok<EstadoAula | null>(await supabase.from('aulas_virtuales').select('estado,ultimo_error,sincronizada').maybeSingle())
export async function conectarAula(url: string) {
  // Se guarda con una función del servidor: el enlace es secreto y nadie (ni la app) puede volver a leerlo
  ok(await supabase.rpc('conectar_aula', { enlace: url.trim() }))
  return sincronizarAula()
}
export async function sincronizarAula(): Promise<{ nuevas?: number; total?: number }> {
  const { data, error } = await supabase.functions.invoke('sincronizar-aula', { body: {} })
  if (error) {
    const detalle = await (error as { context?: Response }).context?.json?.().catch(() => null)
    throw new Error(detalle?.error ?? 'No se pudo actualizar el aula virtual')
  }
  return data ?? {}
}
export async function desconectarAula() { ok(await supabase.from('aulas_virtuales').delete().eq('usuario_id', await uid())) }

// ---------- Amigos: logros, chocar los 5 e invitaciones ----------
export const logrosAmigos = async () => ok<Logro[]>(await supabase.rpc('logros_amigos'))
export const interacciones = async () =>
  ok<Interaccion[]>(await supabase.from('interacciones').select('id,de,para,tipo,materia,cuando,mensaje,respuesta,creada')
    .order('creada', { ascending: false }).limit(50))
async function nuevaInteraccion(fila: { para: string; tipo: 'choca5' | 'invitacion'; materia?: string | null; cuando?: string | null; mensaje?: string | null }) {
  const r = ok<{ id: string }>(await supabase.from('interacciones').insert(fila).select('id').single())
  await supabase.functions.invoke('enviar-notificacion', { body: { interaccion: r.id } }).catch(() => {})
}
export const chocarLos5 = (para: string, materia: string) => nuevaInteraccion({ para, tipo: 'choca5', materia })
export const invitarAEstudiar = (para: string, materia: string | null, cuando: string | null, mensaje: string | null) =>
  nuevaInteraccion({ para, tipo: 'invitacion', materia, cuando, mensaje })
export async function responderInvitacion(id: string, si: boolean) {
  ok(await supabase.from('interacciones').update({ respuesta: si ? 'si' : 'no' }).eq('id', id))
  await supabase.functions.invoke('enviar-notificacion', { body: { interaccion: id, respuesta: true } }).catch(() => {})
}
