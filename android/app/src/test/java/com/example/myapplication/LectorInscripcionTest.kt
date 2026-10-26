package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Páginas de "inscripción / selección": una fila por materia con profesor, horario y salón. */
class LectorInscripcionTest {

    private fun List<Clase>.resumen() = map { "${it.dia} ${it.horaInicio}-${it.horaFin} ${it.materia}" }

    private val tabla = listOf(
        "Asignaturas inscritas - Período 2026-3",
        "Clave\tAsignatura\tSección\tCréditos\tProfesor\tHorario\tAula",
        "ING002\tInglés Nivel 4-6\t10\t0\tRomilio Cuevas D`oleo\tMI 2:00 PM - 5:00 PM\tAula 4 VILLA MELLA (SDN)",
        "STI202\tRedes de Datos II\t2\t4\tLuis Omar Decena Gomez\tJU 2:00 PM - 6:00 PM\tAula 1 VILLA MELLA (SDN)",
        "MAT101\tCálculo Diferencial\t1\t4\tPaola Caridad Portela\tVI 2:00 PM - 6:00 PM\tLab 3",
        "TCI110\tSeguridad de la Información\t3\t3\tAna Pérez\tLU 08:00 AM - 10:00 AM / MI 08:00 AM - 10:00 AM\tAula 12",
        "Total de créditos: 11"
    ).joinToString("\n")

    @Test
    fun tablaDeInscripcion() {
        val clases = LectorHorario.leer(tabla)
        assertEquals(
            listOf(
                "1 08:00-10:00 Seguridad de la Información",
                "3 08:00-10:00 Seguridad de la Información",
                "3 14:00-17:00 Inglés Nivel 4-6",
                "4 14:00-18:00 Redes de Datos II",
                "5 14:00-18:00 Cálculo Diferencial"
            ),
            clases.resumen()
        )
        clases.first { it.materia == "Redes de Datos II" }.let {
            assertEquals("STI-202", it.codigo)
            assertEquals("Luis Omar Decena Gomez", it.profesor)
            assertEquals("1 VILLA MELLA (SDN)", it.aula)
        }
    }

    @Test
    fun conEtiquetasEnCadaRenglon() {
        val texto = """
            Mis asignaturas
            ING002 - Inglés Nivel 4-6
            Grupo: 10
            Profesor: Romilio Cuevas D`oleo
            Horario: Miércoles 2:00 p.m. - 5:00 p.m.
            Salón: 4 VILLA MELLA
            STI202 - Redes de Datos II
            Grupo: 2
            Profesor: Luis Omar Decena Gomez
            Horario: Jueves 2:00 p.m. - 6:00 p.m.
            Salón: 1 VILLA MELLA
        """.trimIndent()
        val clases = LectorHorario.leer(texto)
        assertEquals(listOf("3 14:00-17:00 Inglés Nivel 4-6", "4 14:00-18:00 Redes de Datos II"), clases.resumen())
        clases.first().let {
            assertEquals("10", it.grupo)
            assertEquals("Romilio Cuevas D`oleo", it.profesor)
            assertEquals("4 VILLA MELLA", it.aula)
        }
    }

    @Test
    fun paginaGrandeNoSeTraba() {
        // Una página larga con mucho "ruido" (menús, textos) no debe tardar más de un segundo.
        val ruido = (1..400).joinToString("\n") { "Menú opción $it Lunes Martes Aula Profesor Sección $it" }
        val texto = ruido + "\n" + tabla.repeat(20)
        val inicio = System.currentTimeMillis()
        val clases = LectorHorario.leer(texto)
        val ms = System.currentTimeMillis() - inicio
        assertTrue("tardó $ms ms", ms < 1000)
        assertTrue(clases.isNotEmpty())
    }
}
