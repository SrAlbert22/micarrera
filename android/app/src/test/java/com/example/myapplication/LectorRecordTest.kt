package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LectorRecordTest {

    private fun List<MateriaPortal>.de(codigo: String) = first { it.codigo == codigo }

    @Test
    fun tablaConTabs() {
        val texto = """
            Instituto Tecnológico de las Américas (ITLA) 2024
            Récord de calificaciones
            Código	Asignatura	Créditos	Nota	Literal
            Período 2023-1
            TCI-101	Introducción a la Ciberseguridad	4	87	B
            MAT-101	Matemática Básica	4	65	F
            ESP-101	Lengua Española I	3	92	A
            Período 2023-2
            MAT-101	Matemática Básica	4	78	C
            TCI-102	Lenguaje C	4	W
            TCI-201	Redes I	4	En curso
            Total créditos aprobados: 11
            Índice acumulado: 3.10
        """.trimIndent()

        val r = LectorRecord.leer(texto)
        assertEquals(6, r.materias.size)
        assertEquals(3.10, r.indice!!, 0.001)

        r.materias.de("TCI-101").let {
            assertEquals(87.0, it.nota!!, 0.0); assertEquals("B", it.letra); assertEquals("aprobada", it.estado)
            assertEquals("Introducción a la Ciberseguridad", it.nombre)
        }
        assertEquals("reprobada", r.materias.filter { it.codigo == "MAT-101" }[0].estado)
        assertEquals("aprobada", r.materias.filter { it.codigo == "MAT-101" }[1].estado)
        r.materias.de("TCI-102").let { assertEquals("retirada", it.estado); assertEquals("Lenguaje C", it.nombre) }
        r.materias.de("TCI-201").let { assertEquals("en_curso", it.estado); assertNull(it.nota) }
    }

    @Test
    fun cadaDatoEnSuRenglon() {
        val texto = """
            ISC 105
            Programación I
            Créditos: 4
            90
            A
            ISC 106
            Base de Datos
            Créditos: 4
            Inscrita
            Índice del período 3.5
            Índice general acumulado 3,25
        """.trimIndent()

        val r = LectorRecord.leer(texto)
        assertEquals(2, r.materias.size)
        r.materias.de("ISC-105").let { assertEquals(90.0, it.nota!!, 0.0); assertEquals("A", it.letra); assertEquals("aprobada", it.estado) }
        assertEquals("en_curso", r.materias.de("ISC-106").estado)
        assertEquals(3.25, r.indice!!, 0.001)
    }

    @Test
    fun tablaConEspaciosSinLetra() {
        val texto = """
            MAT101   Cálculo I       5   72.5
            FIS101   Física I        5   61
        """.trimIndent()

        val r = LectorRecord.leer(texto)
        assertEquals("aprobada", r.materias.de("MAT-101").estado)
        assertEquals(72.5, r.materias.de("MAT-101").nota!!, 0.0)
        assertEquals("reprobada", r.materias.de("FIS-101").estado)
        assertNull(r.indice)
    }

    @Test
    fun paginaDelPensumNoEsRecord() {
        val texto = """
            Pensum Desarrollo de Software
            TDS-101	Fundamentos de Programación	4
            TDS-102	Programación I	4	Prerrequisito: TDS-101
        """.trimIndent()

        assertTrue(LectorRecord.leer(texto).materias.isEmpty())
    }
}
