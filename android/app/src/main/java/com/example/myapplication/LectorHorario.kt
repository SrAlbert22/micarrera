package com.example.myapplication

/**
 * Lee el texto de la página de horario del portal (document.body.innerText) y saca las clases.
 * Igual que [LectorRecord]: todo pasa en el teléfono, sin IA.
 *
 * Entiende tres formas comunes de mostrar un horario:
 *  1. Lista por materia: "TCI-101  Intro a la Ciberseguridad  LU/MI  08:00-10:00  Aula A-101  Juan Pérez".
 *  2. Agrupado por día: un renglón "Lunes" y debajo las clases de ese día.
 *  3. Cuadrícula: una fila de encabezado con los días y una fila por hora.
 *  4. Tabla por materia con una columna por día y la hora dentro de la celda.
 */
object LectorHorario {

    private const val L = """(?<!\p{L})"""   // inicio de palabra (también con tildes)
    private const val R = """(?!\p{L})"""    // fin de palabra

    private val DIAS: List<Pair<Int, Regex>> = listOf(
        1 to "lunes|lun|lu",
        2 to "martes|mar|ma",
        3 to "mi[ée]rcoles|mi[ée]r?|mi",
        4 to "jueves|jue|ju",
        5 to "viernes|vie|vi",
        6 to "s[áa]bado|s[áa]b|sa",
        7 to "domingo|dom|do"
    ).map { (n, p) -> n to Regex("$L(?:$p)$R", RegexOption.IGNORE_CASE) }

    private val UN_DIA = Regex(
        "$L(?:lunes|lun|lu|martes|mar|ma|mi[ée]rcoles|mi[ée]r?|mi|jueves|jue|ju|viernes|vie|vi|s[áa]bado|s[áa]b|sa|domingo|dom|do)$R",
        RegexOption.IGNORE_CASE
    )

    /** "8:00 am - 10:00 am", "08:00-10:00", "14:00 a 16:00", "8.00 - 10.00" */
    private val RANGO = Regex(
        """(\d{1,2})[:.](\d{2})(?:\s*([ap])\.?\s?m\b\.?)?\s*(?:-|–|—|a|al|hasta)\s*(\d{1,2})[:.](\d{2})(?:\s*([ap])\.?\s?m\b\.?)?""",
        RegexOption.IGNORE_CASE
    )

    private val CODIGO = Regex("""\b([A-Z]{2,4})[- ]?(\d{3,4})\b""")
    private val AULA = Regex("""(?:aula|sal[óo]n|lab\w*|edif\w*)\s*:?\s*([^\t\n|]+)""", RegexOption.IGNORE_CASE)
    private val AULA_SUELTA = Regex("""^[A-Z]{1,4}-?\d{1,4}[A-Z]?$""")
    private val PROFESOR = Regex("""(?:prof(?:esor|esora)?\.?|docente|maestro)\s*:?\s*(.+)""", RegexOption.IGNORE_CASE)
    private val GRUPO = Regex("""(?:grupo|secci[óo]n|sec\.)\s*:?\s*(\w{1,4})\b""", RegexOption.IGNORE_CASE)
    private val NO_ES_MATERIA = Regex(
        """^(secci[óo]n|grupo|cr[ée]ditos?|horario|d[íi]as?|aula|sal[óo]n|profesor|docente|modalidad|presencial|virtual)\b""",
        RegexOption.IGNORE_CASE
    )

    fun leer(texto: String): List<Clase> {
        // Para las tablas no se quitan los tabs de las orillas: marcan columnas vacías.
        val filas = texto.lines().map { it.trim(' ', '\r') }.filter { it.isNotBlank() }
        val renglones = texto.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val clases = leerCuadricula(filas)?.takeIf { it.isNotEmpty() } ?: leerLista(renglones)
        return clases.distinctBy { Triple(it.materia.lowercase(), it.dia, it.horaInicio) }
            .sortedWith(compareBy({ it.dia }, { it.horaInicio }))
    }

    // ---------- Formato 1 y 2: lista ----------

    private fun leerLista(renglones: List<String>): List<Clase> {
        val hayCodigos = renglones.any { CODIGO.containsMatchIn(it) }
        val clases = mutableListOf<Clase>()
        var diaDelGrupo: Int? = null   // formato 2: renglón "Lunes" como título
        var bloque = StringBuilder()
        var diaDelBloque: Int? = null
        var anterior: String? = null   // sin códigos: el nombre suele estar en el renglón de arriba

        fun cerrarBloque() {
            if (bloque.isNotEmpty()) clases += clasesDelBloque(bloque.toString(), diaDelBloque)
            bloque = StringBuilder()
        }

        for (r in renglones) {
            val soloDia = diaSiEsTitulo(r)
            when {
                soloDia != null -> { cerrarBloque(); diaDelGrupo = soloDia }
                hayCodigos && CODIGO.containsMatchIn(r) -> {
                    cerrarBloque(); bloque.append(r); diaDelBloque = diaDelGrupo
                }
                hayCodigos -> if (bloque.isNotEmpty() && bloque.count { it == '\n' } < 8) bloque.append('\n').append(r)
                RANGO.containsMatchIn(r) -> {
                    val conNombre = if (tieneNombre(r) || anterior == null) r else "$anterior\n$r"
                    clases += clasesDelBloque(conNombre, diaDelGrupo)
                }
            }
            if (!RANGO.containsMatchIn(r) && soloDia == null) anterior = r
        }
        cerrarBloque()
        return clases
    }

    /** Un renglón que es solo el nombre de un día ("Lunes", "LUNES 12/09") es un título de grupo. */
    private fun diaSiEsTitulo(r: String): Int? {
        val limpio = r.replace(Regex("""[\d/.,:-]"""), "").trim()
        return DIAS.firstOrNull { (_, re) -> re.matchEntire(limpio) != null && limpio.length >= 5 }?.first
    }

    private fun clasesDelBloque(bloque: String, diaPorDefecto: Int?): List<Clase> {
        val datos = datosDeMateria(bloque)
        val clases = mutableListOf<Clase>()
        var desde = 0
        var ultimoDia = diaPorDefecto
        for (m in RANGO.findAll(bloque)) {
            // Los días que tocan a este horario son los que están entre el horario anterior y este.
            val dias = diasEn(bloque.substring(desde, m.range.first)).ifEmpty { listOfNotNull(ultimoDia) }
            val (inicio, fin) = horas(m) ?: continue
            dias.forEach { d -> clases += datos.clase(d, inicio, fin) }
            if (dias.isNotEmpty()) ultimoDia = dias.last()
            desde = m.range.last + 1
        }
        return clases
    }

    private fun diasEn(texto: String): List<Int> {
        // Quita códigos de materia/aula para que "MA-101" no cuente como martes.
        val limpio = texto.replace(CODIGO, " ").replace(Regex("""[A-Z]{1,4}-\d+"""), " ")
        return UN_DIA.findAll(limpio).mapNotNull { m -> DIAS.firstOrNull { it.second.matchEntire(m.value) != null }?.first }
            .distinct().toList()
    }

    private data class Datos(
        val materia: String, val aula: String?, val profesor: String?, val codigo: String?, val grupo: String?
    ) {
        fun clase(dia: Int, inicio: String, fin: String) = Clase(
            materia = materia, dia = dia, horaInicio = inicio, horaFin = fin,
            aula = aula, profesor = profesor, codigo = codigo, grupo = grupo
        )
    }

    private fun datosDeMateria(bloque: String): Datos {
        val codigo = CODIGO.find(bloque)?.let { "${it.groupValues[1]}-${it.groupValues[2]}" }
        val aula = AULA.find(bloque)?.groupValues?.get(1)?.trim()?.take(40)?.ifBlank { null }
        val profesorEtiquetado = PROFESOR.find(bloque)?.groupValues?.get(1)?.split('\t', '\n')?.first()?.trim()

        val celdas = bloque.replace(CODIGO, "\t").replace(RANGO, "\t")
            .split('\t', '\n', '|').map { it.trim() }.filter { it.isNotEmpty() }
            .flatMap { if ('\t' !in bloque && '\n' !in bloque) it.split(Regex("""\s{2,}""")) else listOf(it) }
        val textos = celdas.filter { c ->
            c.count(Char::isLetter) >= 3 && !NO_ES_MATERIA.containsMatchIn(c) && AULA.find(c) == null &&
                PROFESOR.find(c) == null && UN_DIA.replace(c, "").count(Char::isLetter) >= 3
        }
        val materia = textos.firstOrNull()?.let { quitarDias(it) } ?: codigo ?: "Clase"
        val profesor = profesorEtiquetado
            ?: textos.drop(1).firstOrNull { pareceNombreDePersona(it) }
        val aulaFinal = aula ?: celdas.firstOrNull { AULA_SUELTA.matches(it) }
        val grupo = GRUPO.find(bloque)?.groupValues?.get(1)
        return Datos(materia, aulaFinal, profesor, codigo, grupo)
    }

    private fun quitarDias(s: String) = s.replace(Regex("""\s+(?:(?:LU|MA|MI|JU|VI|SA|DO)[/,\s-]*)+$"""), "")
        .trim().trimStart('-', '–', ':', '·', ' ')

    private fun pareceNombreDePersona(s: String): Boolean {
        val palabras = s.split(Regex("""\s+"""))
        return palabras.size in 2..5 && palabras.all { it.first().isUpperCase() }
    }

    private fun tieneNombre(r: String) = r.replace(RANGO, "").replace(UN_DIA, "").count(Char::isLetter) >= 4

    // ---------- Formatos 3 y 4: tablas con los días como columnas ----------

    private fun leerCuadricula(renglones: List<String>): List<Clase>? {
        val iEncabezado = renglones.indexOfFirst { r ->
            r.split('\t').count { c -> DIAS.any { it.second.matchEntire(c.trim()) != null } } >= 3
        }
        if (iEncabezado < 0) return null
        val columnas = renglones[iEncabezado].split('\t').map { c -> DIAS.firstOrNull { it.second.matchEntire(c.trim()) != null }?.first }

        val sueltas = mutableListOf<Clase>()
        for (r in renglones.drop(iEncabezado + 1)) {
            val celdas = r.split('\t')
            val rangoDeFila = RANGO.find(celdas.firstOrNull().orEmpty())
            if (rangoDeFila != null) {
                // Formato 3: "08:00-09:00 \t TCI-101 Intro \t \t ..." → una clase de una hora por celda llena.
                val (inicio, fin) = horas(rangoDeFila) ?: continue
                celdas.forEachIndexed { i, c ->
                    val dia = columnas.getOrNull(i) ?: return@forEachIndexed
                    if (c.isBlank()) return@forEachIndexed
                    val d = datosDeMateria(c.trim())
                    sueltas += d.clase(dia, inicio, fin)
                }
            } else {
                // Formato 4: "TCI-101 \t Intro \t 08:00-10:00 \t \t 08:00-10:00" → la hora está en la columna del día.
                val info = celdas.filterIndexed { i, _ -> columnas.getOrNull(i) == null }.joinToString("\t")
                if (info.isBlank()) continue
                val d = datosDeMateria(info.trim())
                celdas.forEachIndexed { i, c ->
                    val dia = columnas.getOrNull(i) ?: return@forEachIndexed
                    RANGO.findAll(c).forEach { m ->
                        horas(m)?.let { (inicio, fin) ->
                            sueltas += d.clase(dia, inicio, fin)
                        }
                    }
                }
            }
        }
        // Une las horas seguidas de la misma materia el mismo día (08-09 + 09-10 → 08-10).
        val unidas = mutableListOf<Clase>()
        for (c in sueltas.sortedWith(compareBy({ it.dia }, { it.materia }, { it.horaInicio }))) {
            val ultima = unidas.lastOrNull()
            if (ultima != null && ultima.dia == c.dia && ultima.materia == c.materia && ultima.horaFin == c.horaInicio) {
                unidas[unidas.lastIndex] = ultima.copy(horaFin = c.horaFin)
            } else unidas += c
        }
        return unidas
    }

    // ---------- Horas ----------

    /** Pasa un rango a 24 h ("HH:MM", "HH:MM"). Sin am/pm, las horas de 1 a 6 se toman como de la tarde. */
    private fun horas(m: MatchResult): Pair<String, String>? {
        val g = m.groupValues
        val marcaFin = g[6].lowercase().ifEmpty { null }
        val marcaInicio = g[3].lowercase().ifEmpty { null }
        var fin = a24(g[4].toInt(), marcaFin) ?: return null
        var inicio = a24(g[1].toInt(), marcaInicio ?: marcaFin) ?: return null
        // "11:00 - 1:00 pm": el pm solo es del final.
        if (marcaInicio == null && inicio > fin) inicio = a24(g[1].toInt(), null) ?: return null
        if (inicio >= fin && fin < 12) fin += 12
        if (inicio >= fin || g[2].toInt() > 59 || g[5].toInt() > 59) return null
        return "%02d:%s".format(inicio, g[2]) to "%02d:%s".format(fin, g[5])
    }

    private fun a24(h: Int, marca: String?): Int? = when {
        h > 23 -> null
        marca == "p" -> if (h < 12) h + 12 else h
        marca == "a" -> if (h == 12) 0 else h
        h in 1..6 -> h + 12
        else -> h
    }
}
