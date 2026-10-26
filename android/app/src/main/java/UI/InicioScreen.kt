package com.example.myapplication.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.myapplication.Alertas
import com.example.myapplication.Push
import com.example.myapplication.Repo
import com.example.myapplication.Universidad
import kotlinx.coroutines.launch

/** Ícono de gráfica de barras (el de Material, que no viene en el paquete básico de íconos). */
private val IconoGrafica: ImageVector = ImageVector.Builder("grafica", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(5f, 9.2f); horizontalLineToRelative(3f); verticalLineTo(19f); horizontalLineTo(5f); close()
        moveTo(10.6f, 5f); horizontalLineToRelative(2.8f); verticalLineToRelative(14f); horizontalLineToRelative(-2.8f); close()
        moveTo(16.2f, 13f); horizontalLineTo(19f); verticalLineToRelative(6f); horizontalLineToRelative(-2.8f); close()
    }
}.build()

/** Ícono de libro abierto (Estudio). */
private val IconoLibro: ImageVector = ImageVector.Builder("libro", 24.dp, 24.dp, 24f, 24f).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 5f); lineTo(11f, 6.6f); lineTo(11f, 19.6f); lineTo(3f, 18f); close()
        moveTo(13f, 6.6f); lineTo(21f, 5f); lineTo(21f, 18f); lineTo(13f, 19.6f); close()
    }
}.build()

private enum class Pestana(val titulo: String, val icono: ImageVector) {
    HORARIO("Horario", Icons.Default.DateRange),
    ESTUDIO("Estudio", IconoLibro),
    PENSUM("Pensum", Icons.AutoMirrored.Filled.List),
    ESTADISTICAS("Estadísticas", IconoGrafica),
    CUENTA("Cuenta", Icons.Default.Person)
}

/** "Ahora no" en la pantalla de nombre: no la repetimos hasta que se vuelva a abrir la app. */
private var nombreOmitido = false

@Composable
fun InicioScreen() {
    val scope = rememberCoroutineScope()
    var cargando by remember { mutableStateOf(true) }
    var carreraId by remember { mutableStateOf<String?>(null) }
    var pestana by rememberSaveable { mutableStateOf(Pestana.HORARIO) }
    var conectandoPortal by remember { mutableStateOf(false) }
    var pedirContrasena by remember { mutableStateOf(false) }
    var verAmigos by remember { mutableStateOf(false) }
    var haciendoTest by remember { mutableStateOf(false) }
    var reinicioCinco by remember { mutableIntStateOf(0) }
    val botonesMenu = remember { mutableStateMapOf<String, Rect>() }
    var tutorialVisto by remember { mutableStateOf(true) }
    var universidad by remember { mutableStateOf<Universidad?>(null) }
    var faltaNombre by remember { mutableStateOf<String?>(null) } // no null = pedir nombre (valor: @usuario actual o "")

    val context = LocalContext.current
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

    LaunchedEffect(Unit) {
        val perfil = runCatching { Repo.perfil() }.getOrNull()
        carreraId = perfil?.carreraId
        tutorialVisto = perfil?.tutorialVisto ?: true
        // Sin nombre tus amigos no te encuentran: lo pedimos una vez cada vez que se abre la app ("Ahora no" lo deja para después)
        if (perfil != null && perfil.nombre.isNullOrBlank() && !nombreOmitido) faltaNombre = perfil.usuario.orEmpty()
        cargando = false
    }
    // Notificaciones: pide permiso (Android 13+) y programa las alertas con el horario actual.
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Alertas.tienePermiso(context)) {
            pedirPermiso.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        runCatching { Alertas.programar(context, Repo.horario()) }
        // Entró con un código (sin contraseña): le pedimos que cree una.
        pedirContrasena = debePedirContrasena(context)
        // Avisos del servidor: registra este teléfono.
        Push.crearCanal(context)
        Push.registrar()
    }

    // La universidad de tu carrera (para el enlace del aula virtual en Estudio)
    LaunchedEffect(carreraId) { universidad = carreraId?.let { runCatching { Repo.carreraDetalle(it)?.universidades }.getOrNull() } }

    if (cargando) {
        Box(Modifier.fillMaxSize().background(FondoApp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    faltaNombre?.let { usuario ->
        CompletarPerfilScreen(usuarioActual = usuario.ifEmpty { null }, onListo = { nombreOmitido = true; faltaNombre = null })
        return
    }

    val id = carreraId
    if (id == null) {
        ElegirCarreraScreen(onElegida = { carreraId = it })
        return
    }

    if (haciendoTest) {
        TestVocacionalScreen(onCerrar = { haciendoTest = false })
        return
    }

    if (verAmigos) {
        AmigosScreen(onCerrar = { verAmigos = false })
        return
    }

    if (conectandoPortal) {
        ConectarPortalScreen(
            carreraId = id,
            onCerrar = { conectandoPortal = false },
            onVerPensum = { conectandoPortal = false; pestana = Pestana.PENSUM },
            onVerHorario = { conectandoPortal = false; pestana = Pestana.HORARIO }
        )
        return
    }

    val cambiarCarrera: () -> Unit = {
        scope.launch { runCatching { Repo.guardarCarrera(null) }; carreraId = null }
    }

    if (pedirContrasena) DialogoContrasena(primeraVez = true) {
        pedirContrasena = false
        pedirContrasenaAlEntrar(context, false) // no insistimos; se puede crear luego en Cuenta
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = FondoApp,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            Column {
                HorizontalDivider(color = colores.borde)
                NavigationBar(containerColor = Tarjeta, tonalElevation = 0.dp) {
                    val c = NavigationBarItemDefaults.colors(
                        selectedIconColor = Indigo, selectedTextColor = Indigo, indicatorColor = Lavanda,
                        unselectedIconColor = TextoSuave, unselectedTextColor = TextoSuave
                    )
                    Pestana.entries.forEach { p ->
                        NavigationBarItem(
                            selected = pestana == p, onClick = { pestana = p }, colors = c,
                            modifier = Modifier.onGloballyPositioned { botonesMenu[p.name] = it.boundsInRoot() },
                            icon = { Icon(p.icono, null) }, label = { Text(p.titulo) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (pestana) {
                Pestana.HORARIO -> HorarioScreen(onConectarPortal = { conectandoPortal = true }, onAmigos = { verAmigos = true }, onIrA = { pestana = Pestana.valueOf(it) })
                Pestana.ESTUDIO -> EstudioScreen(universidad)
                Pestana.PENSUM -> PensumScreen(id, onActualizarPortal = { conectandoPortal = true }, onCambiarCarrera = cambiarCarrera)
                Pestana.ESTADISTICAS -> EstadisticasScreen(id)
                Pestana.CUENTA -> CuentaScreen(id, onCambiarCarrera = cambiarCarrera, onConectarPortal = { conectandoPortal = true },
                    onAmigos = { verAmigos = true }, onTest = { haciendoTest = true },
                    onCinco = { reiniciarCinco(context); reinicioCinco++ })
            }
        }
    }
    // 5inco: el recorrido la primera vez y los consejos después
    GuiaCinco(
        pestana = pestana.name, irA = { pestana = Pestana.valueOf(it) }, reinicio = reinicioCinco, botones = botonesMenu,
        tutorialVisto = tutorialVisto, onTutorialVisto = { tutorialVisto = true; scope.launch { runCatching { Repo.marcarTutorialVisto() } } }
    )
    }
}
