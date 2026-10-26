package com.example.myapplication

import java.text.Collator
import java.util.Locale
import kotlin.math.roundToInt

/** Uno de los 6 tipos de intereses del modelo RIASEC de Holland, con nombres fáciles. */
data class TipoVocacional(val letra: Char, val nombre: String, val emoji: String, val descripcion: String)

data class Respuesta(val valor: Int, val texto: String, val emoji: String)

data class Recomendacion(val carrera: CarreraVocacional, val afinidad: Int)

/**
 * Test vocacional "¿Qué debo estudiar?" (lo mismo que vocacional.ts en la web).
 * Cada pregunta suma de 0 a 3 a un tipo; cada carrera tiene un tipo principal (pesa el doble) y uno secundario.
 */
object Vocacional {
    val TIPOS = listOf(
        TipoVocacional('R', "Hacedor", "🔧", "Te gusta trabajar con las manos, usar herramientas y ver resultados concretos."),
        TipoVocacional('I', "Investigador", "🔬", "Te gusta analizar, resolver problemas y entender cómo funcionan las cosas."),
        TipoVocacional('A', "Creativo", "🎨", "Te gusta crear, imaginar y expresarte con tu propio estilo."),
        TipoVocacional('S', "Social", "🤝", "Te gusta ayudar, enseñar y trabajar con personas."),
        TipoVocacional('E', "Emprendedor", "🚀", "Te gusta liderar, convencer y sacar adelante proyectos."),
        TipoVocacional('C', "Organizador", "📋", "Te gusta el orden, los datos y hacer las cosas con precisión."),
    )
    private val porLetra = TIPOS.associateBy { it.letra }
    fun tipo(letra: Char) = porLetra.getValue(letra)

    val RESPUESTAS = listOf(
        Respuesta(0, "No me gusta", "👎"),
        Respuesta(1, "Me da igual", "😐"),
        Respuesta(2, "Me gusta", "🙂"),
        Respuesta(3, "¡Me encanta!", "😍"),
    )

    /** Puntaje de cada tipo de 0 a 1 (lo que sumó / lo máximo que podía sumar). */
    fun puntajes(preguntas: List<PreguntaVocacional>, respuestas: Map<Int, Int>): Map<Char, Double> =
        TIPOS.associate { t ->
            val suyas = preguntas.filter { it.tipo.firstOrNull() == t.letra }
            val suma = suyas.sumOf { respuestas[it.id] ?: 0 }
            t.letra to if (suyas.isEmpty()) 0.0 else suma / (3.0 * suyas.size)
        }

    /** Los 6 tipos de mayor a menor (en empate, el orden RIASEC). */
    fun ranking(p: Map<Char, Double>): List<Char> =
        TIPOS.map { it.letra }.sortedByDescending { p[it] ?: 0.0 } // sortedBy es estable: respeta RIASEC en empates

    /** Afinidad 0–100 con una carrera: su tipo principal pesa el doble que el secundario. */
    fun afinidad(c: CarreraVocacional, p: Map<Char, Double>): Int {
        val t1 = p[c.tipos.getOrNull(0)] ?: 0.0
        val t2 = p[c.tipos.getOrNull(1)] ?: 0.0
        return (100 * (2 * t1 + t2) / 3).roundToInt()
    }

    /** Las [n] carreras que más encajan (en empate, gana la que empieza con tu tipo más fuerte). */
    fun recomendar(carreras: List<CarreraVocacional>, p: Map<Char, Double>, n: Int = 6): List<Recomendacion> {
        val primero = ranking(p).first()
        return carreras.map { Recomendacion(it, afinidad(it, p)) }
            .sortedWith(
                compareByDescending<Recomendacion> { it.afinidad }
                    .thenByDescending { it.carrera.tipos.firstOrNull() == primero }
                    .thenBy(Collator.getInstance(Locale.forLanguageTag("es"))) { it.carrera.nombre }
            )
            .take(n)
    }

    /** Universidades que ofrecen más de tus carreras recomendadas: (siglas, cuántas). */
    fun universidadesTop(recomendadas: List<Recomendacion>, n: Int = 3): List<Pair<String, Int>> =
        recomendadas.flatMap { it.carrera.universidades }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(n).map { it.key to it.value }
}
