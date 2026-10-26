// Mismas tablas que usa la app de Android (ver Modelos.kt).

export type Estado = 'pendiente' | 'en_curso' | 'aprobada'

export interface Universidad { id: string; nombre: string; siglas: string | null; logo_url?: string | null; aula_url?: string | null }
export interface Carrera { id: string; universidad_id: string; nombre: string; aportada_por?: string | null }
export interface CarreraDetalle { id: string; nombre: string; universidades: Universidad | null }

export interface Materia {
  id: string
  carrera_id: string
  codigo: string
  nombre: string
  creditos: number
  cuatrimestre: number
  prerequisitos: string[]
}

export interface Progreso {
  usuario_id: string
  materia_id: string
  estado: Estado
  nota: number | null
  letra: string | null
  origen: string
}

export interface Perfil {
  id: string
  carrera_id: string | null
  indice: number | null
  portal_actualizado: string | null
  nombre: string | null
  usuario: string | null                      // @usuario, único
  buscable: 'universidad' | 'usuario' | 'nadie'
  horario_visible: 'amigos' | 'nadie'
  tutorial_visto?: boolean                    // 5inco ya hizo el recorrido (una vez por cuenta)
  logros_visibles?: boolean                   // los amigos ven qué materias apruebas (sin nota)
}

export interface Clase {
  id?: string
  usuario_id?: string
  materia: string
  dia: number // 1 = lunes … 7 = domingo
  hora_inicio: string
  hora_fin: string
  aula?: string | null
  profesor?: string | null
  codigo?: string | null
  grupo?: string | null
  apunte?: string | null
}

/** Test vocacional (modelo RIASEC de Holland). Ver vocacional.ts. */
export type TipoVocacional = 'R' | 'I' | 'A' | 'S' | 'E' | 'C'
export interface PreguntaVocacional { id: number; tipo: TipoVocacional; texto: string }
export interface CarreraVocacional { id: number; nombre: string; emoji: string; tipos: string; descripcion: string; universidades: string[] }

/** Materia leída del pensum con IA (leer-pensum), antes de guardarla con agregar_carrera(). */
export interface MateriaLeida { codigo: string; nombre: string; creditos: number; periodo: number; prerequisitos: string[] }

// ---------- Estudio ----------
export interface Tarea {
  id: string; titulo: string; materia: string | null; detalle: string | null
  entrega: string | null; hecha: boolean; origen: 'manual' | 'aula'
}
export interface Posicion { id: string; nombre: string | null; usuario: string | null; minutos: number; soy_yo: boolean }
export interface EstadoAula { estado: 'pendiente' | 'ok' | 'error'; ultimo_error: string | null; sincronizada: string | null }

// ---------- Amigos ----------
export interface Logro { amigo: string; nombre: string | null; usuario: string | null; materia: string; fecha: string; ya_chocaste: boolean }
export interface Interaccion {
  id: string; de: string; para: string; tipo: 'choca5' | 'invitacion'; materia: string | null
  cuando: string | null; mensaje: string | null; respuesta: 'si' | 'no' | null; creada: string
}
