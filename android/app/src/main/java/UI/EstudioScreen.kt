package com.example.myapplication.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Alertas
import com.example.myapplication.EstadoAula
import com.example.myapplication.Posicion
import com.example.myapplication.Repo
import com.example.myapplication.Tarea
import com.example.myapplication.Universidad
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Estudio (igual que Estudio.tsx en la web): modo concentración, reto de la semana, pendientes y aula virtual.

private val ES = Locale("es", "DO")

/** Fecha de entrega en la hora del teléfono (o null). */
private fun fechaLocal(iso: String?): LocalDateTime? =
    iso?.let { runCatching { OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() }

fun cuandoEntrega(iso: String?): String {
    val d = fechaLocal(iso) ?: return "Sin fecha"
    val hora = Alertas.horaBonita("%02d:%02d".format(d.hour, d.minute))
    val hoy = LocalDate.now()
    return when (d.toLocalDate()) {
        hoy -> "Hoy $hora"
        hoy.plusDays(1) -> "Mañana $hora"
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)) + " · $hora"
    }
}

@Composable
fun EstudioScreen(universidad: Universidad?) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var tareas by remember { mutableStateOf<List<Tarea>>(emptyList()) }
    var ranking by remember { mutableStateOf<List<Posicion>>(emptyList()) }
    var aula by remember { mutableStateOf<EstadoAula?>(null) }
    var materias by remember { mutableStateOf<List<String>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var editando by remember { mutableStateOf<Tarea?>(null) }
    var nueva by remember { mutableStateOf(false) }
    var viendo by remember { mutableStateOf<Tarea?>(null) }
    var conectando by remember { mutableStateOf(false) }
    var verHechas by remember { mutableStateOf(false) }

    suspend fun recargar() {
        runCatching { Repo.tareas() }.onSuccess { tareas = it; Alertas.guardarPendientes(context, it) }
        runCatching { ranking = Repo.ranking() }
        runCatching { aula = Repo.estadoAula() }
    }
    LaunchedEffect(Unit) {
        recargar()
        runCatching { materias = Repo.horario().map { it.materia }.distinct().sorted() }
        cargando = false
    }
    fun marcar(t: Tarea) {
        tareas = tareas.map { if (it.id == t.id) it.copy(hecha = !it.hecha) else it }
        Alertas.guardarPendientes(context, tareas)
        scope.launch { runCatching { Repo.marcarTarea(t.id, !t.hecha) } }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        TituloPantalla("Estudio") {
            BotonPastilla(
                texto = aula?.let { if (it.estado == "error") "Aula: revisar" else "Aula virtual ✓" } ?: "Conectar aula",
                onClick = { conectando = true }, destacado = aula == null,
                punto = aula?.let { if (it.estado == "error") colores.rojo else colores.verde }
            )
        }
        if (cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val ahora = LocalDateTime.now()
        val finHoy = LocalDate.now().atTime(23, 59, 59)
        val pendientes = tareas.filter { !it.hecha }
        fun f(t: Tarea) = fechaLocal(t.entrega)
        val grupos = listOf(
            Triple("Atrasadas", pendientes.filter { f(it)?.isBefore(ahora) == true }, colores.rojo),
            Triple("Hoy", pendientes.filter { f(it)?.let { d -> !d.isBefore(ahora) && !d.isAfter(finHoy) } == true }, colores.ambar),
            Triple("Esta semana", pendientes.filter { f(it)?.let { d -> d.isAfter(finHoy) && !d.isAfter(ahora.plusDays(7)) } == true }, colores.acento),
            Triple("Más adelante", pendientes.filter { f(it)?.isAfter(ahora.plusDays(7)) ?: true }, colores.textoSuave),
        )
        val hechas = tareas.filter { it.hecha }

        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Concentracion(materias) { scope.launch { runCatching { ranking = Repo.ranking() } } } }
            item { Reto(ranking) }
            if (aula == null) item {
                TarjetaMC(Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.55f), fondo = Lavanda.copy(alpha = 0.35f), onClick = { conectando = true }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🎓", fontSize = 30.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Conecta tu aula virtual", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Tus tareas llegan solas, con su explicación, y te aviso antes de que venzan.", color = TextoSuave, fontSize = 13.sp)
                        }
                        Text("›", color = TextoSuave, fontSize = 22.sp)
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Seccion("Pendientes", Modifier.weight(1f).padding(start = 4.dp))
                    BotonPastilla("+ Agregar", onClick = { nueva = true })
                }
            }
            if (pendientes.isEmpty()) item {
                TarjetaMC(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 36.sp)
                        Text("¡Nada pendiente!", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Agrega tus tareas o conecta tu aula virtual para que lleguen solas.", color = TextoSuave, fontSize = 14.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            grupos.filter { it.second.isNotEmpty() }.forEach { (titulo, lista, color) ->
                item {
                    Column {
                        Text("$titulo · ${lista.size}", color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
                        TarjetaMC(Modifier.fillMaxWidth()) {
                            lista.forEachIndexed { i, t ->
                                if (i > 0) HorizontalDivider(color = colores.borde)
                                FilaTarea(t, onMarcar = { marcar(t) }, onAbrir = { viendo = t })
                            }
                        }
                    }
                }
            }
            if (hechas.isNotEmpty()) item {
                TextButton(onClick = { verHechas = !verHechas }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (verHechas) "Ocultar las hechas" else "Ver las hechas (${hechas.size})", color = Indigo)
                }
            }
            if (verHechas && hechas.isNotEmpty()) item {
                TarjetaMC(Modifier.fillMaxWidth()) {
                    hechas.forEachIndexed { i, t ->
                        if (i > 0) HorizontalDivider(color = colores.borde)
                        FilaTarea(t, onMarcar = { marcar(t) }, onAbrir = { viendo = t })
                    }
                }
            }
        }
    }

    viendo?.let { t ->
        DetalleTarea(t, onCerrar = { viendo = null }, onEditar = { editando = t; viendo = null },
            onBorrar = { scope.launch { runCatching { Repo.borrarTarea(t.id) }; viendo = null; recargar() } },
            onMarcar = { marcar(t); viendo = null })
    }
    if (nueva || editando != null) DialogoTarea(editando, materias) { cambio ->
        nueva = false; editando = null
        if (cambio) scope.launch { recargar() }
    }
    if (conectando) DialogoAula(universidad, aula) { conectando = false; scope.launch { recargar() } }
}

@Composable
private fun FilaTarea(t: Tarea, onMarcar: () -> Unit, onAbrir: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onAbrir).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(28.dp).clip(CircleShape)
                .background(if (t.hecha) VerdeOk else Color.Transparent)
                .border(2.dp, if (t.hecha) VerdeOk else Indigo, CircleShape)
                .clickable(onClick = onMarcar),
            contentAlignment = Alignment.Center
        ) { if (t.hecha) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.titulo, color = if (t.hecha) TextoSuave else AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                textDecoration = if (t.hecha) TextDecoration.LineThrough else null, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text((if (t.origen == "aula") "🎓 " else "") + listOfNotNull(t.materia, cuandoEntrega(t.entrega)).joinToString(" · "),
                color = TextoSuave, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetalleTarea(t: Tarea, onCerrar: () -> Unit, onEditar: () -> Unit, onBorrar: () -> Unit, onMarcar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = {
            Column {
                Text((if (t.origen == "aula") "🎓 Del aula virtual" else "✏️ Agregada por ti") + (t.materia?.let { " · $it" } ?: ""),
                    color = TextoSuave, fontSize = 13.sp)
                Text(t.titulo, color = AzulNoche, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("⏰ ${cuandoEntrega(t.entrega)}", color = AmbarCurso, fontWeight = FontWeight.SemiBold)
                Text(t.detalle ?: "Sin explicación.", color = if (t.detalle != null) AzulNoche else TextoSuave, fontSize = 15.sp, lineHeight = 22.sp)
            }
        },
        confirmButton = { Button(onClick = onMarcar, shape = RoundedCornerShape(14.dp)) { Text(if (t.hecha) "Marcar pendiente" else "✓ Hecha") } },
        dismissButton = {
            if (t.origen == "manual") Row {
                TextButton(onClick = onBorrar) { Text("Borrar", color = colores.rojo) }
                TextButton(onClick = onEditar) { Text("Editar", color = Indigo) }
            }
        }
    )
}

@Composable
private fun DialogoTarea(t: Tarea?, materias: List<String>, onCerrar: (cambio: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var titulo by remember { mutableStateOf(t?.titulo.orEmpty()) }
    var materia by remember { mutableStateOf(t?.materia.orEmpty()) }
    var entrega by remember { mutableStateOf(fechaLocal(t?.entrega)) }
    var detalle by remember { mutableStateOf(t?.detalle.orEmpty()) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun elegirFecha() {
        val base = entrega ?: LocalDate.now().plusDays(1).atTime(23, 59)
        DatePickerDialog(context, { _, a, m, d ->
            TimePickerDialog(context, { _, h, min -> entrega = LocalDateTime.of(a, m + 1, d, h, min) }, base.hour, base.minute, false).show()
        }, base.year, base.monthValue - 1, base.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = { if (!guardando) onCerrar(false) }, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text(if (t != null) "Editar tarea" else "Nueva tarea", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titulo, { if (it.length <= 200) titulo = it }, label = { Text("¿Qué hay que hacer?") },
                    placeholder = { Text("Ej. Práctica 3 de redes") }, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(materia, { if (it.length <= 120) materia = it }, label = { Text("Materia") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                if (materias.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(materias) { m ->
                        FilterChip(selected = materia == m, onClick = { materia = m }, label = { Text(m, fontSize = 12.sp, maxLines = 1) })
                    }
                }
                OutlinedButton(onClick = ::elegirFecha, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(entrega?.let { "⏰ " + it.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)) + " · " + Alertas.horaBonita("%02d:%02d".format(it.hour, it.minute)) }
                        ?: "⏰ Elegir fecha de entrega", color = AzulNoche)
                }
                if (entrega != null) TextButton(onClick = { entrega = null }) { Text("Quitar fecha", color = TextoSuave) }
                OutlinedTextField(detalle, { if (it.length <= 5000) detalle = it }, label = { Text("Explicación o notas") }, minLines = 3,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(enabled = titulo.isNotBlank() && !guardando, shape = RoundedCornerShape(14.dp), onClick = {
                scope.launch {
                    guardando = true
                    runCatching {
                        Repo.guardarTarea(titulo, materia, detalle, entrega?.atZone(ZoneId.systemDefault())?.toOffsetDateTime()?.toString(), t?.id)
                    }.onSuccess { onCerrar(true) }.onFailure {
                        error = if (it.message.orEmpty().contains("límite")) "Llegaste al límite de tareas de hoy." else "No se pudo guardar."
                    }
                    guardando = false
                }
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { onCerrar(false) }) { Text("Cancelar", color = TextoSuave) } }
    )
}

// ---------- Modo concentración (Pomodoro) ----------

private val MODOS = listOf(25 to 5, 50 to 10)

/** El temporizador se guarda en el teléfono: si cierras la app, sigue contando y te avisa al terminar. */
private class RelojEstudio(c: Context) {
    private val p = c.getSharedPreferences("estudio", Context.MODE_PRIVATE)
    var modo: Int get() = p.getInt("modo", 0); set(v) = p.edit().putInt("modo", v).apply()
    var fase: String get() = p.getString("fase", "foco") ?: "foco"; set(v) = p.edit().putString("fase", v).apply()
    var fin: Long get() = p.getLong("fin", 0L); set(v) = p.edit().putLong("fin", v).apply()      // 0 = detenido
    var pausa: Long get() = p.getLong("pausa", -1L); set(v) = p.edit().putLong("pausa", v).apply() // -1 = sin pausa
    var materia: String get() = p.getString("materia", "") ?: ""; set(v) = p.edit().putString("materia", v).apply()
}

@Composable
private fun Concentracion(materias: List<String>, onTerminada: () -> Unit) {
    val context = LocalContext.current
    val reloj = remember { RelojEstudio(context) }
    val scope = rememberCoroutineScope()
    var modo by remember { mutableIntStateOf(reloj.modo) }
    var fase by remember { mutableStateOf(reloj.fase) }
    var fin by remember { mutableLongStateOf(reloj.fin) }
    var pausa by remember { mutableLongStateOf(reloj.pausa) }
    var materia by remember { mutableStateOf(reloj.materia) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    val (foco, descanso) = MODOS[modo]
    val total = (if (fase == "foco") foco else descanso) * 60_000L
    val corriendo = fin > 0
    val falta = if (corriendo) maxOf(0L, fin - ahora) else if (pausa >= 0) pausa else total

    fun guardar() { reloj.modo = modo; reloj.fase = fase; reloj.fin = fin; reloj.pausa = pausa; reloj.materia = materia }

    LaunchedEffect(corriendo) { while (corriendo) { ahora = System.currentTimeMillis(); delay(250) } }
    LaunchedEffect(falta, corriendo) {
        if (!corriendo || falta > 0) return@LaunchedEffect
        if (fase == "foco") {
            scope.launch { runCatching { Repo.guardarSesion(foco, materia.ifBlank { null }) }; onTerminada() }
            mensaje = "¡Bien hecho! 🎉 $foco minutos de concentración. Toma un descanso."
            fase = "descanso"; fin = System.currentTimeMillis() + descanso * 60_000L
            Alertas.avisarEn(context, descanso * 60_000L, "Se acabó el descanso ☕", "¿Otra ronda de concentración? 💪")
        } else {
            fase = "foco"; fin = 0; pausa = -1; mensaje = "Descanso terminado. ¿Otra ronda? 💪"
        }
        guardar()
    }

    fun empezar() {
        mensaje = null
        val ms = if (pausa >= 0) pausa else total
        fin = System.currentTimeMillis() + ms; pausa = -1; ahora = System.currentTimeMillis()
        if (fase == "foco") Alertas.avisarEn(context, ms, "¡Terminaste! 🎉", "Toma un descanso de $descanso minutos.")
        else Alertas.avisarEn(context, ms, "Se acabó el descanso ☕", "¿Otra ronda de concentración? 💪")
        guardar()
    }
    fun pausar() { pausa = falta; fin = 0; Alertas.cancelarAviso(context); guardar() }
    fun reiniciar() { fin = 0; pausa = -1; fase = "foco"; Alertas.cancelarAviso(context); guardar() }

    TarjetaMC(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("🧘 Modo concentración", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                if (!corriendo && pausa < 0) MODOS.forEachIndexed { i, (f, d) ->
                    FilterChip(selected = modo == i, onClick = { modo = i; fase = "foco"; guardar() }, label = { Text("$f/$d") },
                        modifier = Modifier.padding(start = 6.dp))
                }
            }
            val acento = if (fase == "foco") colores.acento else colores.verde
            val pista = colores.tarjetaAlta
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(210.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    val grosor = 12.dp.toPx()
                    val lado = size.minDimension - grosor
                    val esquina = Offset(grosor / 2, grosor / 2)
                    drawArc(pista, 0f, 360f, false, esquina, Size(lado, lado), style = Stroke(grosor))
                    drawArc(acento, -90f, 360f * (1f - falta.toFloat() / total), false, esquina, Size(lado, lado), style = Stroke(grosor, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val s = ((falta + 999) / 1000).toInt()
                    Text("%02d:%02d".format(s / 60, s % 60), color = AzulNoche, fontSize = 42.sp, fontWeight = FontWeight.ExtraBold, fontFamily = FontFamily.Monospace)
                    Text(if (fase == "foco") "Concentración" else "Descanso ☕", color = TextoSuave, fontSize = 14.sp)
                }
            }
            if (!corriendo && fase == "foco" && materias.isNotEmpty()) Box {
                OutlinedButton(onClick = { menu = true }, shape = RoundedCornerShape(14.dp)) {
                    Text(materia.ifBlank { "¿Qué vas a estudiar? (opcional)" } + "  ▾", color = AzulNoche, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Sin materia") }, onClick = { materia = ""; menu = false; guardar() })
                    materias.forEach { m -> DropdownMenuItem(text = { Text(m) }, onClick = { materia = m; menu = false; guardar() }) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (corriendo) Button(onClick = ::pausar, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp)) { Text("Pausar", fontSize = 16.sp) }
                else Button(onClick = ::empezar, shape = RoundedCornerShape(16.dp), modifier = Modifier.height(50.dp)) {
                    Text(if (pausa >= 0) "Seguir" else if (fase == "foco") "Empezar" else "Seguir descanso", fontSize = 16.sp)
                }
                if (corriendo || pausa >= 0 || fase == "descanso") OutlinedButton(onClick = ::reiniciar, shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colores.borde), modifier = Modifier.height(50.dp)) { Text("Reiniciar", color = AzulNoche) }
            }
            mensaje?.let { Text(it, color = VerdeOk, fontSize = 14.sp, textAlign = TextAlign.Center) }
            Text("Guarda el teléfono lejos. Te aviso cuando termine, aunque cierres la app.", color = TextoSuave, fontSize = 12.sp, textAlign = TextAlign.Center)
        }
    }
}

// ---------- Reto de la semana ----------

@Composable
private fun Reto(ranking: List<Posicion>) {
    val maximo = maxOf(60, ranking.maxOfOrNull { it.minutos } ?: 0)
    TarjetaMC(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text("🏆 Reto de la semana", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text("Minutos de concentración (lunes a domingo)", color = TextoSuave, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp))
            if (ranking.size <= 1) Text("Agrega amigos en Horario → Amigos para competir con ellos.", color = TextoSuave, fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 10.dp))
            ranking.take(8).forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(listOf("🥇", "🥈", "🥉").getOrElse(i) { "${i + 1}" }, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.soyYo) "Tú" else p.nombre ?: p.usuario?.let { "@$it" } ?: "Amigo",
                            color = if (p.soyYo) Indigo else AzulNoche, fontWeight = if (p.soyYo) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 14.sp)
                        LinearProgressIndicator(
                            progress = { p.minutos.toFloat() / maximo }, color = Indigo, trackColor = colores.tarjetaAlta,
                            strokeCap = StrokeCap.Round, modifier = Modifier.fillMaxWidth().height(8.dp).padding(top = 4.dp)
                        )
                    }
                    Text("${p.minutos} min", color = AzulNoche, fontSize = 13.sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.width(70.dp))
                }
            }
        }
    }
}

// ---------- Aula virtual ----------

@Composable
private fun DialogoAula(universidad: Universidad?, estado: EstadoAula?, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val valido = Regex("^https://\\S+$").matches(url.trim())

    fun hacer(accion: suspend () -> String) {
        scope.launch {
            trabajando = true; mensaje = null
            mensaje = runCatching { accion() }.fold({ true to it }, { false to ((it as? RestException)?.error ?: it.message ?: "Hubo un problema") })
            trabajando = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!trabajando) onCerrar() }, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text("🎓 Aula virtual", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                estado?.let { e ->
                    Column(Modifier.fillMaxWidth().background(colores.tarjetaAlta, RoundedCornerShape(16.dp)).padding(12.dp)) {
                        Text(if (e.estado == "error") "⚠️ " + (e.ultimoError ?: "Hubo un problema") else "✓ Conectada",
                            color = if (e.estado == "error") colores.rojo else VerdeOk, fontWeight = FontWeight.Bold)
                        fechaLocal(e.sincronizada)?.let {
                            Text("Última revisión: " + it.format(DateTimeFormatter.ofPattern("d MMM, h:mm a", ES)), color = TextoSuave, fontSize = 13.sp)
                        }
                        Text("Reviso tu aula cada 20 minutos y te aviso de tareas nuevas y de las que vencen en 24 horas.", color = TextoSuave, fontSize = 13.sp)
                    }
                }
                Text("Pega el enlace del calendario de tu aula virtual. Ese enlace solo sirve para ver tus fechas y tareas: no me das tu contraseña.",
                    color = AzulNoche, fontSize = 14.sp, lineHeight = 20.sp)
                Text("1. Entra a tu aula virtual.\n2. Ve a Calendario → Exportar calendario (en Moodle).\n3. Elige «Todos los cursos» y «Eventos recientes y próximos», toca «Obtener URL del calendario» y cópiala.",
                    color = AzulNoche, fontSize = 14.sp, lineHeight = 21.sp)
                universidad?.aulaUrl?.let { enlace ->
                    OutlinedButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(enlace))) } },
                        shape = RoundedCornerShape(14.dp)) { Text("Abrir el aula de ${universidad.siglas ?: "tu universidad"}", color = Indigo) }
                }
                Text("¿Usas Google Classroom o Canvas? Sirve la «dirección secreta en formato iCal» de tu calendario.", color = TextoSuave, fontSize = 13.sp)
                OutlinedTextField(url, { url = it.trim() }, placeholder = { Text("https://…/calendar/export_execute.php?…") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                mensaje?.let { (ok, t) -> Text(t, color = if (ok) VerdeOk else colores.rojo, fontSize = 14.sp) }
                if (estado != null) Row {
                    TextButton(enabled = !trabajando, onClick = { hacer { Repo.desconectarAula(); "Desconectada. Tus tareas se quedan." } }) {
                        Text("Desconectar", color = colores.rojo)
                    }
                    TextButton(enabled = !trabajando, onClick = { hacer { "Listo: ${Repo.sincronizarAula().total ?: 0} tareas revisadas." } }) {
                        Text("Actualizar ahora", color = Indigo)
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = valido && !trabajando, shape = RoundedCornerShape(14.dp),
                onClick = { hacer { "¡Conectada! Encontré ${Repo.conectarAula(url).total ?: 0} tareas y fechas." } }) {
                if (trabajando) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(if (estado != null) "Cambiar enlace" else "Conectar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar, enabled = !trabajando) { Text("Cerrar", color = TextoSuave) } }
    )
}
