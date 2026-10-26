package com.example.myapplication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CorreoTest {
    @Test
    fun corrigeDominiosMalEscritos() {
        assertEquals("ana@gmail.com", correccionCorreo("ana@gamil.com"))
        assertEquals("ana@gmail.com", correccionCorreo(" Ana@Gmail.con "))
        assertEquals("luis@hotmail.com", correccionCorreo("luis@hotmial.com"))
    }

    @Test
    fun dejaTranquilosLosCorreosBuenos() {
        assertNull(correccionCorreo("ana@gmail.com"))
        assertNull(correccionCorreo("ana@itla.edu.do"))
        assertNull(correccionCorreo("sin-arroba"))
    }
}
