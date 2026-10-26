package com.example.myapplication.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import com.example.myapplication.Actualizador
import com.example.myapplication.BuildConfig
import com.example.myapplication.nombreVersionInstalada
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Alertas
import com.example.myapplication.CarreraDetalle
import com.example.myapplication.Materia
import com.example.myapplication.Perfil
import com.example.myapplication.Push
import com.example.myapplication.compartirInvitacion
import com.example.myapplication.Repo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ícono de "Tema" (medio sol, medio sombra). */
private val IconoTema: ImageVector = ImageVector.Builder("tema", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        addPathNodes("M20 15.31 23.31 12 20 8.69V4h-4.69L12 .69 8.69 4H4v4.69L.69 12 4 15.31V20h4.69L12 23.31 15.31 20H20v-4.69zM12 18V6c3.31 0 6 2.69 6 6s-2.69 6-6 6z"),
        fill = SolidColor(Color.Black)
    )
}.build()

@Composable
fun CuentaScreen(
    carreraId: String,
    onCambiarCarrera: () -> Unit,
    onConectarPortal: () -> Unit,
    onAmigos: () -> Unit,
    onTest: () -> Unit,
    onCinco: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var perfil by remember { mutableStateOf<Perfil?>(null) }
    var saliendoDeTodo by remember { mutableStateOf(false) }
    var eliminando by remember { mutableStateOf(false) }
    var carrera by remember { mutableStateOf<CarreraDetalle?>(null) }
    var materias by remember { mutableStateOf<List<Materia>>(emptyList()) }
    val estados = remember { mutableStateMapOf<String, String>() }
    var editandoNombre by remember { mutableStateOf(false) }
    var viendoPrivacidad by remember { mutableStateOf(false) }
    var confirmandoSalida by remember { mutableStateOf(false) }
    var cambiandoContrasena by remember { mutableStateOf(false) }
    var editandoUsuario by remember { mutableStateOf(false) }
    var eligiendo by remember { mutableStateOf<String?>(null) } // "buscable" | "horario"
    var eligiendoTema by remember { mutableStateOf(false) }
    var estadoVersion by remember { mutableStateOf("Toca para buscar actualizaciones") }

    val context = LocalContext.current
    var alertasClase by remember { mutableStateOf(Alertas.clasesActivadas(context)) }
    var resumen by remember { mutableStateOf(Alertas.resumenActivado(context)) }
    var minutosAntes by remember { mutableIntStateOf(Alertas.minutosAntes(context)) }
    var permiso by remember { mutableStateOf(Alertas.tienePermiso(context)) }
    var mensajePush by remember { mutableStateOf<String?>(null) }
    val abrirAjustes = {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        )
    }
    // Si Android ya no muestra el diálogo (lo negaron dos veces), abrimos los ajustes de la app.
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        permiso = ok
        if (!ok) abrirAjustes()
    }

    LaunchedEffect(carreraId) {
        runCatching { perfil = Repo.perfil() }
        runCatching { carrera = Repo.carreraDetalle(carreraId) }
        runCatching {
            materias = Repo.materias(carreraId)
            Repo.progreso().forEach { estados[it.materiaId] = it.estado }
        }
    }

    val correo = Repo.correo.orEmpty()
    val nombre = perfil?.nombre?.takeIf { it.isNotBlank() } ?: correo.substringBefore("@").ifBlank { "Estudiante" }
    val iniciales = nombre.split(Regex("""[\s._-]+""")).filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifBlank { "🎓" }
    val aprobadas = materias.filter { estados[it.id] == "aprobada" }
    val credTotal = materias.sumOf { it.creditos }.coerceAtLeast(1)
    val credAprob = aprobadas.sumOf { it.creditos }
    val faltan = materias.filter { estados[it.id] != "aprobada" }.map { it.cuatrimestre }.distinct().size

    Column(Modifier.fillMaxSize().background(FondoApp).verticalScroll(rememberScrollState())) {
        // Cabecera con degradado
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(colores.degradado.last().copy(alpha = 0.55f), Color.Transparent)))
                .statusBarsPadding().padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.size(96.dp).background(Indigo, CircleShape).border(4.dp, FondoApp, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(iniciales, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(nombre, color = AzulNoche, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("Cambiar", color = Indigo, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { editandoNombre = true })
            }
            carrera?.let { c ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    c.universidades?.let { LogoUniversidad(it, 26.dp); Spacer(Modifier.width(8.dp)) }
                    Text(c.nombre, color = TextoSuave, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Números
            TarjetaMC(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Numero(Modifier.weight(1f), perfil?.indice?.let { "%.2f".format(it) } ?: "—", "Índice")
                    Box(Modifier.width(1.dp).height(36.dp).background(colores.borde))
                    Numero(Modifier.weight(1f), "${aprobadas.size}", "Aprobadas")
                    Box(Modifier.width(1.dp).height(36.dp).background(colores.borde))
                    Numero(Modifier.weight(1f), "$credAprob", "Créditos")
                }
            }

            // Avance de carrera
            TarjetaMC(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Avance de carrera", color = AzulNoche, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("${credAprob * 100 / credTotal}%", color = Indigo, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(12.dp))
                    BarraPorPeriodos(materias, estados)
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Text("$credAprob / $credTotal cr.", color = TextoSuave, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        Text(
                            when (faltan) { 0 -> "¡Carrera completa! 🎉"; 1 -> "Te falta 1 período"; else -> "Te faltan $faltan períodos" },
                            color = TextoSuave, fontSize = 13.sp
                        )
                    }
                }
            }

            Seccion("Amigos y privacidad", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(Icons.Default.Person, "Tu usuario", perfil?.usuario?.let { "@$it" } ?: "Crea tu @usuario") { editandoUsuario = true }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Share, "Amigos", "Solicitudes, clases en común y horas libres", onAmigos)
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Email, "Invitar amigos", "Comparte tu enlace de MiCarrera") {
                    compartirInvitacion(context, perfil?.usuario)
                }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Search, "Quién te puede encontrar",
                    OPCIONES_BUSCABLE.firstOrNull { it.first == (perfil?.buscable ?: "universidad") }?.second.orEmpty()) { eligiendo = "buscable" }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.DateRange, "Quién ve tu horario",
                    OPCIONES_HORARIO.firstOrNull { it.first == (perfil?.horarioVisible ?: "amigos") }?.second.orEmpty()) { eligiendo = "horario" }
                HorizontalDivider(color = colores.borde)
                FilaInterruptor(Icons.Default.Star, "Mostrar mis materias aprobadas",
                    if (perfil?.logrosVisibles == false) "Tus amigos no ven cuando apruebas" else "Tus amigos pueden chocarte los 5 🙌",
                    perfil?.logrosVisibles != false) { v ->
                    perfil = perfil?.copy(logrosVisibles = v)
                    scope.launch { runCatching { Repo.guardarLogrosVisibles(v) }.onFailure { perfil = perfil?.copy(logrosVisibles = !v) } }
                }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Lock, "Tus notas e índice", "Nunca se comparten con nadie") {}
            }

            Seccion("Portal", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(
                    Icons.Default.Refresh, "Conectar mi portal",
                    perfil?.portalActualizado?.let { "Actualizado el ${fecha(it)}" } ?: "Trae tus notas y tu horario de tu universidad",
                    onConectarPortal
                )
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Lock, "Qué pasa al conectar tu portal", "Lo que leemos, lo que se guarda y lo que no.") {
                    viendoPrivacidad = true
                }
            }

            Seccion("Carrera", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(Icons.Default.Settings, "Cambiar de carrera", carrera?.nombre ?: "", onCambiarCarrera)
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Search, "¿Qué debo estudiar?", "Test vocacional: carreras y universidades para ti", onTest)
            }

            Seccion("Notificaciones", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                if (!permiso) {
                    FilaAjuste(Icons.Default.Warning, "Permite las notificaciones", "Sin permiso no podemos avisarte.") {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else abrirAjustes()
                    }
                    HorizontalDivider(color = colores.borde)
                }
                FilaInterruptor(Icons.Default.Notifications, "Alertas de clase", "$minutosAntes min antes de cada clase", alertasClase) {
                    alertasClase = it
                    Alertas.configurar(context, clases = it)
                }
                if (alertasClase) {
                    Row(
                        Modifier.padding(start = 70.dp, end = 16.dp, bottom = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Alertas.MINUTOS_OPCIONES.forEach { m ->
                            FilterChip(
                                selected = minutosAntes == m,
                                onClick = { minutosAntes = m; Alertas.configurar(context, minutos = m) },
                                label = { Text("$m min") },
                                shape = RoundedCornerShape(12.dp),
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Lavanda, selectedLabelColor = Indigo)
                            )
                        }
                    }
                }
                HorizontalDivider(color = colores.borde)
                FilaInterruptor(Icons.Default.DateRange, "Resumen del día", "Cada mañana a las 7:00, tus clases de hoy", resumen) {
                    resumen = it
                    Alertas.configurar(context, resumen = it)
                }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(
                    Icons.Default.Share, "Probar aviso del servidor",
                    mensajePush ?: "Te llega una desde Firebase en unos segundos"
                ) {
                    mensajePush = "Enviando…"
                    scope.launch {
                        Push.registrar()
                        mensajePush = runCatching { Repo.probarPush() }
                            .fold({ "¡Enviado! Debería llegarte ya." }, { "No se pudo enviar. ¿Está configurado Firebase en Supabase?" })
                    }
                }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.PlayArrow, "Probar notificación", "Te llega una ahora mismo") {
                    permiso = Alertas.tienePermiso(context)
                    if (permiso) Alertas.probar(context)
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else abrirAjustes()
                }
            }

            Seccion("Apariencia", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(IconoTema, "Tema", Apariencia.OPCIONES.firstOrNull { it.first == Apariencia.tema }?.second.orEmpty()) {
                    eligiendoTema = true
                }
            }

            Seccion("App", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(Icons.Default.Info, "Versión ${nombreVersionInstalada(context)}", estadoVersion) {
                    if (BuildConfig.TIENDA_PLAY) { // en Play Store se actualiza desde la tienda
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                        }
                        return@FilaAjuste
                    }
                    estadoVersion = "Buscando…"
                    scope.launch {
                        estadoVersion = runCatching { Actualizador.buscar(context) }.fold(
                            onSuccess = { v -> if (v == null) "Tienes la última versión ✓" else "Hay una versión nueva: ${v.versionName}" },
                            onFailure = { "No se pudo revisar. ¿Tienes internet?" }
                        )
                    }
                }
            }

            Seccion("Ayuda", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth(), onClick = onCinco) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cinco(tam = 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("5inco, tu guía", color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Text("Ayuda, comentarios y el tutorial de la app", color = TextoSuave, fontSize = 13.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GrisPendiente)
                }
            }

            Seccion("Legal", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(Icons.Default.Lock, "Política de privacidad", "Qué datos usamos y quién los ve") { abrirPagina(context, "privacidad") }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Info, "Términos y condiciones", "Las reglas para usar MiCarrera") { abrirPagina(context, "terminos") }
            }

            Seccion("Sesión", Modifier.padding(start = 4.dp, top = 6.dp))
            TarjetaMC(Modifier.fillMaxWidth()) {
                FilaAjuste(Icons.Default.Lock, "Contraseña", "Crear o cambiar tu contraseña") { cambiandoContrasena = true }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.AutoMirrored.Filled.ExitToApp, "Cerrar sesión", correo) { confirmandoSalida = true }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Lock, "Cerrar sesión en todos los dispositivos", "Si perdiste tu teléfono o entraste en otra computadora") {
                    saliendoDeTodo = true
                }
                HorizontalDivider(color = colores.borde)
                FilaAjuste(Icons.Default.Delete, "Eliminar mi cuenta", "Borra tu cuenta y todos tus datos para siempre") { eliminando = true }
            }

            Text(
                "MiCarrera v${nombreVersionInstalada(context)} · Creado por Albert Silvestre", color = TextoSuave, fontSize = 12.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
            )
        }
    }

    if (editandoNombre) {
        var texto by remember { mutableStateOf(perfil?.nombre ?: "") }
        AlertDialog(
            onDismissRequest = { editandoNombre = false },
            containerColor = Tarjeta,
            shape = RoundedCornerShape(28.dp),
            title = { Text("¿Cómo te llamas?", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(texto, { texto = it.take(40) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    placeholder = { Text("Tu nombre") })
            },
            confirmButton = {
                Button(enabled = texto.isNotBlank(), shape = RoundedCornerShape(14.dp), onClick = {
                    editandoNombre = false
                    perfil = perfil?.copy(nombre = texto.trim())
                    scope.launch { runCatching { Repo.guardarNombre(texto) } }
                }) { Text("Guardar") }
            },
            dismissButton = { TextButton(onClick = { editandoNombre = false }) { Text("Cancelar", color = TextoSuave) } }
        )
    }

    if (cambiandoContrasena) DialogoContrasena(primeraVez = false) { cambiandoContrasena = false }

    if (editandoUsuario) DialogoUsuario(perfil?.usuario) { nuevo ->
        editandoUsuario = false
        if (nuevo != null) perfil = perfil?.copy(usuario = nuevo)
    }
    if (eligiendoTema) DialogoOpciones("Tema", Apariencia.OPCIONES, Apariencia.tema) { valor ->
        eligiendoTema = false
        valor?.let { Apariencia.cambiar(context, it) }
    }

    eligiendo?.let { que ->
        DialogoOpciones(
            titulo = if (que == "buscable") "¿Quién te puede encontrar?" else "¿Quién ve tu horario?",
            opciones = if (que == "buscable") OPCIONES_BUSCABLE else OPCIONES_HORARIO,
            actual = if (que == "buscable") perfil?.buscable ?: "universidad" else perfil?.horarioVisible ?: "amigos"
        ) { valor ->
            eligiendo = null
            if (valor != null) {
                perfil = if (que == "buscable") perfil?.copy(buscable = valor) else perfil?.copy(horarioVisible = valor)
                scope.launch {
                    runCatching { if (que == "buscable") Repo.guardarPrivacidad(buscable = valor) else Repo.guardarPrivacidad(horarioVisible = valor) }
                }
            }
        }
    }

    if (viendoPrivacidad) {
        AlertDialog(
            onDismissRequest = { viendoPrivacidad = false },
            containerColor = Tarjeta,
            shape = RoundedCornerShape(28.dp),
            title = { Text("Tu portal y tu privacidad 🔒", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Punto("🔑", "Tu contraseña", "La escribes solo en la página de tu universidad. MiCarrera no la ve ni la guarda.")
                    Punto("📱", "Lo que leemos", "Solo el texto de la página que tú abres (récord u horario), y se lee en tu teléfono.")
                    Punto("☁️", "Lo que se guarda", "Tus materias, notas, índice y horario, en tu cuenta. Solo tú los ves.")
                    Punto("🧹", "Al salir", "Borramos las cookies y la sesión del portal del teléfono.")
                }
            },
            confirmButton = { TextButton(onClick = { viendoPrivacidad = false }) { Text("Entendido") } }
        )
    }

    DialogosSesion(saliendoDeTodo, eliminando) { saliendoDeTodo = false; eliminando = false }

    if (confirmandoSalida) {
        AlertDialog(
            onDismissRequest = { confirmandoSalida = false },
            containerColor = Tarjeta,
            shape = RoundedCornerShape(28.dp),
            title = { Text("¿Cerrar sesión?", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = { Text("Tus datos quedan guardados. Vuelves a entrar con tu correo.", color = TextoSuave) },
            confirmButton = {
                Button(onClick = {
                    confirmandoSalida = false
                    Alertas.programar(context, emptyList()) // sin sesión, sin alertas
                    scope.launch {
                        Push.olvidar() // este teléfono deja de recibir avisos de esta cuenta
                        runCatching { Repo.salir() }
                    }
                },
                    shape = RoundedCornerShape(14.dp)) { Text("Cerrar sesión") }
            },
            dismissButton = { TextButton(onClick = { confirmandoSalida = false }) { Text("Cancelar", color = TextoSuave) } }
        )
    }
}

@Composable
private fun DialogosSesion(
    saliendoDeTodo: Boolean, eliminando: Boolean, onCerrar: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    if (saliendoDeTodo) AlertDialog(
        onDismissRequest = onCerrar, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text("¿Salir de todos lados?", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = { Text("Se cierra tu sesión en todos los teléfonos y computadoras, incluido este. Tus datos quedan guardados.", color = TextoSuave) },
        confirmButton = {
            Button(shape = RoundedCornerShape(14.dp), onClick = {
                onCerrar()
                Alertas.programar(context, emptyList())
                scope.launch { Push.olvidar(); runCatching { Repo.salirDeTodo() } }
            }) { Text("Cerrar en todos") }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar", color = TextoSuave) } }
    )
    if (eliminando) {
        var texto by remember { mutableStateOf("") }
        var trabajando by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!trabajando) onCerrar() }, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
            title = { Text("¿Eliminar tu cuenta?", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Se borran para siempre tus notas, horario, tareas, amigos y todo lo demás. No se puede deshacer. Escribe ELIMINAR para confirmar.",
                        color = TextoSuave)
                    OutlinedTextField(texto, { texto = it.uppercase() }, singleLine = true, placeholder = { Text("ELIMINAR") }, shape = RoundedCornerShape(14.dp))
                    error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
                }
            },
            confirmButton = {
                Button(
                    enabled = texto == "ELIMINAR" && !trabajando, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colores.rojo),
                    onClick = {
                        scope.launch {
                            trabajando = true; error = null
                            Alertas.programar(context, emptyList())
                            Push.olvidar()
                            runCatching { Repo.eliminarMiCuenta() }.onFailure { error = "No se pudo eliminar. Intenta otra vez." }
                            trabajando = false
                        }
                    }
                ) { Text(if (trabajando) "Eliminando…" else "Eliminar para siempre") }
            },
            dismissButton = { TextButton(enabled = !trabajando, onClick = onCerrar) { Text("Cancelar", color = TextoSuave) } }
        )
    }
}

private fun fecha(iso: String): String = runCatching {
    Instant.parse(iso).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d 'de' MMMM", Locale.forLanguageTag("es-DO")))
}.getOrDefault("—")

@Composable
private fun Numero(modifier: Modifier, valor: String, etiqueta: String) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(valor, color = AzulNoche, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(etiqueta, color = TextoSuave, fontSize = 13.sp)
    }
}

/** Una barra por período: se llena según los créditos aprobados de ese período. */
@Composable
private fun BarraPorPeriodos(materias: List<Materia>, estados: Map<String, String>) {
    val periodos = materias.groupBy { it.cuatrimestre }.toSortedMap().values.toList()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        periodos.forEach { lista ->
            val total = lista.sumOf { it.creditos }.coerceAtLeast(1)
            val hecho = lista.filter { estados[it.id] == "aprobada" }.sumOf { it.creditos }
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(colores.tarjetaAlta)) {
                Box(Modifier.fillMaxWidth(hecho.toFloat() / total).fillMaxHeight().background(Indigo))
            }
        }
    }
}

@Composable
private fun FilaAjuste(icono: ImageVector, titulo: String, subtitulo: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(colores.tarjetaAlta, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = Indigo, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = AzulNoche, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (subtitulo.isNotBlank()) Text(subtitulo, color = TextoSuave, fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TextoSuave)
    }
}

@Composable
private fun FilaInterruptor(icono: ImageVector, titulo: String, subtitulo: String, activo: Boolean, onCambio: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onCambio(!activo) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).background(colores.tarjetaAlta, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(icono, null, tint = Indigo, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = AzulNoche, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitulo, color = TextoSuave, fontSize = 13.sp)
        }
        Switch(
            checked = activo, onCheckedChange = onCambio,
            colors = SwitchDefaults.colors(checkedTrackColor = Indigo, checkedThumbColor = Color.White)
        )
    }
}

@Composable
private fun Punto(emoji: String, titulo: String, texto: String) {
    Row {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(titulo, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(texto, color = TextoSuave, fontSize = 13.sp)
        }
    }
}
