package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimerosPasosTest {
    @Test
    fun cuentaNuevaSoloTieneLaCarrera() {
        val pasos = PrimerosPasos.pasos(materiasMarcadas = 0, clases = 0, tareasOAula = false)
        assertEquals(4, pasos.size)
        assertEquals(1, pasos.count { it.hecho })
        assertTrue(PrimerosPasos.mostrar(pasos, oculta = false))
    }

    @Test
    fun seMarcaCadaPaso() {
        val pasos = PrimerosPasos.pasos(materiasMarcadas = 3, clases = 0, tareasOAula = true)
        assertEquals(listOf(true, true, false, true), pasos.map { it.hecho })
    }

    @Test
    fun desapareceAlTerminarOOcultarla() {
        assertFalse(PrimerosPasos.mostrar(PrimerosPasos.pasos(5, 8, true), oculta = false))
        assertFalse(PrimerosPasos.mostrar(PrimerosPasos.pasos(0, 0, false), oculta = true))
    }
}
