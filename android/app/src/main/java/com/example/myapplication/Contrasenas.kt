package com.example.myapplication

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Contraseñas seguras (lo mismo que contrasenas.ts en la web):
 *  - Reglas locales: mínimo 8, no solo números, no repetir un carácter, no las más comunes, no tu correo.
 *  - Contraseñas filtradas: se consulta Have I Been Pwned con "k-anonimato": solo salen del teléfono los
 *    5 primeros caracteres del SHA-1 de la contraseña, nunca la contraseña. Es lo que Supabase cobra en el plan Pro.
 */
object Contrasenas {
    const val MINIMO = 8

    private val COMUNES = setOf(
        "12345678", "123456789", "1234567890", "87654321", "11111111", "00000000", "12341234", "11223344",
        "password", "password1", "password123", "contraseña", "contrasena", "contraseña1", "contrasena1",
        "qwerty123", "qwertyui", "asdfghjk", "abcd1234", "abc12345", "iloveyou", "teamo123", "micarrera",
        "micarrera1", "micarrera123", "itla2026", "itla1234", "uasd1234", "dominicana", "republica",
        "12345678a", "a12345678", "1q2w3e4r", "zaq12wsx", "admin123", "welcome1", "estudiante", "universidad",
    )

    /** Problema de la contraseña (sin internet), o null si pasa las reglas. */
    fun problemaLocal(contrasena: String, correo: String? = null): String? {
        val c = contrasena.lowercase()
        val usuario = correo?.substringBefore("@")?.lowercase().orEmpty()
        return when {
            contrasena.length < MINIMO -> "Usa al menos $MINIMO caracteres."
            contrasena.all { it.isDigit() } -> "No uses solo números: mezcla letras y números."
            contrasena.toSet().size <= 2 -> "No repitas el mismo carácter."
            c in COMUNES || c.trimEnd { it.isDigit() || !it.isLetterOrDigit() } in COMUNES -> "Esa contraseña es de las más usadas. Elige otra."
            usuario.length >= 4 && usuario in c -> "No uses tu correo dentro de la contraseña."
            "micarrera" in c -> "No uses el nombre de la app en tu contraseña."
            else -> null
        }
    }

    fun sha1(texto: String): String =
        MessageDigest.getInstance("SHA-1").digest(texto.toByteArray()).joinToString("") { "%02X".format(it) }

    /** Cuántas veces aparece [sufijo] en la respuesta de /range (líneas "SUFIJO:VECES"; las de relleno traen 0). */
    fun vecesEnRespuesta(sufijo: String, cuerpo: String): Int =
        cuerpo.lineSequence().map { it.trim().split(":") }
            .firstOrNull { it.size == 2 && it[0].equals(sufijo, ignoreCase = true) }
            ?.get(1)?.toIntOrNull() ?: 0

    /** Veces que la contraseña salió en filtraciones, o null si no se pudo revisar (sin internet). */
    suspend fun vecesFiltrada(contrasena: String): Int? = withContext(Dispatchers.IO) {
        runCatching {
            val hash = sha1(contrasena)
            val con = URL("https://api.pwnedpasswords.com/range/${hash.take(5)}").openConnection() as HttpURLConnection
            con.connectTimeout = 5000; con.readTimeout = 5000; con.instanceFollowRedirects = false
            con.setRequestProperty("Add-Padding", "true") // respuestas del mismo tamaño: nadie deduce nada por el tráfico
            con.setRequestProperty("User-Agent", "MiCarrera-Android")
            try {
                if (con.responseCode != 200) null else vecesEnRespuesta(hash.drop(5), con.inputStream.bufferedReader().readText())
            } finally { con.disconnect() }
        }.getOrNull()
    }

    /** Todas las revisiones: el problema a mostrar, o null si la contraseña es buena. */
    suspend fun revisar(contrasena: String, correo: String? = null): String? {
        problemaLocal(contrasena, correo)?.let { return it }
        val veces = vecesFiltrada(contrasena) ?: return null // sin internet no bloqueamos
        return if (veces > 0) "Esa contraseña apareció en filtraciones de datos de otras páginas. Elige otra que no uses en ningún lado." else null
    }
}
