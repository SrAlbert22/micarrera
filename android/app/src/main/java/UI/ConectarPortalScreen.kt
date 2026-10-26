package com.example.myapplication.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.myapplication.Clase
import com.example.myapplication.DIAS
import com.example.myapplication.LectorHorario
import com.example.myapplication.LectorRecord
import com.example.myapplication.RecordPortal
import com.example.myapplication.Repo
import com.example.myapplication.ResultadoImportacion
import com.example.myapplication.Universidad
import com.example.myapplication.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Texto visible de la página, incluido el de los marcos internos (iframes/frames): muchos portales
 * de inscripción muestran la tabla de materias dentro de uno.
 */
private const val JS_TEXTO = """
(function texto(doc) {
  var t = doc.body ? doc.body.innerText : '';
  var marcos = doc.querySelectorAll('iframe, frame');
  for (var i = 0; i < marcos.length; i++) {
    try { t += '\n' + texto(marcos[i].contentDocument); } catch (e) { /* marco de otro sitio */ }
  }
  return t;
})(document);
"""

private suspend fun WebView.textoDeLaPagina(): String = suspendCancellableCoroutine { cont ->
    evaluateJavascript(JS_TEXTO.trimIndent()) { resultado ->
        // evaluateJavascript devuelve el valor como JSON ("\"texto...\"" o "null").
        val texto = runCatching { json.decodeFromString<String?>(resultado ?: "null") }.getOrNull()
        cont.resume(texto.orEmpty())
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ConectarPortalScreen(
    carreraId: String,
    onCerrar: () -> Unit,
    onVerPensum: () -> Unit,
    onVerHorario: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var progresoPagina by remember { mutableIntStateOf(0) }
    var importando by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var record by remember { mutableStateOf<RecordPortal?>(null) }
    var resultado by remember { mutableStateOf<ResultadoImportacion?>(null) }
    var horario by remember { mutableStateOf<List<Clase>?>(null) }
    var resultadoHorario by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var universidad by remember { mutableStateOf<Universidad?>(null) }
    var cargandoUni by remember { mutableStateOf(true) }
    var direccion by remember { mutableStateOf("") }
    var ultimoTexto by remember { mutableStateOf<String?>(null) } // para "Copiar el texto" si no se entiende
    val context = LocalContext.current
    // Aquí se escribe la contraseña del portal y se ven las notas: sin capturas de pantalla y oculto en "apps recientes"
    DisposableEffect(Unit) {
        val ventana = (context as? android.app.Activity)?.window
        ventana?.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { ventana?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE) }
    }

    LaunchedEffect(carreraId) {
        universidad = runCatching { Repo.carreraDetalle(carreraId)?.universidades }.getOrNull()
        cargandoUni = false
    }
    val siglas = universidad?.siglas ?: "tu universidad"

    fun volverAlPortal() { record = null; resultado = null; horario = null; resultadoHorario = null; error = null }

    // Al salir: cerrar la sesión del portal y no dejar rastro en el teléfono.
    // (La WebView en sí se limpia en onRelease del AndroidView.)
    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
            WebStorage.getInstance().deleteAllData()
        }
    }

    BackHandler {
        val w = webView
        when {
            record != null || horario != null -> volverAlPortal()
            w != null && w.canGoBack() -> w.goBack()
            else -> onCerrar()
        }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        EncabezadoMarca(
            titulo = "Conectar mi portal",
            subtitulo = "Trae tus notas y tu horario de $siglas",
            alVolver = onCerrar
        )

        val r = record
        // La WebView se queda montada siempre (debajo de la vista previa) para no perder la página abierta.
        Box(Modifier.weight(1f)) {
        Column(Modifier.fillMaxSize()) {
            // Aviso de privacidad
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Lavanda)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(Icons.Default.Lock, null, tint = Indigo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Inicia sesión en tu portal y abre tu récord de calificaciones, tu horario o tu inscripción. " +
                            "Tu contraseña se escribe solo en la página de $siglas; MiCarrera no la ve ni la guarda.",
                        fontSize = 13.sp, color = AzulNoche
                    )
                }
            }

            if (direccion.isNotEmpty()) Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Lock, null, tint = VerdeOk, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(direccion, color = TextoSuave, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                IconButton(onClick = { webView?.let { if (it.canGoBack()) it.goBack() } }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = TextoSuave, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { webView?.reload() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Refresh, "Recargar", tint = TextoSuave, modifier = Modifier.size(18.dp))
                }
            }

            if (progresoPagina in 1..99) {
                LinearProgressIndicator(
                    progress = { progresoPagina / 100f },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    color = Indigo, trackColor = Lavanda
                )
            }

            Card(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Tarjeta),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                val url = universidad?.portalUrl
                if (cargandoUni) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else if (url == null) Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("Todavía no tenemos el portal de $siglas. Escríbenos y lo agregamos.",
                        color = TextoSuave, fontSize = 15.sp)
                }
                else AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.allowFileAccess = false    // la página del portal no puede leer archivos del teléfono
                            settings.allowContentAccess = false
                            webViewClient = object : WebViewClient() {
                                override fun doUpdateVisitedHistory(view: WebView?, u: String?, isReload: Boolean) {
                                    direccion = u?.let { Uri.parse(it).host }.orEmpty()
                                }
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progresoPagina = newProgress
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    onRelease = { w ->
                        w.stopLoading()
                        w.clearFormData()
                        w.clearCache(true)
                        w.clearHistory()
                        w.destroy()
                    }
                )
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
            }
            // Si no entendimos la página, el estudiante puede copiar su texto y enviárnoslo para mejorar el lector.
            ultimoTexto?.let { t ->
                TextButton(
                    onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            .setPrimaryClip(ClipData.newPlainText("Página del portal", t))
                        error = "Texto copiado. Pégalo en un mensaje para que mejoremos el lector (borra tu nombre y matrícula)."
                    },
                    modifier = Modifier.padding(start = 8.dp)
                ) { Text("Copiar el texto de esta página") }
            }

            Button(
                enabled = !importando && webView != null,
                modifier = Modifier.fillMaxWidth().padding(16.dp).navigationBarsPadding().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                onClick = {
                    val w = webView ?: return@Button
                    scope.launch {
                        importando = true; error = null; ultimoTexto = null
                        try {
                            // Nunca más de 15 s esperando a la página ni 15 s leyéndola: si algo se traba, avisamos.
                            val texto = withTimeoutOrNull(15_000) { w.textoDeLaPagina() }
                            if (texto == null) {
                                error = "La página no respondió. Espera a que termine de cargar e intenta otra vez."
                            } else if (texto.isBlank()) {
                                error = "La página está vacía. Espera a que cargue e intenta de nuevo."
                            } else {
                                // Se lee aquí mismo en el teléfono: el texto no se envía a ningún lado.
                                // Si la página tiene horas de clase es el horario; si no, se intenta como récord.
                                val lectura = scope.async(Dispatchers.Default) {
                                    val c = LectorHorario.leer(texto)
                                    c to (if (c.isEmpty()) LectorRecord.leer(texto) else null)
                                }
                                val (clases, leido) = withTimeoutOrNull(15_000) { lectura.await() } ?: (emptyList<Clase>() to null)
                                when {
                                    clases.isNotEmpty() -> horario = clases
                                    leido != null && leido.materias.isNotEmpty() -> record = leido
                                    else -> {
                                        error = "No encontré notas ni clases en esta página. " +
                                            "Abre tu récord de calificaciones, tu horario o tu inscripción."
                                        ultimoTexto = texto
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            error = "No se pudo leer la página. Intenta otra vez."
                        }
                        importando = false
                    }
                }
            ) {
                if (importando) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Leyendo la página…", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Importar esta página", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (r != null) Box(Modifier.fillMaxSize().background(FondoApp)) {
            VistaPrevia(
                record = r,
                resultado = resultado,
                guardando = guardando,
                error = error,
                onGuardar = {
                    scope.launch {
                        guardando = true; error = null
                        try {
                            resultado = Repo.guardarRecord(carreraId, r)
                        } catch (e: Exception) {
                            error = "No se pudo guardar. Revisa tu internet e intenta otra vez."
                        }
                        guardando = false
                    }
                },
                onOtraPagina = ::volverAlPortal,
                onListo = onVerPensum
            )
        }

        val h = horario
        if (r == null && h != null) Box(Modifier.fillMaxSize().background(FondoApp)) {
            VistaPreviaHorario(
                clases = h,
                resultado = resultadoHorario,
                guardando = guardando,
                error = error,
                onGuardar = {
                    scope.launch {
                        guardando = true; error = null
                        try {
                            resultadoHorario = Repo.guardarHorarioImportado(h)
                        } catch (e: Exception) {
                            error = "No se pudo guardar. Revisa tu internet e intenta otra vez."
                        }
                        guardando = false
                    }
                },
                onOtraPagina = ::volverAlPortal,
                onListo = onVerHorario
            )
        }
        }
    }
}

@Composable
private fun VistaPrevia(
    record: RecordPortal,
    resultado: ResultadoImportacion?,
    guardando: Boolean,
    error: String?,
    onGuardar: () -> Unit,
    onOtraPagina: () -> Unit,
    onListo: () -> Unit
) {
    val cuenta = record.materias.groupingBy { it.estado }.eachCount()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Tarjeta),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (resultado == null) "Esto encontramos 🔍" else "¡Listo! ✅",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AzulNoche
                )
                Text("${record.materias.size} materias en tu récord", fontSize = 14.sp, color = TextoSuave)

                record.indice?.let {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("%.2f".format(it), fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = Indigo)
                        Spacer(Modifier.width(10.dp))
                        Text("índice\nacumulado", fontSize = 13.sp, color = TextoSuave, lineHeight = 16.sp)
                    }
                }

                Spacer(Modifier.height(16.dp))
                FilaCuenta(VerdeOk, "Aprobadas", cuenta["aprobada"] ?: 0)
                FilaCuenta(AmbarCurso, "En curso", cuenta["en_curso"] ?: 0)
                if ((cuenta["reprobada"] ?: 0) > 0) FilaCuenta(MaterialTheme.colorScheme.error, "Reprobadas", cuenta["reprobada"] ?: 0)
                if ((cuenta["retirada"] ?: 0) > 0) FilaCuenta(GrisPendiente, "Retiradas", cuenta["retirada"] ?: 0)
            }
        }

        if (resultado != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Lavanda)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Se actualizaron ${resultado.guardadas} materias de tu pensum.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AzulNoche)
                    if (resultado.sinCoincidir.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "No están en tu pensum: ${resultado.sinCoincidir.joinToString()}",
                            fontSize = 13.sp, color = TextoSuave
                        )
                    }
                }
            }
        } else {
            Text(
                "Las reprobadas y retiradas quedan como pendientes, pero guardamos su nota.",
                fontSize = 13.sp, color = TextoSuave, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        if (resultado == null) {
            Button(
                enabled = !guardando,
                onClick = onGuardar,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (guardando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar en mi pensum", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onOtraPagina, enabled = !guardando, modifier = Modifier.fillMaxWidth()) {
                Text("Volver al portal")
            }
        } else {
            Button(
                onClick = onListo,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Ver mi pensum", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun VistaPreviaHorario(
    clases: List<Clase>,
    resultado: Pair<Int, Int>?,
    guardando: Boolean,
    error: String?,
    onGuardar: () -> Unit,
    onOtraPagina: () -> Unit,
    onListo: () -> Unit
) {
    val porDia = clases.groupBy { it.dia }.toSortedMap()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Tarjeta),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (resultado == null) "Esto encontramos 🗓️" else "¡Listo! ✅",
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AzulNoche
                )
                Text(
                    "${clases.size} clases en ${porDia.size} ${if (porDia.size == 1) "día" else "días"}",
                    fontSize = 14.sp, color = TextoSuave
                )
                porDia.forEach { (dia, lista) ->
                    Spacer(Modifier.height(14.dp))
                    Text(DIAS.getOrElse(dia - 1) { "Día $dia" }, fontSize = 15.sp,
                        fontWeight = FontWeight.Bold, color = AzulNoche)
                    lista.forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${c.horaInicio}–${c.horaFin}", color = Indigo, fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.background(Lavanda, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.materia, fontSize = 14.sp, color = AzulNoche, fontWeight = FontWeight.SemiBold)
                                val detalle = listOfNotNull(c.aula?.let { "Aula $it" }, c.profesor).joinToString(" · ")
                                if (detalle.isNotBlank()) Text(detalle, fontSize = 12.sp, color = TextoSuave)
                            }
                        }
                    }
                }
            }
        }

        if (resultado != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Lavanda)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Se agregaron ${resultado.first} clases a tu horario.",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AzulNoche)
                    if (resultado.second > 0) {
                        Spacer(Modifier.height(4.dp))
                        Text("${resultado.second} ya estaban, no las repetimos.", fontSize = 13.sp, color = TextoSuave)
                    }
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }

        if (resultado == null) {
            Button(
                enabled = !guardando,
                onClick = onGuardar,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (guardando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar en mi horario", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
            TextButton(onClick = onOtraPagina, enabled = !guardando, modifier = Modifier.fillMaxWidth()) {
                Text("Volver al portal")
            }
        } else {
            Button(
                onClick = onListo,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) { Text("Ver mi horario", fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun FilaCuenta(color: Color, texto: String, n: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(10.dp))
        Text(texto, fontSize = 15.sp, color = AzulNoche, modifier = Modifier.weight(1f))
        Text("$n", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AzulNoche)
    }
}
