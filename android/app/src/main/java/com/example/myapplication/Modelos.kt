package com.example.myapplication

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Universidad(
    val id: String,
    val nombre: String,
    val siglas: String? = null,
    @SerialName("portal_url") val portalUrl: String? = null, // página de inicio de sesión del portal de estudiantes
    @SerialName("logo_url") val logoUrl: String? = null,     // PNG cuadrado servido por la web (…/universidades/x.png)
    @SerialName("aula_url") val aulaUrl: String? = null      // aula virtual (Moodle) de la universidad
)

@Serializable
data class Carrera(
    val id: String,
    @SerialName("universidad_id") val universidadId: String,
    val nombre: String,
    @SerialName("aportada_por") val aportadaPor: String? = null // la agregó un estudiante con su pensum
)

/** Una versión publicada de la app (tabla "versiones"), para avisar que hay que actualizar. */
@Serializable
data class Version(
    @SerialName("version_code") val versionCode: Long,
    @SerialName("version_name") val versionName: String,
    val notas: String? = null,
    val url: String,
    val obligatoria: Boolean = false,
    val sha256: String? = null // huella del APK: si la descarga no coincide, no se instala
)

/** Carrera con su universidad, para la tarjeta del pensum. */
@Serializable
data class CarreraDetalle(
    val id: String,
    val nombre: String,
    val universidades: Universidad? = null
)

@Serializable
data class Materia(
    val id: String,
    @SerialName("carrera_id") val carreraId: String,
    val codigo: String,
    val nombre: String,
    val creditos: Int = 0,
    val cuatrimestre: Int = 1,
    val prerequisitos: List<String> = emptyList()
)

// Ojo: nuestro Json no envía los valores por defecto. @EncodeDefault obliga a mandarlos
// (por ejemplo carrera_id = null para "cambiar carrera").
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Perfil(
    val id: String,
    @EncodeDefault @SerialName("carrera_id") val carreraId: String? = null,
    val indice: Double? = null,
    @SerialName("portal_actualizado") val portalActualizado: String? = null,
    val nombre: String? = null,
    val usuario: String? = null,                                  // @usuario, único
    val buscable: String? = null,                                 // universidad | usuario | nadie
    @SerialName("horario_visible") val horarioVisible: String? = null, // amigos | nadie
    @SerialName("tutorial_visto") val tutorialVisto: Boolean? = null,  // 5inco ya hizo el recorrido (en cualquier dispositivo)
    @SerialName("logros_visibles") val logrosVisibles: Boolean? = null // los amigos ven las materias que apruebas
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Progreso(
    @SerialName("usuario_id") val usuarioId: String,
    @SerialName("materia_id") val materiaId: String,
    val estado: String, // pendiente | en_curso | aprobada
    @EncodeDefault val nota: Double? = null,
    @EncodeDefault val letra: String? = null,
    @EncodeDefault val origen: String = "manual" // manual | portal | foto
)

/** Lo que devuelve la Edge Function "importar-record". */
@Serializable
data class MateriaPortal(
    val codigo: String,
    val nombre: String? = null,
    val nota: Double? = null,
    val letra: String? = null,
    val estado: String // aprobada | en_curso | reprobada | retirada
)

@Serializable
data class RecordPortal(
    val materias: List<MateriaPortal> = emptyList(),
    val indice: Double? = null
)

/** Resultado de guardar el récord en el pensum. */
data class ResultadoImportacion(val guardadas: Int, val sinCoincidir: List<String>)

@Serializable
data class Clase(
    val id: String? = null,
    @SerialName("usuario_id") val usuarioId: String? = null,
    val materia: String,
    val dia: Int, // 1 = lunes ... 7 = domingo
    @SerialName("hora_inicio") val horaInicio: String,
    @SerialName("hora_fin") val horaFin: String,
    val aula: String? = null,
    val profesor: String? = null,
    val codigo: String? = null,
    val grupo: String? = null,
    val apunte: String? = null
)

val DIAS = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")


/** Pregunta del test vocacional: suma a uno de los 6 tipos RIASEC (R, I, A, S, E, C). */
@Serializable
data class PreguntaVocacional(val id: Int, val tipo: String, val texto: String)

/** Carrera del test vocacional: [tipos] = tipo principal + secundario (ej. "IS"); [universidades] = siglas. */
@Serializable
data class CarreraVocacional(
    val id: Int,
    val nombre: String,
    val emoji: String,
    val tipos: String,
    val descripcion: String,
    val universidades: List<String> = emptyList()
)

/** Materia leída del pensum con IA (leer-pensum), antes de guardarla con agregar_carrera(). */
@Serializable
data class MateriaLeida(
    val codigo: String,
    val nombre: String,
    val creditos: Int = 0,
    val periodo: Int = 1,
    val prerequisitos: List<String> = emptyList()
)

@Serializable
data class PensumLeido(val carrera: String? = null, val materias: List<MateriaLeida> = emptyList())

@Serializable
data class CarreraAgregada(val id: String, val existente: Boolean = false)

// ---------- Estudio ----------

/** Un pendiente: agregado a mano o traído del aula virtual. [entrega] en ISO-8601. */
@Serializable
data class Tarea(
    val id: String,
    val titulo: String,
    val materia: String? = null,
    val detalle: String? = null,
    val entrega: String? = null,
    val hecha: Boolean = false,
    val origen: String = "manual" // manual | aula
)

/** Una fila del reto de concentración de la semana. */
@Serializable
data class Posicion(
    val id: String,
    val nombre: String? = null,
    val usuario: String? = null,
    val minutos: Int = 0,
    @SerialName("soy_yo") val soyYo: Boolean = false
)

/** Estado de la conexión con el aula virtual (el enlace nunca se puede volver a leer). */
@Serializable
data class EstadoAula(
    val estado: String, // pendiente | ok | error
    @SerialName("ultimo_error") val ultimoError: String? = null,
    val sincronizada: String? = null
)

@Serializable
data class ResultadoAula(val nuevas: Int? = null, val total: Int? = null, val error: String? = null)

// ---------- Amigos: logros e interacciones ----------

/** Un amigo aprobó una materia (últimos 14 días). */
@Serializable
data class Logro(
    val amigo: String,
    val nombre: String? = null,
    val usuario: String? = null,
    val materia: String,
    val fecha: String,
    @SerialName("ya_chocaste") val yaChocaste: Boolean = false
) {
    val nombreVisible: String get() = nombre?.takeIf { it.isNotBlank() } ?: usuario?.let { "@$it" } ?: "Tu amigo"
}

/** "Chocar los 5" o invitación a estudiar. */
@Serializable
data class Interaccion(
    val id: String,
    val de: String,
    val para: String,
    val tipo: String, // choca5 | invitacion
    val materia: String? = null,
    val cuando: String? = null,
    val mensaje: String? = null,
    val respuesta: String? = null, // si | no
    val creada: String
)
