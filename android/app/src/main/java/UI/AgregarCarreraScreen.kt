package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.MateriaLeida
import com.example.myapplication.Repo
import com.example.myapplication.Universidad
import com.example.myapplication.archivoParaIA
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class PasoCarrera { SUBIR, LEYENDO, REVISAR, GUARDANDO }

private class ArchivoPensum(val nombre: String, val datos: String, val tipo: String)

private fun mensajeIA(t: String) = when {
    "límite de lecturas" in t -> "Llegaste al límite de lecturas con IA por hoy. Intenta mañana."
    "8 MB" in t -> "Los archivos son muy grandes (máx. 8 MB en total)."
    "Error de Gemini" in t || "Falta configurar" in t -> "La IA está muy ocupada ahora mismo. Intenta otra vez en un minuto."
    else -> "No se pudo leer el pensum. Revisa tu internet e intenta otra vez."
}

/**
 * "Agrega tu carrera" (igual que en la web): el estudiante sube su pensum (fotos, PDF o texto copiado),
 * la IA lo organiza, lo revisa y lo guarda para todos los estudiantes de su universidad.
 */
@Composable
fun AgregarCarreraScreen(universidad: Universidad, onLista: (String) -> Unit, onCancelar: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var paso by remember { mutableStateOf(PasoCarrera.SUBIR) }
    val archivos = remember { mutableStateListOf<ArchivoPensum>() }
    var texto by remember { mutableStateOf("") }
    var verTexto by remember { mutableStateOf(false) }
    var nombre by remember { mutableStateOf("") }
    val materias = remember { mutableStateListOf<MateriaLeida>() }
    var editando by remember { mutableStateOf<Int?>(null) } // -1 = nueva
    var error by remember { mutableStateOf<String?>(null) }
    val siglas = universidad.siglas ?: universidad.nombre

    BackHandler { if (paso == PasoCarrera.REVISAR) paso = PasoCarrera.SUBIR else if (paso == PasoCarrera.SUBIR) onCancelar() }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        error = null
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    uris.map { uri ->
                        val (datos, tipo) = archivoParaIA(context, uri, maxLado = 2200) // letra chiquita: un poco más grande
                        ArchivoPensum(uri.lastPathSegment?.substringAfterLast('/') ?: "archivo", datos, tipo)
                    }
                }
            }.onSuccess { nuevos -> archivos.addAll(nuevos.take(8 - archivos.size)) }
                .onFailure { error = if ("8 MB" in it.message.orEmpty()) "Ese PDF es muy grande (máx. 8 MB)." else "No se pudo abrir ese archivo." }
        }
    }

    fun leer() {
        paso = PasoCarrera.LEYENDO; error = null
        scope.launch {
            runCatching { Repo.leerPensumConIA(archivos.map { it.datos to it.tipo }, texto) }
                .onSuccess { r ->
                    if (r.materias.size < 5) {
                        error = "No encontré el pensum ahí. Prueba con fotos más claras, el PDF de tu universidad o pega el texto."
                        paso = PasoCarrera.SUBIR
                    } else {
                        materias.clear(); materias.addAll(r.materias)
                        if (nombre.isBlank()) nombre = r.carrera.orEmpty()
                        paso = PasoCarrera.REVISAR
                    }
                }
                .onFailure { error = mensajeIA(it.message.orEmpty()); paso = PasoCarrera.SUBIR }
        }
    }

    fun guardar() {
        paso = PasoCarrera.GUARDANDO; error = null
        scope.launch {
            runCatching { Repo.agregarCarrera(universidad.id, nombre.trim(), materias.toList()) }
                .onSuccess { onLista(it.id) }
                .onFailure {
                    // Los errores de la base de datos ya vienen en español (agregar_carrera)
                    error = (it as? RestException)?.error?.takeIf { e -> e.isNotBlank() && "fetch" !in e }
                        ?: "No se pudo guardar. Revisa tu internet e intenta otra vez."
                    paso = PasoCarrera.REVISAR
                }
        }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        val revisando = paso == PasoCarrera.REVISAR || paso == PasoCarrera.GUARDANDO
        EncabezadoMarca(
            titulo = if (revisando) "Revisa tu pensum" else "Agrega tu carrera",
            subtitulo = universidad.nombre,
            alVolver = { if (paso == PasoCarrera.REVISAR) paso = PasoCarrera.SUBIR else if (paso == PasoCarrera.SUBIR) onCancelar() }
        ) { LogoUniversidad(universidad, 44.dp) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!revisando) {
                item {
                    Column(
                        Modifier.fillMaxWidth().background(Brush.linearGradient(colores.degradado), RoundedCornerShape(24.dp)).padding(20.dp)
                    ) {
                        Text("📄✨", fontSize = 32.sp)
                        Spacer(Modifier.height(6.dp))
                        Text("Sube el pensum y la IA lo organiza", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text("Saca las materias, créditos, períodos y prerrequisitos. Tú lo revisas y queda guardado para todos los estudiantes de $siglas.",
                            color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
                item { Seccion("¿Dónde encuentro mi pensum?", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        Pista("🌐", "En la página de tu universidad", "Busca “pensum” o “plan de estudios” + tu carrera. Casi siempre es un PDF.")
                        HorizontalDivider(color = colores.borde)
                        Pista("🎓", "En el portal de estudiantes", "Copia el texto de la página del pensum y pégalo aquí.")
                        HorizontalDivider(color = colores.borde)
                        Pista("📸", "En papel", "Tómale fotos claras a todas las páginas.")
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { elegir.launch(arrayOf("image/*", "application/pdf")) }, enabled = paso == PasoCarrera.SUBIR,
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50)
                            ) { Text("📸 Fotos o PDF", color = colores.texto, fontWeight = FontWeight.SemiBold) }
                        }
                        Box(Modifier.weight(1f)) {
                            OutlinedButton(
                                onClick = { verTexto = !verTexto }, enabled = paso == PasoCarrera.SUBIR,
                                modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (verTexto) colores.acentoSuave else Color.Transparent)
                            ) { Text("📋 Pegar texto", color = colores.texto, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                if (archivos.isNotEmpty()) item {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        archivos.forEachIndexed { i, a ->
                            Row(
                                Modifier.fillMaxWidth().background(colores.tarjetaAlta, RoundedCornerShape(14.dp)).padding(start = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (a.tipo == "application/pdf") "📄" else "🖼️")
                                Spacer(Modifier.width(8.dp))
                                Text(a.nombre, color = colores.texto, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                IconButton(onClick = { archivos.removeAt(i) }) { Icon(Icons.Default.Close, "Quitar", tint = colores.textoSuave) }
                            }
                        }
                    }
                }
                if (verTexto) item {
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it.take(60_000) },
                        placeholder = { Text("Pega aquí el texto del pensum (materias, códigos, créditos…)") },
                        minLines = 6, maxLines = 12, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) } }
                item {
                    if (paso == PasoCarrera.LEYENDO) {
                        TarjetaMC(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp)) {
                                Text("Leyendo tu pensum con IA…", color = colores.texto, fontWeight = FontWeight.Bold)
                                Text("Si es largo puede tardar hasta un minuto.", color = colores.textoSuave, fontSize = 14.sp)
                                Spacer(Modifier.height(12.dp))
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                            }
                        }
                    } else {
                        Button(
                            onClick = ::leer, enabled = archivos.isNotEmpty() || texto.trim().length >= 30,
                            modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
                        ) { Text("Leer el pensum con IA", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it.take(120) }, label = { Text("Nombre de la carrera") },
                        placeholder = { Text("Ej. Ingeniería Industrial (Plan 2022)") },
                        singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    val periodos = materias.map { it.periodo }.distinct().size
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(vertical = 14.dp)) {
                            listOf(materias.size to "Materias", periodos to "Períodos", materias.sumOf { it.creditos } to "Créditos").forEach { (v, t) ->
                                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$v", color = colores.texto, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                                    Text(t, color = colores.textoSuave, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                item {
                    Text("Revisa que no falte nada: toca una materia para corregirla. Se guardará para todos los estudiantes de $siglas.",
                        color = colores.textoSuave, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
                }
                val grupos = materias.withIndex().groupBy { it.value.periodo }.toSortedMap()
                grupos.forEach { (p, lista) ->
                    item(key = "p$p") {
                        TarjetaMC(Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("$p.º período", color = colores.texto, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                                Text("${lista.size} mat. · ${lista.sumOf { it.value.creditos }} cr.", color = colores.textoSuave, fontSize = 13.sp)
                            }
                            lista.forEach { (i, m) ->
                                HorizontalDivider(color = colores.borde)
                                Column(Modifier.fillMaxWidth().clickable { editando = i }.padding(horizontal = 16.dp, vertical = 11.dp)) {
                                    Text(m.nombre, color = colores.texto, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                                    Text(
                                        buildString {
                                            append("${m.codigo} · ${m.creditos} cr.")
                                            if (m.prerequisitos.isNotEmpty()) append("   🔗 ${m.prerequisitos.joinToString(", ")}")
                                        },
                                        color = colores.textoSuave, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    OutlinedButton(onClick = { editando = -1 }, modifier = Modifier.fillMaxWidth().height(46.dp), shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Agregar una materia", color = colores.texto, fontWeight = FontWeight.SemiBold)
                    }
                }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) } }
                item {
                    Button(
                        onClick = ::guardar, enabled = paso == PasoCarrera.REVISAR && nombre.trim().length >= 3 && materias.size >= 5,
                        modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        if (paso == PasoCarrera.GUARDANDO) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Guardar mi carrera", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (nombre.trim().length < 3) Text("Escribe el nombre de la carrera arriba.", color = colores.textoSuave, fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                }
            }
        }
    }

    editando?.let { i ->
        val nueva = i < 0
        DialogoMateria(
            materia = if (nueva) null else materias[i],
            periodoSugerido = materias.maxOfOrNull { it.periodo } ?: 1,
            onCerrar = { editando = null },
            onBorrar = if (nueva) null else ({ materias.removeAt(i); editando = null }),
            onGuardar = { m -> if (nueva) materias.add(m) else materias[i] = m; editando = null }
        )
    }
}

@Composable
private fun Pista(emoji: String, titulo: String, detalle: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, color = colores.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detalle, color = colores.textoSuave, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DialogoMateria(
    materia: MateriaLeida?, periodoSugerido: Int,
    onGuardar: (MateriaLeida) -> Unit, onBorrar: (() -> Unit)?, onCerrar: () -> Unit
) {
    var codigo by remember { mutableStateOf(materia?.codigo ?: "") }
    var nombre by remember { mutableStateOf(materia?.nombre ?: "") }
    var creditos by remember { mutableStateOf((materia?.creditos ?: 3).toString()) }
    var periodo by remember { mutableStateOf((materia?.periodo ?: periodoSugerido).toString()) }
    var prereq by remember { mutableStateOf(materia?.prerequisitos?.joinToString(", ") ?: "") }
    val valido = codigo.isNotBlank() && nombre.trim().length >= 2
    val numerico = KeyboardOptions(keyboardType = KeyboardType.Number)

    AlertDialog(
        onDismissRequest = onCerrar,
        containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text(if (materia == null) "Agregar materia" else "Corregir materia", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(codigo, { codigo = it.uppercase().take(20) }, label = { Text("Código") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(periodo, { periodo = it.filter(Char::isDigit).take(2) }, label = { Text("Período") }, singleLine = true,
                        keyboardOptions = numerico, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(nombre, { nombre = it.take(120) }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(creditos, { creditos = it.filter(Char::isDigit).take(2) }, label = { Text("Créditos") }, singleLine = true,
                        keyboardOptions = numerico, modifier = Modifier.weight(1f))
                    OutlinedTextField(prereq, { prereq = it.uppercase() }, label = { Text("Prerrequisitos") }, placeholder = { Text("MAT-101, ESP-101") },
                        singleLine = true, modifier = Modifier.weight(2f))
                }
            }
        },
        confirmButton = {
            Button(enabled = valido, shape = RoundedCornerShape(14.dp), onClick = {
                onGuardar(MateriaLeida(
                    codigo = codigo.trim(), nombre = nombre.trim(),
                    creditos = (creditos.toIntOrNull() ?: 0).coerceIn(0, 30),
                    periodo = (periodo.toIntOrNull() ?: 1).coerceIn(1, 20),
                    prerequisitos = prereq.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }.take(10)
                ))
            }) { Text("Guardar") }
        },
        dismissButton = {
            Row {
                if (onBorrar != null) TextButton(onClick = onBorrar) { Text("Quitar", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onCerrar) { Text("Cancelar", color = TextoSuave) }
            }
        }
    )
}
