package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AmigosTest {
    private fun c(materia: String, dia: Int, ini: String, fin: String, codigo: String? = null, grupo: String? = null) =
        Clase(materia = materia, dia = dia, horaInicio = ini, horaFin = fin, codigo = codigo, grupo = grupo)

    private val mias = listOf(
        c("Redes de Datos II", 4, "14:00", "18:00", "STI-202", "2"),
        c("Inglés Nivel 4-6", 3, "14:00", "17:00", "ING002", "10"),
        c("Cálculo Diferencial", 5, "14:00", "18:00", "MAT101", "1")
    )
    private val suyas = listOf(
        c("Redes de datos II", 4, "14:00:00", "18:00:00", "STI202", "2"), // mismo código sin guion y hora con segundos
        c("Inglés Nivel 4-6", 3, "14:00", "17:00", "ING002", "11"),      // otro grupo: no es la misma clase
        c("Programación I", 1, "08:00", "10:00")
    )

    @Test
    fun clasesEnComun() {
        assertEquals(listOf("Redes de Datos II"), CalculoAmigos.clasesEnComun(mias, suyas).map { it.materia })
    }

    @Test
    fun mismaClasePorNombreSinCodigo() {
        assertTrue(CalculoAmigos.mismaClase(c("Ética", 2, "08:00", "10:00"), c("ética ", 2, "08:00", "10:00")))
        assertFalse(CalculoAmigos.mismaClase(c("Ética", 2, "08:00", "10:00"), c("Ética", 3, "08:00", "10:00")))
    }

    @Test
    fun horasLibresEnComun() {
        val libres = CalculoAmigos.horasLibresEnComun(mias, suyas)
        // Jueves (4): los dos ocupados de 14 a 18 → libres 7–14 y 18–22
        assertEquals(
            listOf(HuecoLibre(4, "07:00", "14:00"), HuecoLibre(4, "18:00", "22:00")),
            libres.filter { it.dia == 4 }
        )
        // Lunes (1): solo él tiene clase 8–10 → libres 10–22 (7–8 es menos de una hora... es exactamente 60: cuenta)
        assertEquals(
            listOf(HuecoLibre(1, "07:00", "08:00"), HuecoLibre(1, "10:00", "22:00")),
            libres.filter { it.dia == 1 }
        )
        // Martes (2): nadie tiene clase → no se muestra
        assertTrue(libres.none { it.dia == 2 })
    }

    @Test
    fun amigosEnClase() {
        val gabriel = Persona(id = "g", nombre = "Gabriel")
        val brayan = Persona(id = "b", nombre = "Brayan")
        val horarios = mapOf(gabriel to suyas, brayan to listOf(c("Cálculo Diferencial", 5, "14:00", "18:00", "MAT-101", "1")))
        assertEquals(listOf(gabriel), CalculoAmigos.amigosEnClase(mias[0], horarios))
        assertEquals(listOf(brayan), CalculoAmigos.amigosEnClase(mias[2], horarios))
    }

    @Test
    fun usuarioYNombres() {
        assertEquals("albert.s", CalculoAmigos.limpiarUsuario(" @Albert.S "))
        assertTrue(CalculoAmigos.FORMATO_USUARIO.matches("albert_s"))
        assertFalse(CalculoAmigos.FORMATO_USUARIO.matches("al"))
        assertFalse(CalculoAmigos.FORMATO_USUARIO.matches("albert silvestre"))
        assertEquals("AS", Persona(id = "1", nombre = "Albert Silvestre").iniciales)
        assertEquals("@gabo", Persona(id = "1", usuario = "gabo").nombreVisible)
    }

    @Test
    fun sugiereUsuarioDesdeElNombre() {
        assertEquals("maria.rodriguez", CalculoAmigos.sugerirUsuario("María  Rodríguez Pérez"))
        assertEquals("jose", CalculoAmigos.sugerirUsuario("José"))
        assertEquals("", CalculoAmigos.sugerirUsuario("Al"))
        assertEquals("ana.nunez", CalculoAmigos.sugerirUsuario("Ana Núñez!"))
    }
}
