package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class EstudioTest {
    private fun c(materia: String, dia: Int, ini: String, fin: String) = Clase(materia = materia, dia = dia, horaInicio = ini, horaFin = fin)
    private val clases = listOf(c("Redes", 1, "08:00:00", "10:00:00"), c("Cálculo", 1, "14:00", "16:00"), c("Inglés", 2, "08:00", "10:00"))

    @Test
    fun enClaseAhora() {
        val e = CalculoAmigos.estadoAhora(clases, 1, 9 * 60)
        assertFalse(e.libre)
        assertEquals("Redes", e.clase?.materia)
    }

    @Test
    fun libreHastaLaProxima() {
        val e = CalculoAmigos.estadoAhora(clases, 1, 11 * 60)
        assertTrue(e.libre)
        assertEquals("Cálculo", e.proxima?.materia)
        // Justo cuando termina la clase ya está libre
        assertTrue(CalculoAmigos.estadoAhora(clases, 1, 10 * 60).libre)
    }

    @Test
    fun libreTodoElDia() {
        val e = CalculoAmigos.estadoAhora(clases, 3, 9 * 60)
        assertTrue(e.libre)
        assertNull(e.proxima)
    }

    private val ahora = LocalDateTime.of(2026, 9, 25, 9, 0)
    private fun iso(d: LocalDateTime) = d.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString()
    private fun t(titulo: String, materia: String?, entrega: LocalDateTime?, hecha: Boolean = false) =
        Tarea(id = titulo, titulo = titulo, materia = materia, entrega = entrega?.let(::iso), hecha = hecha)

    @Test
    fun pendienteDeLaMateriaEnLaAlerta() {
        val tareas = listOf(
            t("Práctica 3", "Redes de Datos II", ahora.plusDays(1)),
            t("Cuestionario", "Redes de datos II", ahora.plusDays(4)),
            t("Ensayo", "Ética", ahora),
            t("Vieja", "Redes de Datos II", ahora.minusDays(1), hecha = true),
        )
        // Sin tildes ni mayúsculas, y la más próxima primero
        assertEquals("📌 Pendiente: Práctica 3 (vence mañana) y 1 más", Alertas.textoPendientes(tareas, "REDES DE DATOS II", ahora))
        assertEquals("📌 Pendiente: Ensayo (vence hoy)", Alertas.textoPendientes(tareas, "Etica", ahora))
        assertNull(Alertas.textoPendientes(tareas, "Cálculo", ahora))
    }

    @Test
    fun pendienteAtrasadoOSinFecha() {
        assertEquals("📌 Pendiente: Lab (atrasada)", Alertas.textoPendientes(listOf(t("Lab", "Física", ahora.minusDays(2))), "Física", ahora))
        assertEquals("📌 Pendiente: Leer", Alertas.textoPendientes(listOf(t("Leer", "Física", null)), "Física", ahora))
    }
}
