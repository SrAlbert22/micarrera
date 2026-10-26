package com.example.myapplication

import java.time.LocalDate
import java.time.YearMonth

/** Una fila de "Créditos por período". */
data class PeriodoStats(val numero: Int, val aprobados: Int, val enCurso: Int, val total: Int)

/** Todo lo que muestra la pestaña Estadísticas, ya calculado. */
data class Estadisticas(
    val promedio: Double?,          // promedio de tus notas (0–100)
    val materiasConNota: Int,
    val indice: Double?,            // el oficial, traído del portal
    val credAprobados: Int,
    val credEnCurso: Int,
    val credTotal: Int,
    val horasSemana: Double,
    val horasPorDia: List<Pair<Int, Double>>, // (día 1–7, horas); lunes a sábado siempre, domingo solo si hay
    val letras: List<Pair<String, Int>>,      // A, B, C, D, F → cuántas materias
    val letrasDesdeNota: Boolean,             // true si alguna letra se dedujo de la nota
    val periodos: List<PeriodoStats>,
    val mejores: List<Pair<String, Double>>,  // materia, nota
    val reforzar: List<Pair<String, Double>>,
    val periodosFaltan: Int,
    val graduacion: YearMonth?                // estimado: un período cada 4 meses
)

object CalculoEstadisticas {
    private val LETRAS = listOf("A", "B", "C", "D", "F")
    private const val MESES_POR_PERIODO = 4L

    /** "B+" → "B"; sin letra, se deduce de la nota (90 A, 80 B, 70 C, 60 D, menos F). */
    fun letraBase(letra: String?, nota: Double?): String? {
        val l = letra?.trim()?.uppercase()?.firstOrNull()?.toString()
        if (l != null && l in LETRAS) return l
        if (l != null) return null // W, R, I…: retiradas o incompletas no cuentan
        return when {
            nota == null -> null
            nota >= 90 -> "A"
            nota >= 80 -> "B"
            nota >= 70 -> "C"
            nota >= 60 -> "D"
            else -> "F"
        }
    }

    private fun minutos(h: String): Int =
        h.take(5).split(":").let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

    fun calcular(
        materias: List<Materia>,
        estados: Map<String, String>,     // materiaId → pendiente | en_curso | aprobada
        notas: Map<String, Progreso>,     // materiaId → progreso con nota/letra
        indice: Double?,
        clases: List<Clase>,
        hoy: LocalDate = LocalDate.now()
    ): Estadisticas {
        val estado = { m: Materia -> estados[m.id] ?: "pendiente" }
        val conNota = materias.mapNotNull { m -> notas[m.id]?.nota?.let { m to it } }

        val letrasContadas = mutableMapOf<String, Int>()
        var desdeNota = false
        materias.forEach { m ->
            val p = notas[m.id] ?: return@forEach
            val l = letraBase(p.letra, p.nota) ?: return@forEach
            if (p.letra == null) desdeNota = true
            letrasContadas[l] = (letrasContadas[l] ?: 0) + 1
        }

        val porDia = clases.groupBy { it.dia }.mapValues { (_, l) ->
            l.sumOf { (minutos(it.horaFin) - minutos(it.horaInicio)).coerceAtLeast(0) } / 60.0
        }
        val dias = (1..6) + (if ((porDia[7] ?: 0.0) > 0) listOf(7) else emptyList())

        val periodos = materias.groupBy { it.cuatrimestre }.toSortedMap().map { (n, l) ->
            PeriodoStats(
                numero = n,
                aprobados = l.filter { estado(it) == "aprobada" }.sumOf { it.creditos },
                enCurso = l.filter { estado(it) == "en_curso" }.sumOf { it.creditos },
                total = l.sumOf { it.creditos }
            )
        }
        val faltan = materias.filter { estado(it) != "aprobada" }.map { it.cuatrimestre }.distinct().size

        val ordenadas = conNota.sortedByDescending { it.second }
        return Estadisticas(
            promedio = conNota.takeIf { it.isNotEmpty() }?.map { it.second }?.average(),
            materiasConNota = conNota.size,
            indice = indice,
            credAprobados = materias.filter { estado(it) == "aprobada" }.sumOf { it.creditos },
            credEnCurso = materias.filter { estado(it) == "en_curso" }.sumOf { it.creditos },
            credTotal = materias.sumOf { it.creditos },
            horasSemana = porDia.values.sum(),
            horasPorDia = dias.map { it to (porDia[it] ?: 0.0) },
            letras = LETRAS.map { it to (letrasContadas[it] ?: 0) },
            letrasDesdeNota = desdeNota,
            periodos = periodos,
            mejores = ordenadas.take(3).map { it.first.nombre to it.second },
            reforzar = ordenadas.reversed().filter { it.second < 80 }.take(3).map { it.first.nombre to it.second },
            periodosFaltan = faltan,
            graduacion = if (faltan > 0 && materias.isNotEmpty()) YearMonth.from(hoy).plusMonths(MESES_POR_PERIODO * faltan) else null
        )
    }
}
