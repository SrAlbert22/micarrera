package com.example.myapplication

// Errores comunes al escribir el correo (un código a "gamil.com" nunca llega). Igual que util.ts en la web.
private val DOMINIOS = mapOf(
    "gamil.com" to "gmail.com", "gmial.com" to "gmail.com", "gmai.com" to "gmail.com", "gmal.com" to "gmail.com",
    "gnail.com" to "gmail.com", "gmaill.com" to "gmail.com", "gmail.co" to "gmail.com", "gmail.con" to "gmail.com",
    "gmail.cm" to "gmail.com", "gmail.om" to "gmail.com", "gmail.comm" to "gmail.com", "gimail.com" to "gmail.com",
    "hotmial.com" to "hotmail.com", "hotmal.com" to "hotmail.com", "hotmil.com" to "hotmail.com", "hotmail.co" to "hotmail.com",
    "hotmail.con" to "hotmail.com", "hormail.com" to "hotmail.com", "outlok.com" to "outlook.com", "outllok.com" to "outlook.com",
    "outlook.co" to "outlook.com", "outlook.con" to "outlook.com", "yaho.com" to "yahoo.com", "yahoo.co" to "yahoo.com",
    "yahoo.con" to "yahoo.com", "iclod.com" to "icloud.com", "icloud.co" to "icloud.com",
)

/** Si el correo parece mal escrito ("ana@gamil.com"), devuelve la versión corregida; si no, null. */
fun correccionCorreo(correo: String): String? {
    val partes = correo.trim().lowercase().split("@")
    if (partes.size != 2 || partes[0].isEmpty()) return null
    return DOMINIOS[partes[1]]?.let { "${partes[0]}@$it" }
}
