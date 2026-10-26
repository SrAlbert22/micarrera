package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ContrasenasTest {
    @Test
    fun reglasLocales() {
        assertNotNull(Contrasenas.problemaLocal("2026"))                 // muy corta
        assertNotNull(Contrasenas.problemaLocal("20262026"))             // solo números
        assertNotNull(Contrasenas.problemaLocal("aaaaaaaa"))             // un solo carácter
        assertNotNull(Contrasenas.problemaLocal("Password123"))          // de las más comunes
        assertNotNull(Contrasenas.problemaLocal("MiCarrera2026!"))       // nombre de la app
        assertNotNull(Contrasenas.problemaLocal("maria.rod99x", "maria.rod@gmail.com")) // su correo
        assertNull(Contrasenas.problemaLocal("Cangrejo-Azul-47"))
        assertNull(Contrasenas.problemaLocal("tres perros verdes", "maria@gmail.com"))
    }

    @Test
    fun hashYRespuesta() {
        // SHA-1 conocido de "password"
        assertEquals("5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8", Contrasenas.sha1("password"))
        val cuerpo = "003D68EB55068C33ACE09247EE4C639306B:3\r\n1E4C9B93F3F0682250B6CF8331B7EE68FD8:9659365\r\nFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF:0"
        assertEquals(9659365, Contrasenas.vecesEnRespuesta("1E4C9B93F3F0682250B6CF8331B7EE68FD8", cuerpo))
        assertEquals(0, Contrasenas.vecesEnRespuesta("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", cuerpo)) // relleno
        assertEquals(0, Contrasenas.vecesEnRespuesta("ABC", cuerpo))
    }
}
