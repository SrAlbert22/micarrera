package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.CalculoEstadisticas
import com.example.myapplication.DIAS
import com.example.myapplication.Estadisticas
import com.example.myapplication.PeriodoStats
import com.example.myapplication.Progreso
import com.example.myapplication.Repo
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private fun num(x: Double) = if (x % 1.0 == 0.0) x.toInt().toString() else "%.1f".format(x)

@Composable
fun EstadisticasScreen(carreraId: String) {
    var datos by remember { mutableStateOf<Estadisticas?>(null) }

    LaunchedEffect(carreraId) {
        val materias = runCatching { Repo.materias(carreraId) }.getOrDefault(emptyList())
        val progreso = runCatching { Repo.progreso() }.getOrDefault(emptyList())
        val indice = runCatching { Repo.perfil()?.indice }.getOrNull()
        val clases = runCatching { Repo.horario() }.getOrDefault(emptyList())
        datos = CalculoEstadisticas.calcular(
            materias = materias,
            estados = progreso.associate { it.materiaId to it.estado },
            notas = progreso.filter { it.nota != null || it.letra != null }.associateBy(Progreso::materiaId),
            indice = indice,
            clases = clases
        )
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        TituloPantalla("Estadísticas")
        val e = datos
        if (e == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Números principales
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Cifra(Modifier.weight(1f), "Promedio de notas", e.promedio?.let { num(Math.round(it * 10) / 10.0) } ?: "—",
                    if (e.materiasConNota > 0) "de 100 · ${e.materiasConNota} materias" else "Sin notas todavía")
                Cifra(Modifier.weight(1f), "Índice", e.indice?.let { "%.2f".format(it) } ?: "—",
                    if (e.indice != null) "de 4.00" else "Se trae del portal")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val pct = if (e.credTotal > 0) e.credAprobados * 100 / e.credTotal else 0
                Cifra(Modifier.weight(1f), "Créditos aprobados", "${e.credAprobados}", "de ${e.credTotal} · $pct %")
                Cifra(Modifier.weight(1f), "Clases por semana", "${num(e.horasSemana)} h",
                    if (e.horasSemana > 0) "en ${e.horasPorDia.count { it.second > 0 }} días" else "Sin horario todavía")
            }

            // Notas por letra
            TarjetaGrafica(
                "Tus notas",
                if (e.materiasConNota == 0 && e.letras.all { it.second == 0 }) "Importa tu récord del portal para ver tus notas."
                else "Cuántas materias tienes con cada letra" + if (e.letrasDesdeNota) " (algunas según tu nota)" else ""
            ) {
                if (e.letras.any { it.second > 0 }) {
                    Columnas(e.letras.map { (l, n) -> Triple(l, n.toDouble(), Indigo) }, { it.toInt().toString() })
                }
            }

            // Créditos por período
            if (e.periodos.isNotEmpty()) TarjetaGrafica("Créditos por período", "Aprobados y en curso de cada período") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                    Leyenda(colores.graficaAprobado, "Aprobados")
                    Leyenda(colores.graficaCurso, "En curso")
                    Leyenda(colores.tarjetaAlta, "Pendientes")
                }
                e.periodos.forEach { FilaPeriodo(it) }
            }

            // Horas por día
            if (e.horasSemana > 0) TarjetaGrafica("Horas de clase por día", "Total: ${num(e.horasSemana)} h a la semana") {
                val hoy = LocalDate.now().dayOfWeek.value
                Columnas(
                    e.horasPorDia.map { (d, h) -> Triple(DIAS[d - 1].take(2), h, if (d == hoy) Indigo else GrisPendiente) },
                    { if (it > 0) "${num(it)} h" else "" }
                )
                Text("Hoy en color", color = TextoSuave, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
            }

            // Mejores notas y a reforzar
            if (e.mejores.isNotEmpty()) TarjetaGrafica("Tus mejores notas", null) {
                e.mejores.forEachIndexed { i, (m, n) -> FilaNota("${i + 1}", m, n) }
            }
            if (e.reforzar.isNotEmpty()) TarjetaGrafica("Para reforzar", "Materias con menos de 80") {
                e.reforzar.forEach { (m, n) -> FilaNota("•", m, n) }
            }

            // Proyección
            e.graduacion?.let { g ->
                TarjetaGrafica("Proyección", null) {
                    val mes = g.format(DateTimeFormatter.ofPattern("MMMM 'de' yyyy", Locale.forLanguageTag("es-DO")))
                    Text(
                        if (e.periodosFaltan == 1) "Te falta 1 período." else "Te faltan ${e.periodosFaltan} períodos.",
                        color = AzulNoche, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                    )
                    Text("Si tomas un período completo cada cuatrimestre, terminarías cerca de $mes.",
                        color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun Cifra(modifier: Modifier, etiqueta: String, valor: String, detalle: String) {
    TarjetaMC(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(etiqueta, color = TextoSuave, fontSize = 13.sp)
            Text(valor, color = AzulNoche, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(detalle, color = TextoSuave, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TarjetaGrafica(titulo: String, subtitulo: String?, contenido: @Composable ColumnScope.() -> Unit) {
    TarjetaMC(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(titulo, color = AzulNoche, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            subtitulo?.let { Text(it, color = TextoSuave, fontSize = 13.sp) }
            Spacer(Modifier.height(14.dp))
            contenido()
        }
    }
}

/** Columnas de una sola serie: barra de máx. 24 dp, punta redondeada, valor encima, etiqueta debajo. */
@Composable
private fun Columnas(datos: List<Triple<String, Double, Color>>, formato: (Double) -> String, alto: Dp = 120.dp) {
    val max = datos.maxOfOrNull { it.second }?.takeIf { it > 0 } ?: 1.0
    Column {
        Row(Modifier.fillMaxWidth().height(alto + 20.dp), verticalAlignment = Alignment.Bottom) {
            datos.forEach { (etiqueta, valor, color) ->
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "$etiqueta: ${formato(valor).ifEmpty { "0" }}" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(formato(valor), color = AzulNoche, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.width(24.dp).height(alto * (valor / max).toFloat())
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)).background(color)
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(colores.borde)) // línea base
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            datos.forEach { (etiqueta, _, _) ->
                Text(etiqueta, color = TextoSuave, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Un período: barra apilada (aprobados · en curso · pendientes) con 2 dp de separación. */
@Composable
private fun FilaPeriodo(p: PeriodoStats) {
    val total = p.total.coerceAtLeast(1)
    val pendientes = (p.total - p.aprobados - p.enCurso).coerceAtLeast(0)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 5.dp)
            .semantics { contentDescription = "Período ${p.numero}: ${p.aprobados} aprobados, ${p.enCurso} en curso, de ${p.total} créditos" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("${p.numero}.º", color = TextoSuave, fontSize = 13.sp, modifier = Modifier.width(34.dp))
        Row(Modifier.weight(1f).height(14.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(p.aprobados to colores.graficaAprobado, p.enCurso to colores.graficaCurso, pendientes to colores.tarjetaAlta)
                .filter { it.first > 0 }
                .forEach { (n, c) ->
                    Box(Modifier.weight(n.toFloat() / total).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(c))
                }
        }
        Text("${p.aprobados}/${p.total}", color = AzulNoche, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
    }
}

@Composable
private fun Leyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape).border(1.dp, GrisPendiente, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(texto, color = TextoSuave, fontSize = 12.sp)
    }
}

@Composable
private fun FilaNota(marca: String, materia: String, nota: Double) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(marca, color = TextoSuave, fontSize = 14.sp, modifier = Modifier.width(22.dp))
        Text(materia, color = AzulNoche, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(num(nota), color = AzulNoche, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}
