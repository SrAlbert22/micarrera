package com.example.myapplication.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Alertas
import com.example.myapplication.CalculoAmigos
import com.example.myapplication.Persona
import com.example.myapplication.Paso
import com.example.myapplication.PrimerosPasos
import com.example.myapplication.Clase
import com.example.myapplication.DIAS
import com.example.myapplication.Repo
import com.example.myapplication.archivoParaIA
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

/** "14:00" o "14:00:00" → minutos desde medianoche. */
private fun minutos(h: String): Int = h.take(5).split(":").let { (it[0].toIntOrNull() ?: 0) * 60 + (it.getOrNull(1)?.toIntOrNull() ?: 0) }

/** "14:00" → "2:00 pm" */
private fun hora12(h: String): String {
    val m = minutos(h)
    val hh = m / 60
    val sufijo = if (hh < 12) "am" else "pm"
    val h12 = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
    return "%d:%02d %s".format(h12, m % 60, sufijo)
}

private fun enCuanto(min: Int): String = when {
    min < 60 -> "en $min min"
    min % 60 == 0 -> "en ${min / 60} h"
    else -> "en ${min / 60} h ${min % 60} min"
}

@Composable
fun HorarioScreen(onConectarPortal: () -> Unit, onAmigos: () -> Unit, onIrA: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var clases by remember { mutableStateOf<List<Clase>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var leyendo by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var propuestas by remember { mutableStateOf<List<Clase>?>(null) }
    var editando by remember { mutableStateOf<Clase?>(null) }
    var agregando by remember { mutableStateOf(false) }
    var apunteDe by remember { mutableStateOf<Clase?>(null) }
    var ahora by remember { mutableStateOf(LocalDateTime.now()) }
    val lista = rememberLazyListState()

    suspend fun recargar() {
        runCatching { Repo.horario() }.onSuccess {
            clases = it
            Alertas.programar(context, it) // las alertas siguen al horario
        }
    }
    LaunchedEffect(Unit) { recargar(); cargando = false }
    // Amigos que comparten su horario: para mostrar quién va contigo a cada clase.
    var horariosAmigos by remember { mutableStateOf<Map<Persona, List<Clase>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        runCatching {
            val amigos = Repo.misAmigos().filter { it.estado == "aceptada" }
            val porId = Repo.horariosDe(amigos.map { it.id })
            horariosAmigos = amigos.associateWith { porId[it.id].orEmpty() }.filterValues { it.isNotEmpty() }
        }
    }
    LaunchedEffect(Unit) { while (true) { delay(60_000); ahora = LocalDateTime.now() } }

    // Primeros pasos: materias marcadas y tareas/aula (el horario ya lo tenemos en "clases").
    val prefsPasos = remember { context.getSharedPreferences("primeros_pasos", android.content.Context.MODE_PRIVATE) }
    var pasosOcultos by remember { mutableStateOf(prefsPasos.getBoolean("oculta", false)) }
    var materiasMarcadas by remember { mutableStateOf<Int?>(null) }
    var tareasOAula by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (pasosOcultos) return@LaunchedEffect
        runCatching {
            materiasMarcadas = Repo.progreso().size
            tareasOAula = Repo.tareas().isNotEmpty() || Repo.estadoAula() != null
        }
    }

    val hoy = ahora.dayOfWeek.value
    val porDia = clases.groupBy { it.dia }.toSortedMap()

    // Al abrir, baja hasta el día de hoy (o el siguiente con clases).
    LaunchedEffect(cargando) {
        if (!cargando) {
            val dias = porDia.keys.toList()
            val i = dias.indexOfFirst { it >= hoy }.takeIf { it >= 0 } ?: 0
            if (dias.isNotEmpty()) lista.scrollToItem(i + 1) // +1 por la tarjeta de "próxima clase"
        }
    }

    val elegirArchivo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            leyendo = true; mensaje = null
            try {
                val (base64, tipo) = withContext(Dispatchers.IO) { archivoParaIA(context, uri) }
                val leidas = Repo.leerHorarioConIA(base64, tipo)
                if (leidas.isEmpty()) mensaje = "No encontré clases en ese archivo. Prueba con una foto más clara."
                else propuestas = leidas
            } catch (e: Exception) {
                mensaje = when {
                    e.message.orEmpty().let { it.contains("Falta configurar") || it.contains("Error de la IA") || it.contains("Error de Gemini") } ->
                        "La lectura con IA todavía no está disponible. Mientras tanto, agrega tus clases con \"Agregar\"."
                    e.message.orEmpty().contains("8 MB") -> "Ese PDF es muy grande (máx. 8 MB)."
                    e.message.orEmpty().contains("límite de lecturas") ->
                        "Llegaste al límite de lecturas con IA por hoy. Mañana puedes seguir, o agrega tus clases con \"Agregar\"."
                    else -> "No se pudo leer el horario. Revisa tu internet e intenta otra vez."
                }
            }
            leyendo = false
        }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        TituloPantalla("Horario") { BotonPastilla("Amigos", onAmigos, icono = Icons.Default.Person, destacado = true) }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotonPastilla("Actualizar", onConectarPortal, icono = Icons.Default.Refresh)
            BotonPastilla(
                if (leyendo) "Leyendo…" else "Foto o PDF", enabled = !leyendo, icono = Icons.Default.Star,
                onClick = { elegirArchivo.launch(arrayOf("image/*", "application/pdf")) }
            )
            BotonPastilla("Agregar", { agregando = true }, icono = Icons.Default.Add)
        }
        val subirHorario = { elegirArchivo.launch(arrayOf("image/*", "application/pdf")) }
        Spacer(Modifier.height(10.dp))

        if (cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        LazyColumn(
            state = lista,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    mensaje?.let { Aviso(it) }
                    if (leyendo) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Indigo, trackColor = Lavanda)
                    ProximaClase(clases, ahora)
                    materiasMarcadas?.let { marcadas ->
                        val pasos = PrimerosPasos.pasos(marcadas, clases.size, tareasOAula)
                        if (PrimerosPasos.mostrar(pasos, pasosOcultos)) TarjetaPrimerosPasos(
                            pasos,
                            onPaso = { if (it.ir == "HORARIO") subirHorario() else onIrA(it.ir) },
                            onOcultar = { pasosOcultos = true; prefsPasos.edit().putBoolean("oculta", true).apply() }
                        )
                    }
                }
            }

            if (clases.isEmpty()) item { HorarioVacio(onConectarPortal) }

            porDia.forEach { (dia, delDia) ->
                item(key = "dia-$dia") {
                    TarjetaDia(
                        dia = dia,
                        clases = delDia.sortedBy { minutos(it.horaInicio) },
                        esHoy = dia == hoy,
                        yaPaso = dia < hoy,
                        onEditar = { editando = it },
                        onApunte = { apunteDe = it },
                        conQuien = { CalculoAmigos.amigosEnClase(it, horariosAmigos) }
                    )
                }
            }
        }
    }

    propuestas?.let { l ->
        AlertDialog(
            onDismissRequest = { propuestas = null },
            containerColor = Tarjeta,
            shape = RoundedCornerShape(28.dp),
            title = { Text("La IA encontró ${l.size} clases ✨", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(l) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(DIAS.getOrElse(c.dia - 1) { "?" }.take(3), color = Indigo, fontWeight = FontWeight.Bold,
                                fontSize = 13.sp, modifier = Modifier.width(40.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.materia, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("${hora12(c.horaInicio)} – ${hora12(c.horaFin)}" + (c.aula?.let { " · Aula $it" } ?: ""),
                                    color = TextoSuave, fontSize = 12.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch { runCatching { Repo.guardarHorarioImportado(l) }; propuestas = null; recargar() }
                }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { propuestas = null }) { Text("Descartar", color = TextoSuave) } }
        )
    }

    if (agregando || editando != null) {
        DialogoClase(
            inicial = editando,
            onCerrar = { agregando = false; editando = null },
            onGuardar = { c ->
                val eraEdicion = editando != null
                agregando = false; editando = null
                scope.launch {
                    runCatching { if (eraEdicion) Repo.actualizarClase(c) else Repo.agregarClases(listOf(c)) }
                    recargar()
                }
            },
            onBorrar = { c ->
                editando = null
                scope.launch { c.id?.let { runCatching { Repo.borrarClase(it) } }; recargar() }
            }
        )
    }

    apunteDe?.let { c ->
        DialogoApunte(
            clase = c,
            onCerrar = { apunteDe = null },
            onGuardar = { texto ->
                apunteDe = null
                scope.launch { runCatching { Repo.actualizarClase(c.copy(apunte = texto.ifBlank { null })) }; recargar() }
            }
        )
    }
}

/** Tarjeta de arriba: la clase que está pasando ahora o la próxima. */
@Composable
private fun ProximaClase(clases: List<Clase>, ahora: LocalDateTime) {
    if (clases.isEmpty()) return
    val hoy = ahora.dayOfWeek.value
    val min = ahora.hour * 60 + ahora.minute
    val deHoy = clases.filter { it.dia == hoy }.sortedBy { minutos(it.horaInicio) }
    val actual = deHoy.firstOrNull { minutos(it.horaInicio) <= min && min < minutos(it.horaFin) }
    val siguienteHoy = deHoy.firstOrNull { minutos(it.horaInicio) > min }
    val (titulo, clase, detalle) = when {
        actual != null -> Triple("Ahora", actual, "hasta las ${hora12(actual.horaFin)}")
        siguienteHoy != null -> Triple("Próxima clase", siguienteHoy,
            "${hora12(siguienteHoy.horaInicio)} · ${enCuanto(minutos(siguienteHoy.horaInicio) - min)}")
        else -> {
            val otra = (1..7).map { (hoy - 1 + it) % 7 + 1 }
                .firstNotNullOfOrNull { d -> clases.filter { it.dia == d }.minByOrNull { minutos(it.horaInicio) } }
                ?: return
            val cuando = if (otra.dia == hoy % 7 + 1) "Mañana" else DIAS[otra.dia - 1]
            Triple("Próxima clase", otra, "$cuando · ${hora12(otra.horaInicio)}")
        }
    }
    TarjetaMC(Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.5f), fondo = Lavanda.copy(alpha = 0.35f)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).background(if (actual != null) VerdeOk else Indigo, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) { Text(if (actual != null) "●" else "⏰", color = Color.White, fontSize = 18.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo.uppercase(), color = if (actual != null) VerdeOk else Indigo, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
                Text(clase.materia, color = AzulNoche, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(detalle + (clase.aula?.let { " · Aula $it" } ?: ""), color = TextoSuave, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TarjetaDia(
    dia: Int,
    clases: List<Clase>,
    esHoy: Boolean,
    yaPaso: Boolean,
    onEditar: (Clase) -> Unit,
    onApunte: (Clase) -> Unit,
    conQuien: (Clase) -> List<Persona>
) {
    TarjetaMC(
        Modifier.fillMaxWidth().alpha(if (yaPaso) 0.6f else 1f),
        borde = if (esHoy) Indigo else colores.borde
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(DIAS.getOrElse(dia - 1) { "Día $dia" }, color = AzulNoche, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (esHoy) {
                Spacer(Modifier.width(10.dp))
                Text("HOY", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.background(Indigo, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(if (clases.size == 1) "1 clase" else "${clases.size} clases", color = TextoSuave, fontSize = 14.sp)
        }
        HorizontalDivider(color = colores.borde)
        clases.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(color = colores.borde, modifier = Modifier.padding(start = 86.dp))
            FilaClase(c, onEditar = { onEditar(c) }, onApunte = { onApunte(c) }, amigos = conQuien(c))
        }
    }
}

@Composable
private fun FilaClase(c: Clase, onEditar: () -> Unit, onApunte: () -> Unit, amigos: List<Persona> = emptyList()) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp)) {
        // Horas
        Column(
            Modifier.width(72.dp).fillMaxHeight().padding(start = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val (h, sufijo) = hora12(c.horaInicio).split(" ").let { it[0] to it.getOrElse(1) { "" } }
            Column {
                Text(h, color = AzulNoche, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(sufijo, color = AzulNoche, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Text(hora12(c.horaFin), color = TextoSuave, fontSize = 12.sp)
        }
        // Línea de tiempo
        Column(Modifier.width(14.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(10.dp).background(Tarjeta, CircleShape).padding(2.dp).background(Indigo, CircleShape))
            Box(Modifier.width(2.dp).weight(1f).background(colores.borde))
        }
        Spacer(Modifier.width(12.dp))
        // Detalle
        Column(Modifier.weight(1f).padding(end = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(c.materia, color = AzulNoche, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                BotonPastilla("Editar", onEditar, icono = Icons.Default.Edit)
            }
            c.codigo?.let { Text(it, color = TextoSuave, fontSize = 13.sp, fontFamily = FontFamily.Monospace) }
            c.profesor?.let { Text(it, color = TextoSuave, fontSize = 14.sp) }
            c.grupo?.let { Text("Grupo $it", color = TextoSuave, fontSize = 14.sp) }
            c.aula?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, null, tint = TextoSuave, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Aula $it", color = AzulNoche, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
            if (amigos.isNotEmpty()) {
                Text(
                    "👥 Con " + amigos.map { it.nombreVisible.substringBefore(" ") }.let {
                        if (it.size <= 2) it.joinToString(" y ") else it.take(2).joinToString(", ") + " y ${it.size - 2} más"
                    },
                    color = Indigo, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                )
            }
            if (c.apunte != null) {
                Text(
                    "📝 ${c.apunte}", color = AzulNoche, fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        .background(colores.tarjetaAlta, RoundedCornerShape(12.dp)).padding(10.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            BotonPastilla(if (c.apunte == null) "Agregar apunte" else "Editar apunte", onApunte)
        }
    }
}

@Composable
private fun Aviso(texto: String) {
    TarjetaMC(Modifier.fillMaxWidth(), borde = colores.rojo.copy(alpha = 0.4f), fondo = colores.rojo.copy(alpha = 0.1f)) {
        Text(texto, color = colores.rojo, fontSize = 14.sp, modifier = Modifier.padding(14.dp))
    }
}

/** Lista de lo que falta para sacarle provecho a la app; cada paso pendiente lleva a donde se hace. */
@Composable
private fun TarjetaPrimerosPasos(pasos: List<Paso>, onPaso: (Paso) -> Unit, onOcultar: () -> Unit) {
    val listos = pasos.count { it.hecho }
    TarjetaMC(Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.5f)) {
        Row(Modifier.padding(start = 18.dp, end = 6.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Primeros pasos", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("$listos de ${pasos.size} listos", color = TextoSuave, fontSize = 13.sp)
            }
            TextButton(onClick = onOcultar) { Text("Ocultar", color = TextoSuave) }
        }
        LinearProgressIndicator(
            progress = { listos / pasos.size.toFloat() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
            color = VerdeOk, trackColor = Lavanda
        )
        pasos.forEach { p ->
            Row(
                Modifier.fillMaxWidth()
                    .let { if (p.hecho) it else it.clickable { onPaso(p) } }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(26.dp).background(if (p.hecho) VerdeOk else Lavanda, CircleShape),
                    contentAlignment = Alignment.Center
                ) { if (p.hecho) Text("✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f).alpha(if (p.hecho) 0.6f else 1f)) {
                    Text(p.titulo, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    if (!p.hecho) Text(p.detalle, color = TextoSuave, fontSize = 13.sp)
                }
                if (!p.hecho) Text("›", color = Indigo, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun HorarioVacio(onConectarPortal: () -> Unit) {
    TarjetaMC(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🗓️", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text("Aún no tienes clases", fontWeight = FontWeight.Bold, fontSize = 18.sp,
                color = AzulNoche, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Tráelo del portal de tu universidad, sube una foto o PDF, o agrega tus clases a mano.",
                fontSize = 14.sp, color = TextoSuave, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConectarPortal, shape = RoundedCornerShape(16.dp)) { Text("Importar del portal") }
        }
    }
}

@Composable
private fun DialogoClase(
    inicial: Clase?,
    onCerrar: () -> Unit,
    onGuardar: (Clase) -> Unit,
    onBorrar: (Clase) -> Unit
) {
    var materia by remember { mutableStateOf(inicial?.materia ?: "") }
    var codigo by remember { mutableStateOf(inicial?.codigo ?: "") }
    var grupo by remember { mutableStateOf(inicial?.grupo ?: "") }
    var dia by remember { mutableIntStateOf(inicial?.dia ?: 1) }
    var inicio by remember { mutableStateOf(inicial?.horaInicio?.take(5) ?: "08:00") }
    var fin by remember { mutableStateOf(inicial?.horaFin?.take(5) ?: "10:00") }
    var aula by remember { mutableStateOf(inicial?.aula ?: "") }
    var profesor by remember { mutableStateOf(inicial?.profesor ?: "") }
    var confirmarBorrado by remember { mutableStateOf(false) }
    val campo = RoundedCornerShape(14.dp)
    val horaValida = Regex("""^\d{1,2}:\d{2}$""")
    val valido = materia.isNotBlank() && horaValida.matches(inicio) && horaValida.matches(fin) && minutos(inicio) < minutos(fin)

    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text(if (inicial == null) "Agregar clase" else "Editar clase", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(materia, { materia = it }, label = { Text("Materia") }, singleLine = true, shape = campo)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(codigo, { codigo = it.uppercase() }, label = { Text("Código") }, singleLine = true,
                        shape = campo, modifier = Modifier.weight(1f))
                    OutlinedTextField(grupo, { grupo = it }, label = { Text("Grupo") }, singleLine = true,
                        shape = campo, modifier = Modifier.weight(0.7f))
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DIAS.take(6).forEachIndexed { i, d ->
                        FilterChip(
                            selected = dia == i + 1, onClick = { dia = i + 1 }, label = { Text(d.take(2)) },
                            shape = RoundedCornerShape(12.dp),
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lavanda, selectedLabelColor = Indigo)
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(inicio, { inicio = it }, label = { Text("Inicio (24 h)") }, singleLine = true, shape = campo,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(fin, { fin = it }, label = { Text("Fin (24 h)") }, singleLine = true, shape = campo,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
                OutlinedTextField(aula, { aula = it }, label = { Text("Aula (opcional)") }, singleLine = true, shape = campo)
                OutlinedTextField(profesor, { profesor = it }, label = { Text("Profesor (opcional)") }, singleLine = true, shape = campo)
                if (inicial != null) {
                    TextButton(
                        onClick = { if (confirmarBorrado) onBorrar(inicial) else confirmarBorrado = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (confirmarBorrado) "Toca otra vez para borrar" else "Borrar esta clase", color = colores.rojo) }
                }
            }
        },
        confirmButton = {
            Button(enabled = valido, shape = RoundedCornerShape(14.dp), onClick = {
                val base = inicial ?: Clase(materia = "", dia = 1, horaInicio = "", horaFin = "")
                onGuardar(base.copy(
                    materia = materia.trim(), dia = dia, horaInicio = inicio.padStart(5, '0'), horaFin = fin.padStart(5, '0'),
                    aula = aula.trim().ifBlank { null }, profesor = profesor.trim().ifBlank { null },
                    codigo = codigo.trim().ifBlank { null }, grupo = grupo.trim().ifBlank { null }
                ))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = TextoSuave) } }
    )
}

@Composable
private fun DialogoApunte(clase: Clase, onCerrar: () -> Unit, onGuardar: (String) -> Unit) {
    var texto by remember { mutableStateOf(clase.apunte ?: "") }
    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Apunte · ${clase.materia}", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                texto, { texto = it }, placeholder = { Text("Tarea, examen, qué llevar…") },
                shape = RoundedCornerShape(14.dp), minLines = 4, modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { Button(onClick = { onGuardar(texto.trim()) }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = TextoSuave) } }
    )
}
