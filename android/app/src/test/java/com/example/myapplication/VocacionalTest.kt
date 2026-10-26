package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Test

class VocacionalTest {
    // 2 preguntas por tipo: ids 1..12 en orden R, I, A, S, E, C, R, I, …
    private val letras = "RIASEC"
    private val preguntas = (1..12).map { PreguntaVocacional(it, letras[(it - 1) % 6].toString(), "p$it") }
    private fun carrera(nombre: String, tipos: String, vararg unis: String) =
        CarreraVocacional(nombre.hashCode(), nombre, "🎓", tipos, "", unis.toList())

    /** Mismo valor para las 2 preguntas de cada tipo. */
    private fun responder(vararg valores: Pair<Char, Int>): Map<Int, Int> {
        val porTipo = valores.toMap()
        return preguntas.associate { it.id to (porTipo[it.tipo[0]] ?: 0) }
    }

    @Test
    fun puntajesVanDeCeroAUno() {
        val p = Vocacional.puntajes(preguntas, responder('I' to 3, 'A' to 2, 'R' to 1))
        assertEquals(1.0, p['I']!!, 1e-9)
        assertEquals(2 / 3.0, p['A']!!, 1e-9)
        assertEquals(1 / 3.0, p['R']!!, 1e-9)
        assertEquals(0.0, p['E']!!, 1e-9)
    }

    @Test
    fun rankingRespetaOrdenRiasecEnEmpates() {
        val p = Vocacional.puntajes(preguntas, responder('C' to 2, 'A' to 2, 'I' to 3))
        assertEquals(listOf('I', 'A', 'C', 'R', 'S', 'E'), Vocacional.ranking(p))
    }

    @Test
    fun afinidadPesaElDobleElTipoPrincipal() {
        val p = Vocacional.puntajes(preguntas, responder('I' to 3, 'S' to 0))
        assertEquals(67, Vocacional.afinidad(carrera("Medicina", "IS"), p))
        assertEquals(33, Vocacional.afinidad(carrera("Enfermería", "SI"), p))
    }

    @Test
    fun recomiendaLasQueMasEncajan() {
        val carreras = listOf(
            carrera("Software", "IA", "ITLA", "INTEC"),
            carrera("Derecho", "ES", "UASD"),
            carrera("Diseño", "AE", "UNAPEC"),
            carrera("Ciberseguridad", "IC", "ITLA"),
        )
        val p = Vocacional.puntajes(preguntas, responder('I' to 3, 'A' to 3, 'C' to 1))
        val r = Vocacional.recomendar(carreras, p, 3)
        assertEquals(listOf("Software", "Ciberseguridad", "Diseño"), r.map { it.carrera.nombre })
        assertEquals(100, r[0].afinidad)
        assertEquals(listOf("ITLA" to 2, "INTEC" to 1, "UNAPEC" to 1), Vocacional.universidadesTop(r))
    }

    @Test
    fun enEmpateGanaLaDeTuTipoMasFuerte() {
        // Software (IA) y Diseño (AI) dan lo mismo si I = A; gana la que empieza con I (primero en RIASEC).
        val carreras = listOf(carrera("Diseño", "AI"), carrera("Software", "IA"))
        val p = Vocacional.puntajes(preguntas, responder('I' to 2, 'A' to 2))
        assertEquals("Software", Vocacional.recomendar(carreras, p, 1).single().carrera.nombre)
    }
}
