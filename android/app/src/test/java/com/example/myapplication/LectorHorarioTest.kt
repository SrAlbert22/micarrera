package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LectorHorarioTest {

    private fun List<Clase>.resumen() = map { "${it.dia} ${it.horaInicio}-${it.horaFin} ${it.materia}" }

    @Test
    fun listaPorMateriaConVariosDias() {
        val texto = """
            Mi horario - Período 2024-3
            Código	Asignatura	Sección	Profesor	Días	Horario	Aula
            TCI-101	Introducción a la Ciberseguridad	01	Juan Pérez	LU/MI	08:00-10:00	A-101
            MAT-101	Matemática Básica	02	Ana Gómez	MA	6:00 pm - 8:00 pm	B-204
            ESP-101	Lengua Española I	01	Luis Díaz	VI	2:00-4:00	Lab 3
        """.trimIndent()

        val clases = LectorHorario.leer(texto)
        assertEquals(
            listOf(
                "1 08:00-10:00 Introducción a la Ciberseguridad",
                "2 18:00-20:00 Matemática Básica",
                "3 08:00-10:00 Introducción a la Ciberseguridad",
                "5 14:00-16:00 Lengua Española I"
            ),
            clases.resumen()
        )
        clases.first().let { assertEquals("A-101", it.aula); assertEquals("Juan Pérez", it.profesor) }
        assertEquals("3", clases.last().aula)
    }

    @Test
    fun diasConHorasDistintas() {
        val texto = "ISC-105\tProgramación I\tLunes 08:00-10:00  Jueves 10:00-12:00\tAula E-12"
        assertEquals(
            listOf("1 08:00-10:00 Programación I", "4 10:00-12:00 Programación I"),
            LectorHorario.leer(texto).resumen()
        )
    }

    @Test
    fun agrupadoPorDia() {
        val texto = """
            LUNES
            Redes I
            8:00 a.m. - 10:00 a.m.
            Aula: C-3
            Base de Datos
            10:00 a.m. - 12:00 p.m.
            MIÉRCOLES
            Redes I
            8:00 a.m. - 10:00 a.m.
        """.trimIndent()

        val clases = LectorHorario.leer(texto)
        assertEquals(
            listOf("1 08:00-10:00 Redes I", "1 10:00-12:00 Base de Datos", "3 08:00-10:00 Redes I"),
            clases.resumen()
        )
    }

    @Test
    fun cuadriculaPorHoras() {
        val texto = listOf(
            "Hora\tLunes\tMartes\tMiércoles\tJueves\tViernes",
            "08:00-09:00\tTCI-101 Intro Ciber\t\tTCI-101 Intro Ciber\t\t",
            "09:00-10:00\tTCI-101 Intro Ciber\t\tTCI-101 Intro Ciber\t\t",
            "10:00-11:00\t\tMAT-101 Matemática\t\t\tESP-101 Español"
        ).joinToString("\n")

        assertEquals(
            listOf(
                "1 08:00-10:00 Intro Ciber",
                "2 10:00-11:00 Matemática",
                "3 08:00-10:00 Intro Ciber",
                "5 10:00-11:00 Español"
            ),
            LectorHorario.leer(texto).resumen()
        )
    }

    @Test
    fun tablaConColumnaPorDia() {
        val texto = listOf(
            "Código\tAsignatura\tLunes\tMartes\tMiércoles\tJueves\tViernes\tSábado",
            "TCI-101\tIntro Ciber\t08:00-10:00\t\t08:00-10:00\t\t\t",
            "MAT-101\tMatemática\t\t18:00-20:00\t\t\t\t09:00-12:00"
        ).joinToString("\n")

        assertEquals(
            listOf(
                "1 08:00-10:00 Intro Ciber",
                "2 18:00-20:00 Matemática",
                "3 08:00-10:00 Intro Ciber",
                "6 09:00-12:00 Matemática"
            ),
            LectorHorario.leer(texto).resumen()
        )
    }

    @Test
    fun recordNoEsHorario() {
        val texto = """
            TCI-101	Introducción a la Ciberseguridad	4	87	B
            MAT-101	Matemática Básica	4	65	F
            Índice acumulado: 3.10
        """.trimIndent()
        assertTrue(LectorHorario.leer(texto).isEmpty())
    }
}
