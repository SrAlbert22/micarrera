package com.example.myapplication

import android.content.Context
import android.content.Intent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Una persona en la sección Amigos (lo que devuelven las funciones mis_amigos, sugerencias_amigos
 * y buscar_usuarios de Supabase). Nunca trae notas ni índice: solo el avance en % de los amigos aceptados.
 */
@Serializable
data class Persona(
    val id: String,
    val nombre: String? = null,
    val usuario: String? = null,
    val universidad: String? = null,
    val carrera: String? = null,
    val amistad: String? = null,              // id de la amistad (solo en mis_amigos)
    val estado: String? = null,               // pendiente | aceptada
    @SerialName("enviada_por_mi") val enviadaPorMi: Boolean = false,
    val avance: Int? = null,                  // % de créditos aprobados (solo amigos aceptados)
    @SerialName("en_comun") val enComun: Int = 0 // materias en común (solo sugerencias)
) {
    val nombreVisible: String get() = nombre?.takeIf { it.isNotBlank() } ?: usuario?.let { "@$it" } ?: "Estudiante"
    val iniciales: String get() = nombreVisible.removePrefix("@").split(Regex("""[\s._-]+"""))
        .filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "?" }
}

/** Un hueco libre de los dos: día (1–7) y horas "HH:MM". */
data class HuecoLibre(val dia: Int, val desde: String, val hasta: String)

/** Si alguien está en clase ahora ([clase]) o libre (con su [proxima] clase de hoy, si tiene). */
data class Ahora(val clase: Clase?, val proxima: Clase?) { val libre get() = clase == null }

object CalculoAmigos {
    private fun min(h: String) = h.take(5).split(":").let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    /** ¿Está en clase en este momento? (día 1 = lunes) */
    fun estadoAhora(clases: List<Clase>, dia: Int, minuto: Int): Ahora {
        val hoy = clases.filter { it.dia == dia }.sortedBy { min(it.horaInicio) }
        val clase = hoy.firstOrNull { min(it.horaInicio) <= minuto && minuto < min(it.horaFin) }
        return Ahora(clase, if (clase == null) hoy.firstOrNull { min(it.horaInicio) > minuto } else null)
    }

    /** Formato válido de @usuario: 3 a 20 letras minúsculas, números, punto o guion bajo. */
    val FORMATO_USUARIO = Regex("^[a-z0-9._]{3,20}$")

    fun limpiarUsuario(texto: String) = texto.trim().removePrefix("@").lowercase()

    /** Un @usuario a partir del nombre: "María Rodríguez" -> "maria.rodriguez" (sin tildes, 3 a 20 caracteres). */
    fun sugerirUsuario(nombre: String): String {
        val partes = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase()
            .replace(Regex("[^a-z0-9\\s]"), "").split(Regex("\\s+")).filter { it.isNotBlank() }
        val base = partes.take(2).joinToString(".").take(20).trimEnd('.')
        return if (base.length >= 3) base else ""
    }

    private fun cod(c: Clase) = c.codigo.orEmpty().uppercase().filterNot { it == '-' || it.isWhitespace() }
    private fun minutos(h: String) =
        h.take(5).split(":").let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }
    private fun hora(m: Int) = "%02d:%02d".format(m / 60, m % 60)

    /**
     * Misma clase: mismo día y hora de inicio y, si las dos tienen código, mismo código y grupo
     * (otro grupo = otra sección). Sin código, se compara el nombre de la materia.
     */
    fun mismaClase(a: Clase, b: Clase): Boolean {
        if (a.dia != b.dia || a.horaInicio.take(5) != b.horaInicio.take(5)) return false
        val (ca, cb) = cod(a) to cod(b)
        if (ca.isNotEmpty() && cb.isNotEmpty()) {
            return ca == cb && (a.grupo.isNullOrBlank() || b.grupo.isNullOrBlank() || a.grupo.trim() == b.grupo.trim())
        }
        return a.materia.trim().equals(b.materia.trim(), ignoreCase = true)
    }

    /** Mis clases que también tiene el amigo, ordenadas por día y hora. */
    fun clasesEnComun(mias: List<Clase>, suyas: List<Clase>): List<Clase> =
        mias.filter { m -> suyas.any { mismaClase(m, it) } }.sortedWith(compareBy({ it.dia }, { minutos(it.horaInicio) }))

    /**
     * Horas en que los dos están libres (lunes a sábado, entre [desde] y [hasta], huecos de al menos [minimo] min),
     * solo en los días en que alguno de los dos tiene clase.
     */
    fun horasLibresEnComun(
        mias: List<Clase>, suyas: List<Clase>,
        desde: Int = 7 * 60, hasta: Int = 22 * 60, minimo: Int = 60
    ): List<HuecoLibre> {
        val todas = mias + suyas
        return (1..6).flatMap { dia ->
            val ocupado = todas.filter { it.dia == dia }
                .map { minutos(it.horaInicio).coerceAtLeast(desde) to minutos(it.horaFin).coerceAtMost(hasta) }
                .filter { it.first < it.second }
                .sortedBy { it.first }
            if (ocupado.isEmpty()) return@flatMap emptyList()
            val huecos = mutableListOf<HuecoLibre>()
            var cursor = desde
            for ((ini, fin) in ocupado) {
                if (ini - cursor >= minimo) huecos += HuecoLibre(dia, hora(cursor), hora(ini))
                cursor = maxOf(cursor, fin)
            }
            if (hasta - cursor >= minimo) huecos += HuecoLibre(dia, hora(cursor), hora(hasta))
            huecos
        }
    }

    /** Los amigos que van a esta clase (según sus horarios visibles). */
    fun amigosEnClase(clase: Clase, horarios: Map<Persona, List<Clase>>): List<Persona> =
        horarios.filter { (_, suyas) -> suyas.any { mismaClase(clase, it) } }.keys.toList()
}

/**
 * "Invitar amigos": abre el menú de compartir con un mensaje y tu enlace (…/?invita=usuario).
 * Quien lo abre ve quién lo invitó y, al registrarse, puede agregarte como amigo con un toque.
 */
fun compartirInvitacion(context: Context, usuario: String?) {
    val enlace = "https://micarrera-beta.vercel.app" + (usuario?.let { "/?invita=$it" } ?: "")
    val texto = "🎓 Te invito a MiCarrera: tu pensum, tu horario y tus notas en orden. " +
        "Así vemos qué clases tenemos juntos 👥\n👉 $enlace"
    val enviar = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
    context.startActivity(Intent.createChooser(enviar, "Invitar a MiCarrera"))
}
