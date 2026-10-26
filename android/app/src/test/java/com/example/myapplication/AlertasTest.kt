package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class AlertasTest {
    // Jueves 24 de septiembre de 2026, 12:00
    private val jueves = LocalDateTime.of(2026, 9, 24, 12, 0)

    @Test
    fun claseDeHoyMasTarde() {
        // Jueves 14:00, 15 min antes → hoy a las 13:45
        assertEquals(LocalDateTime.of(2026, 9, 24, 13, 45), Alertas.proximaAlerta(4, "14:00", 15, jueves))
    }

    @Test
    fun claseDeHoyQueYaPasoVaALaOtraSemana() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 7, 45), Alertas.proximaAlerta(4, "08:00:00", 15, jueves))
    }

    @Test
    fun claseDeOtroDia() {
        // Lunes 18:00 → próximo lunes 28 a las 17:30
        assertEquals(LocalDateTime.of(2026, 9, 28, 17, 30), Alertas.proximaAlerta(1, "18:00", 30, jueves))
    }

    @Test
    fun alertaQueCaeJustoAhoraPasaALaOtraSemana() {
        assertEquals(LocalDateTime.of(2026, 10, 1, 12, 0), Alertas.proximaAlerta(4, "12:15", 15, jueves))
    }

    @Test
    fun diaInvalido() {
        assertNull(Alertas.proximaAlerta(9, "08:00", 15, jueves))
    }

    @Test
    fun resumenDelDia() {
        assertEquals(LocalDateTime.of(2026, 9, 25, 7, 0), Alertas.proximoResumen(jueves))
        assertEquals(LocalDateTime.of(2026, 9, 24, 7, 0), Alertas.proximoResumen(LocalDateTime.of(2026, 9, 24, 6, 30)))

        val clases = listOf(
            Clase(materia = "Redes", dia = 4, horaInicio = "14:00", horaFin = "16:00"),
            Clase(materia = "Cálculo", dia = 4, horaInicio = "08:00", horaFin = "10:00"),
            Clase(materia = "Inglés", dia = 3, horaInicio = "14:00", horaFin = "17:00")
        )
        assertEquals("Hoy tienes 2 clases. La primera: Cálculo a las 8:00 am.", Alertas.textoResumen(clases, 4))
        assertEquals("Hoy tienes Inglés a las 2:00 pm.", Alertas.textoResumen(clases, 3))
        assertNull(Alertas.textoResumen(clases, 6))
    }
}
