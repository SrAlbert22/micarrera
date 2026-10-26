package com.example.myapplication

/**
 * Lee el texto del récord de calificaciones del portal (lo que devuelve document.body.innerText)
 * y saca materias, notas e índice. Todo pasa en el teléfono: sin IA y sin enviar el texto a ningún lado.
 *
 * No depende de un formato exacto: en cada renglón busca un código de materia (TCI-101, MAT 101…)
 * y alrededor de él la nota (10–100), la letra (A, B+, F, W…) y palabras como "en curso" o "retirada".
 * Sirve tanto si la página es una tabla (celdas separadas por tabs) como si pone cada dato en su renglón.
 */
object LectorRecord {
    /** Nota mínima para aprobar cuando la página no trae la letra. */
    const val NOTA_MINIMA = 70.0

    private val CODIGO = Regex("""\b([A-Z]{2,4})[- ]?(\d{3,4})\b""")
    private val NUMERO = Regex("""^\d{1,3}(?:[.,]\d{1,2})?$""")
    private val LETRA = Regex("""^(?:[ABCD][+-]?|F|FN|R|W|RET)$""")
    private val INDICE = Regex("""[ÍI]ndice[^\d\n]{0,40}?(\d(?:[.,]\d{1,3})?)""", RegexOption.IGNORE_CASE)
    /** Renglones que marcan el final de la tabla (totales, índice…): no se pegan a la última materia. */
    private val FIN_DE_TABLA = Regex("""total|[íi]ndice|promedio""", RegexOption.IGNORE_CASE)
    private val ESTADO = Regex("""en curso|cursando|inscrit|retir|reprob|aprob|cr[ée]ditos|per[íi]odo""", RegexOption.IGNORE_CASE)
    private const val MAX_RENGLONES_EXTRA = 6

    fun leer(texto: String): RecordPortal {
        // 1. Agrupar: cada renglón con código empieza una materia; los siguientes sin código son sus datos.
        val bloques = mutableListOf<Pair<String, StringBuilder>>()
        var extra = 0
        for (renglon in texto.lines()) {
            val r = renglon.trim()
            if (r.isEmpty()) continue
            val codigo = buscarCodigo(r)
            when {
                codigo != null -> { bloques += codigo to StringBuilder(r); extra = 0 }
                FIN_DE_TABLA.containsMatchIn(r) -> extra = MAX_RENGLONES_EXTRA
                bloques.isNotEmpty() && extra < MAX_RENGLONES_EXTRA -> {
                    bloques.last().second.append('\n').append(r); extra++
                }
            }
        }

        val materias = bloques.map { (codigo, bloque) -> leerMateria(codigo, bloque.toString()) }

        // Si ninguna materia trae nota ni letra, no es un récord (p. ej. es la página del pensum).
        val pareceRecord = materias.any { it.nota != null || it.letra != null }
        return RecordPortal(
            materias = if (pareceRecord) materias else emptyList(),
            indice = buscarIndice(texto)
        )
    }

    private fun buscarCodigo(renglon: String): String? =
        CODIGO.findAll(renglon)
            .firstOrNull { m ->
                val numero = m.groupValues[2]
                // "ITLA 2024" no es una materia: descarta años.
                !(numero.length == 4 && (numero.startsWith("19") || numero.startsWith("20")))
            }
            ?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }

    private fun leerMateria(codigo: String, bloque: String): MateriaPortal {
        val sinCodigo = bloque.replaceFirst(CODIGO, " ")
        // Celdas: por tabs o renglones; si la tabla viene con espacios, por grupos de 2+ espacios o palabra por palabra.
        val celdas = sinCodigo.split('\t', '\n', '|').map { it.trim() }.filter { it.isNotEmpty() }
            .let { if (it.size > 1) it else sinCodigo.trim().split(Regex("""\s{2,}""")) }
            .let { if (it.size > 1) it else sinCodigo.trim().split(Regex("""\s+""")) }
            .filter { it.isNotEmpty() }

        val nota = celdas.mapNotNull { c ->
            if (NUMERO.matches(c)) c.replace(',', '.').toDouble().takeIf { it in 10.0..100.0 } else null
        }.lastOrNull()
        val letra = celdas.lastOrNull { LETRA.matches(it.uppercase()) }?.uppercase()
        val nombre = celdas.filter { c ->
            c.count(Char::isLetter) >= 3 && !LETRA.matches(c.uppercase()) && !ESTADO.containsMatchIn(c)
        }.maxByOrNull { it.length }

        val t = bloque.lowercase()
        val estado = when {
            "retir" in t || letra == "W" || letra == "R" || letra == "RET" -> "retirada"
            "en curso" in t || "cursando" in t || "inscrit" in t -> "en_curso"
            "reprob" in t || letra == "F" || letra == "FN" -> "reprobada"
            letra != null -> "aprobada"
            nota != null -> if (nota >= NOTA_MINIMA) "aprobada" else "reprobada"
            else -> "en_curso"
        }
        return MateriaPortal(codigo = codigo, nombre = nombre, nota = nota, letra = letra, estado = estado)
    }

    /** Índice académico (escala 0–4). Prefiere el acumulado si la página trae varios. */
    private fun buscarIndice(texto: String): Double? {
        val encontrados = INDICE.findAll(texto).mapNotNull { m ->
            val valor = m.groupValues[1].replace(',', '.').toDoubleOrNull()?.takeIf { it in 0.0..4.0 }
            valor?.let { m.value.contains("acum", ignoreCase = true) to it }
        }.toList()
        return (encontrados.lastOrNull { it.first } ?: encontrados.lastOrNull())?.second
    }
}
