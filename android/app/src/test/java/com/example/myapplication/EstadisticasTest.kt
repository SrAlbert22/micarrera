package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class EstadisticasTest {
    private fun m(id: String, cuatri: Int, cred: Int = 4) =
        Materia(id = id, carreraId = "c", codigo = "COD-$id", nombre = "Materia $id", creditos = cred, cuatrimestre = cuatri)

    private fun p(id: String, nota: Double?, letra: String?) =
        Progreso(usuarioId = "u", materiaId = id, estado = "aprobada", nota = nota, letra = letra, origen = "portal")

    private val materias = listOf(m("1", 1), m("2", 1), m("3", 1, 3), m("4", 2), m("5", 2), m("6", 3))
    private val estados = mapOf("1" to "aprobada", "2" to "aprobada", "3" to "aprobada", "4" to "en_curso")
    private val notas = mapOf(
        "1" to p("1", 93.0, "A"),
        "2" to p("2", 85.0, "B+"),
        "3" to p("3", 65.0, null), // sin letra: se deduce D
        "4" to p("4", null, "W")   // retirada: no cuenta
    )
    private val clases = listOf(
        Clase(materia = "Redes", dia = 4, horaInicio = "14:00", horaFin = "18:00"),
        Clase(materia = "Inglés", dia = 3, horaInicio = "14:00", horaFin = "17:00"),
        Clase(materia = "Cálculo", dia = 3, horaInicio = "08:00", horaFin = "09:30")
    )

    private val e = CalculoEstadisticas.calcular(materias, estados, notas, 2.7, clases, LocalDate.of(2026, 9, 24))

    @Test
    fun promedioYCreditos() {
        assertEquals((93 + 85 + 65) / 3.0, e.promedio!!, 0.001)
        assertEquals(3, e.materiasConNota)
        assertEquals(11, e.credAprobados)
        assertEquals(4, e.credEnCurso)
        assertEquals(23, e.credTotal)
    }

    @Test
    fun letras() {
        assertEquals(listOf("A" to 1, "B" to 1, "C" to 0, "D" to 1, "F" to 0), e.letras)
        assertTrue(e.letrasDesdeNota)
        assertNull(CalculoEstadisticas.letraBase("W", null))
        assertEquals("F", CalculoEstadisticas.letraBase(null, 40.0))
    }

    @Test
    fun horas() {
        assertEquals(8.5, e.horasSemana, 0.001)
        assertEquals(4.5, e.horasPorDia.first { it.first == 3 }.second, 0.001)
        assertEquals(6, e.horasPorDia.size) // lunes a sábado; sin domingo
    }

    @Test
    fun periodosYGraduacion() {
        assertEquals(PeriodoStats(1, 11, 0, 11), e.periodos[0])
        assertEquals(PeriodoStats(2, 0, 4, 8), e.periodos[1])
        assertEquals(2, e.periodosFaltan) // períodos 2 y 3
        assertEquals(YearMonth.of(2027, 5), e.graduacion)
    }

    @Test
    fun mejoresYReforzar() {
        assertEquals(listOf("Materia 1", "Materia 2", "Materia 3"), e.mejores.map { it.first })
        assertEquals(listOf("Materia 3" to 65.0), e.reforzar)
    }

    @Test
    fun sinDatos() {
        val vacio = CalculoEstadisticas.calcular(emptyList(), emptyMap(), emptyMap(), null, emptyList())
        assertNull(vacio.promedio)
        assertNull(vacio.graduacion)
        assertEquals(0.0, vacio.horasSemana, 0.0)
    }
}
