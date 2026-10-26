package com.example.myapplication

/**
 * "Primeros pasos": lo mínimo para que MiCarrera te sirva (la mayoría se quedaba sin horario ni materias).
 * [ir] es la pestaña a la que lleva cada paso ("HORARIO" = subir foto o PDF del horario).
 */
data class Paso(val titulo: String, val detalle: String, val hecho: Boolean, val ir: String)

object PrimerosPasos {
    fun pasos(materiasMarcadas: Int, clases: Int, tareasOAula: Boolean): List<Paso> = listOf(
        Paso("Elige tu carrera", "Ya está ✓", true, "PENSUM"),
        Paso("Marca las materias que ya aprobaste", "Así ves cuánto te falta para graduarte", materiasMarcadas > 0, "PENSUM"),
        Paso("Agrega tu horario", "Sube una foto o PDF y te avisamos antes de cada clase", clases > 0, "HORARIO"),
        Paso("Anota una tarea o conecta tu aula virtual", "Para que no se te pase ninguna entrega", tareasOAula, "ESTUDIO"),
    )

    /** Se muestra mientras falte algún paso (y la persona no la haya ocultado). */
    fun mostrar(pasos: List<Paso>, oculta: Boolean): Boolean = !oculta && pasos.any { !it.hecho }
}
