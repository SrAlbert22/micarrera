package com.example.myapplication.ui

import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Alertas
import com.example.myapplication.Repo
import com.example.myapplication.nombreVersionInstalada
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

// 5inco: la mascota de MiCarrera (un libro con carita). Igual que cinco.tsx en la web: mismo dibujo, mismas frases.

private val TAPA = Color(0xFF4F56C9)
private val TAPA_OSCURA = Color(0xFF3B41A8)
private val PAGINA = Color(0xFFFFFDF6)
private val TINTA = Color(0xFF1F2244)
private fun ruta(d: String) = PathParser().parsePathString(d).toPath()
private val PAGINA_IZQ = ruta("M60 34 C45 26 28 26 18 30 L18 92 C28 88 45 88 60 96 Z")
private val PAGINA_DER = ruta("M60 34 C75 26 92 26 102 30 L102 92 C92 88 75 88 60 96 Z")
private val MARCADOR = ruta("M84 16 H96 V40 L90 35 L84 40 Z")
private val SONRISA = ruta("M50 71 Q60 81 70 71")
private val BRAZO_IZQ = ruta("M16 64 Q4 58 6 46")
private val BRAZO_DER = ruta("M104 64 Q114 70 112 80")

/** El dibujo de Cinco (en una caja de 120×120 escalada a [tam]). */
@Composable
fun Cinco(tam: Dp = 96.dp, hablando: Boolean = false, saludando: Boolean = false, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "cinco")
    val rebote by t.animateFloat(0f, -3f, infiniteRepeatable(tween(1200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rebote")
    val parpadeo by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "parpadeo")
    val boca by t.animateFloat(0.35f, 1f, infiniteRepeatable(tween(200), RepeatMode.Reverse), label = "boca")
    val brazo by t.animateFloat(-14f, 20f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "brazo")
    val ojos = if (parpadeo in 0.92f..0.98f) 0.1f else 1f

    Canvas(modifier.size(tam)) {
        scale(size.width / 120f, size.height / 120f, pivot = Offset.Zero) {
            drawOval(Color.Black.copy(alpha = 0.18f), Offset(26f, 107f), Size(68f, 10f))
            translate(top = rebote) {
                drawOval(TAPA_OSCURA, Offset(36f, 98f), Size(18f, 10f))
                drawOval(TAPA_OSCURA, Offset(66f, 98f), Size(18f, 10f))
                rotate(if (saludando) brazo else 0f, pivot = Offset(16f, 64f)) {
                    drawPath(BRAZO_IZQ, TAPA, style = Stroke(6f, cap = StrokeCap.Round))
                    drawCircle(TAPA, 5.5f, Offset(6f, 44f))
                }
                drawPath(BRAZO_DER, TAPA, style = Stroke(6f, cap = StrokeCap.Round))
                drawCircle(TAPA, 5.5f, Offset(112f, 82f))
                drawRoundRect(TAPA, Offset(12f, 30f), Size(96f, 70f), CornerRadius(12f))
                drawPath(PAGINA_IZQ, PAGINA)
                drawPath(PAGINA_DER, PAGINA)
                drawLine(Color(0xFFD6D9F2), Offset(60f, 34f), Offset(60f, 96f), 2f)
                drawLine(Color(0xFFE3E6F7), Offset(26f, 38f), Offset(44f, 38f), 2.5f, StrokeCap.Round)
                drawLine(Color(0xFFE3E6F7), Offset(76f, 38f), Offset(94f, 38f), 2.5f, StrokeCap.Round)
                drawPath(MARCADOR, Color(0xFFF5B83D))
                cinco(Offset(90f, 26f))
                // Ojos (parpadean)
                scale(1f, ojos, pivot = Offset(60f, 57f)) {
                    drawOval(TINTA, Offset(34f, 49.5f), Size(12f, 15f))
                    drawOval(TINTA, Offset(74f, 49.5f), Size(12f, 15f))
                    drawCircle(Color.White, 2.2f, Offset(42.2f, 54.2f))
                    drawCircle(Color.White, 2.2f, Offset(82.2f, 54.2f))
                }
                drawOval(Color(0xFFFF9DB5).copy(alpha = 0.65f), Offset(24f, 66.4f), Size(12f, 7.2f))
                drawOval(Color(0xFFFF9DB5).copy(alpha = 0.65f), Offset(84f, 66.4f), Size(12f, 7.2f))
                if (hablando) {
                    scale(1f, boca, pivot = Offset(60f, 75f)) {
                        drawOval(TINTA, Offset(52f, 68.5f), Size(16f, 13f))
                        drawOval(Color(0xFFFF7A93), Offset(55.5f, 75.9f), Size(9f, 5.2f))
                    }
                } else drawPath(SONRISA, TINTA, style = Stroke(3.5f, cap = StrokeCap.Round))
            }
        }
    }
}

/** El "5" del marcador, hecho con líneas (no depende de fuentes). */
private fun DrawScope.cinco(c: Offset) {
    val s = Stroke(2.2f, cap = StrokeCap.Round)
    val p = ruta("M${c.x + 3} ${c.y - 5} H${c.x - 2.5f} L${c.x - 3} ${c.y} Q${c.x + 4} ${c.y - 1.5f} ${c.x + 3.2f} ${c.y + 3} Q${c.x + 2} ${c.y + 6.5f} ${c.x - 3.2f} ${c.y + 4.5f}")
    drawPath(p, Color.White, style = s)
}

// ---------- Lo que dice Cinco ----------

private class PasoCinco(val texto: String, val pestana: String? = null)

private val RECORRIDO = listOf(
    PasoCinco("¡Hola! Mi nombre es 5inco 👋 Soy una mascota creada por Albert Silvestre y estaré aquí para ayudarte en todo lo que necesites. Te enseño a usar la app en un minuto."),
    PasoCinco("📅 Tu horario: toca «Agregar» para poner tus clases una por una, o «Foto o PDF» y yo leo tu horario solito. Te aviso antes de cada clase ⏰", "HORARIO"),
    PasoCinco("👥 El botón «Amigos» (arriba) sirve para buscar a tus panas por su nombre o @usuario y ver qué clases tienen juntos.", "HORARIO"),
    PasoCinco("📖 En Estudio tienes tus tareas pendientes, el modo concentración y el reto con tus amigos. Conecta tu aula virtual y las tareas te llegan solas.", "ESTUDIO"),
    PasoCinco("📚 Tu pensum: toca una materia para marcarla. Un toque = en curso, otro toque = aprobada ✅. El filtro «Disponibles» te dice qué ya puedes inscribir.", "PENSUM"),
    PasoCinco("🔄 Con «Actualizar» conectas el portal de tu universidad y traigo tus notas e índice. Y si tu carrera no estaba, en «Cambiar» puedes agregarla con tu pensum 📄", "PENSUM"),
    PasoCinco("📈 En Estadísticas ves tu índice, tus mejores materias, tus horas de clase y cuándo te gradúas 🎓", "ESTADISTICAS"),
    PasoCinco("⚙️ En Cuenta creas tu @usuario, invitas amigos, cambias el tema a claro u oscuro y ajustas tus alertas.", "CUENTA"),
    PasoCinco("¡Listo! Ya sabes usar MiCarrera. Si me necesitas, tócame aquí en la esquina: te doy consejos, recibo tus comentarios y te ayudo con lo que sea 😉"),
)

private val CONSEJOS = mapOf(
    "HORARIO" to listOf(
        "¿Sabías que puedes subir una foto o PDF de tu horario y yo lo lleno por ti? 📸",
        "Toca una clase para agregarle un apunte, como “traer calculadora” ✏️",
        "Te aviso antes de cada clase, y si tienes una tarea pendiente de esa materia te la recuerdo ⏰",
        "Toca “Amigos” para ver qué clases tienes con tus panas 👥",
    ),
    "ESTUDIO" to listOf(
        "Conecta tu aula virtual y te aviso cuando te dejen una tarea nueva 🎓",
        "Prueba el modo concentración: 25 minutos sin el teléfono y 5 de descanso 🧘",
        "Cada ronda de concentración suma al reto de la semana con tus amigos 🏆",
    ),
    "PENSUM" to listOf(
        "Toca una materia para cambiarla: pendiente → en curso → aprobada ✅",
        "El filtro “Disponibles” te dice qué materias ya puedes inscribir ✨",
        "Con “Actualizar” traigo tus notas desde el portal de tu universidad 🎓",
    ),
    "ESTADISTICAS" to listOf(
        "Mientras más materias marques, mejor calculo cuándo te gradúas 🎓",
        "Las materias con menos de 80 te las pongo en “Para reforzar”. ¡Tú puedes! 💪",
    ),
    "CUENTA" to listOf(
        "Crea tu @usuario para que tus amigos te encuentren 👥",
        "¿Te gusta más la app clara? Cámbiala en Apariencia 🌗",
        "¿Tienes un pana que no sabe qué estudiar? Pásale el test “¿Qué debo estudiar?” 🧭",
    ),
)

// ---------- Preferencia local: ocultar a 5inco (el tutorial visto se guarda en la cuenta) ----------

private fun prefs(c: Context) = c.getSharedPreferences("cinco", Context.MODE_PRIVATE)
/** Para "5inco, tu guía" en Cuenta: lo vuelve a mostrar. */
fun reiniciarCinco(c: Context) = prefs(c).edit().putBoolean("oculto", false).apply()

/** Lo que muestra el globo de Cinco fuera del recorrido. */
private sealed interface Modo {
    data object Nada : Modo
    data object Menu : Modo
    data class Consejo(val texto: String) : Modo
    data class Escribir(val clase: String) : Modo // comentario | problema
    data object Gracias : Modo
    data object Borrar : Modo
    data object Eliminar : Modo
}

/**
 * Cinco en pantalla:
 *  - La PRIMERA vez de cada cuenta hace el recorrido (cambia de pestaña e ilumina su botón del menú).
 *    Se marca como visto en la cuenta apenas empieza, así no se repite en ningún dispositivo.
 *  - Después se queda en la esquina: al tocarlo abre el menú de ayuda (consejos, comentarios, problemas, borrar datos…).
 * [botones]: dónde está cada botón del menú (en la misma pantalla que este overlay).
 */
@Composable
fun GuiaCinco(
    pestana: String, irA: (String) -> Unit, reinicio: Int, botones: Map<String, Rect>,
    tutorialVisto: Boolean, onTutorialVisto: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var oculto by remember { mutableStateOf(prefs(context).getBoolean("oculto", false)) }
    var paso by remember { mutableStateOf<Int?>(if (tutorialVisto) null else 0) }
    var modo by remember { mutableStateOf<Modo>(Modo.Nada) }
    var indice by remember { mutableIntStateOf(0) }
    var voz by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf("") }
    var confirmacion by remember { mutableStateOf("") }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var avisado by remember { mutableStateOf(false) }

    LaunchedEffect(tutorialVisto) { if (!tutorialVisto && paso == null && !avisado) paso = 0 }
    LaunchedEffect(reinicio) { if (reinicio > 0) { oculto = false; paso = 0; modo = Modo.Nada } }
    // El recorrido cuenta como visto apenas empieza: aunque cierren la app a mitad, no se repite
    LaunchedEffect(paso, tutorialVisto) { if (paso == 0 && !tutorialVisto && !avisado) { avisado = true; onTutorialVisto() } }

    // Voz del teléfono (español; un poquito más aguda: es un libro chiquito)
    val tts = remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        var motor: TextToSpeech? = null
        motor = TextToSpeech(context) { ok ->
            if (ok == TextToSpeech.SUCCESS) motor?.apply {
                language = listOf(Locale("es", "DO"), Locale("es", "US"), Locale("es", "MX"), Locale("es"))
                    .firstOrNull { isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE } ?: Locale("es")
                setPitch(1.25f); setSpeechRate(1.05f)
                tts.value = this
            }
        }
        onDispose { motor.stop(); motor.shutdown() }
    }
    fun decir(texto: String) {
        val limpio = texto.replace("5inco", "Cinco").replace(Regex("[\\p{So}\\p{Cn}\\uFE0F\\u200D“”«»]"), "").replace("→", ", luego")
        tts.value?.speak(limpio, TextToSpeech.QUEUE_FLUSH, null, "cinco")
        voz = true
    }
    fun callar() { tts.value?.stop(); voz = false }

    val enRecorrido = paso != null
    val texto: String? = paso?.let { RECORRIDO[it].texto } ?: when (val m = modo) {
        Modo.Nada -> null
        Modo.Menu -> "¿En qué te ayudo? 😊"
        is Modo.Consejo -> m.texto
        is Modo.Escribir -> if (m.clase == "problema") "Cuéntame qué pasó y dónde. Albert lo recibe al instante 🐞" else "¿Qué te gustaría que mejoremos o agreguemos? 💬"
        Modo.Gracias -> "¡Gracias! Ya le llegó a Albert. Si hace falta, te escribe a tu correo 💚"
        Modo.Borrar -> "¿Qué quieres borrar? Esto no se puede deshacer 🗑️"
        Modo.Eliminar -> "Se borrará tu cuenta con TODO: notas, horario, tareas y amigos. Escribe ELIMINAR para confirmar."
    }

    // Texto letra por letra (mientras, Cinco mueve la boca)
    var visibles by remember { mutableIntStateOf(0) }
    LaunchedEffect(texto) {
        visibles = 0
        val largo = texto?.length ?: 0
        while (visibles < largo) { delay(28); visibles = minOf(largo, visibles + 2) }
    }
    LaunchedEffect(voz) { if (voz) { delay(200); while (tts.value?.isSpeaking == true) delay(200); voz = false } }
    LaunchedEffect(paso) { paso?.let { RECORRIDO[it].pestana }?.let(irA) }

    fun cerrarRecorrido() { callar(); paso = null; modo = Modo.Nada }
    fun ir(nuevo: Modo) { callar(); error = null; modo = nuevo }
    fun consejo() {
        val lista = CONSEJOS[pestana] ?: return
        indice = (indice + 1) % lista.size
        ir(Modo.Consejo(lista[indice]))
    }
    fun enviar(clase: String) {
        scope.launch {
            trabajando = true; error = null
            runCatching { Repo.enviarSoporte(clase, mensaje, pestana.lowercase(), nombreVersionInstalada(context)) }
                .onSuccess { mensaje = ""; modo = Modo.Gracias }
                .onFailure { error = if (it.message.orEmpty().contains("límite")) "Ya enviaste muchos mensajes hoy. Intenta mañana." else "No se pudo enviar. Revisa tu internet." }
            trabajando = false
        }
    }
    fun borrar(que: String) {
        scope.launch {
            trabajando = true; error = null
            runCatching { if (que == "horario") Repo.borrarMiHorario() else Repo.reiniciarMiProgreso() }
                .onSuccess {
                    if (que == "horario") runCatching { Alertas.programar(context, emptyList()) }
                    modo = Modo.Consejo(if (que == "horario") "Listo, borré tu horario 🗑️" else "Listo, reinicié tu pensum 🗑️")
                }
                .onFailure { error = "No se pudo borrar. Intenta otra vez." }
            trabajando = false
        }
    }
    var confirmarBorrar by remember { mutableStateOf<String?>(null) }

    if (oculto && !enRecorrido) return
    val hablando = (texto != null && visibles < texto.length) || voz

    Box(Modifier.fillMaxSize()) {
        if (enRecorrido) {
            val foco = paso?.let { RECORRIDO[it].pestana }?.let { botones[it] }
            val densidad = LocalDensity.current
            Canvas(
                Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {} // bloquea toques detrás
            ) {
                drawRect(Color(0xFF040614).copy(alpha = 0.55f))
                foco?.let {
                    val m = with(densidad) { 6.dp.toPx() }
                    drawRoundRect(
                        Color(0xFFF5B83D), Offset(it.left - m, it.top - m), Size(it.width + 2 * m, it.height + 2 * m),
                        CornerRadius(with(densidad) { 18.dp.toPx() }), style = Stroke(with(densidad) { 3.dp.toPx() })
                    )
                }
            }
        }
        Column(
            Modifier.align(if (enRecorrido) Alignment.BottomCenter else Alignment.BottomEnd)
                .navigationBarsPadding().imePadding().padding(start = 12.dp, end = 12.dp, bottom = 88.dp)
                .widthIn(max = if (enRecorrido) 440.dp else 340.dp),
            horizontalAlignment = if (enRecorrido) Alignment.CenterHorizontally else Alignment.End
        ) {
            if (texto != null) {
                Column(
                    Modifier.fillMaxWidth().background(colores.tarjeta, RoundedCornerShape(22.dp))
                        .border(1.dp, colores.borde, RoundedCornerShape(22.dp)).padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("5inco", color = colores.acento, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { if (voz) callar() else decir(texto) }) { Text(if (voz) "⏹" else "🔊", fontSize = 18.sp) }
                        if (!enRecorrido) IconButton(onClick = { ir(Modo.Nada) }) {
                            Icon(Icons.Default.Close, "Cerrar", tint = colores.textoSuave)
                        }
                    }
                    Text(texto.take(visibles), color = colores.texto, fontSize = 15.sp, lineHeight = 21.sp,
                        modifier = Modifier.padding(end = 8.dp).heightIn(min = 22.dp))
                    Spacer(Modifier.height(10.dp))
                    Column(Modifier.padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (enRecorrido) Row(verticalAlignment = Alignment.CenterVertically) {
                            val p = paso!!
                            Text("${p + 1} de ${RECORRIDO.size}", color = colores.textoSuave, fontSize = 12.sp, modifier = Modifier.weight(1f))
                            if (p + 1 < RECORRIDO.size) TextButton(onClick = ::cerrarRecorrido) { Text("Saltar", color = colores.textoSuave) }
                            Button(onClick = { callar(); if (p + 1 < RECORRIDO.size) paso = p + 1 else cerrarRecorrido() }, shape = RoundedCornerShape(14.dp)) {
                                Text(if (p + 1 < RECORRIDO.size) "Siguiente" else "¡Listo!")
                            }
                        } else when (val m = modo) {
                            Modo.Menu -> {
                                OpcionCinco("💡", "Un consejo", onClick = ::consejo)
                                OpcionCinco("💬", "Enviar un comentario") { ir(Modo.Escribir("comentario")) }
                                OpcionCinco("🐞", "Reportar un problema") { ir(Modo.Escribir("problema")) }
                                OpcionCinco("🗑️", "Borrar mis datos") { ir(Modo.Borrar) }
                                OpcionCinco("📖", "Ver el tutorial otra vez") { ir(Modo.Nada); paso = 0 }
                                OpcionCinco("🙈", "Ocultar a 5inco", colores.textoSuave) {
                                    callar(); prefs(context).edit().putBoolean("oculto", true).apply(); oculto = true; modo = Modo.Nada
                                }
                            }
                            is Modo.Consejo -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { ir(Modo.Menu) }) { Text("Menú", color = colores.acento) }
                                OutlinedButton(onClick = ::consejo, shape = RoundedCornerShape(50)) { Text("Otro consejo", color = colores.texto, fontSize = 13.sp) }
                            }
                            is Modo.Escribir -> {
                                OutlinedTextField(
                                    mensaje, { if (it.length <= 2000) mensaje = it }, minLines = 3, maxLines = 6, shape = RoundedCornerShape(14.dp),
                                    placeholder = { Text(if (m.clase == "problema") "Ej. En Horario, al subir la foto sale un error…" else "Ej. Me gustaría que…") },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { ir(Modo.Menu) }) { Text("Volver", color = colores.textoSuave) }
                                    Button(enabled = mensaje.trim().length >= 3 && !trabajando, onClick = { enviar(m.clase) }, shape = RoundedCornerShape(14.dp)) { Text("Enviar") }
                                }
                            }
                            Modo.Gracias -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = { ir(Modo.Menu) }) { Text("Volver al menú", color = colores.acento) }
                            }
                            Modo.Borrar -> {
                                OpcionCinco("📅", if (trabajando) "Borrando…" else "Borrar todo mi horario") { if (!trabajando) confirmarBorrar = "horario" }
                                OpcionCinco("📚", "Reiniciar mi pensum (notas y estados)") { if (!trabajando) confirmarBorrar = "progreso" }
                                OpcionCinco("⚠️", "Eliminar mi cuenta", colores.rojo) { confirmacion = ""; ir(Modo.Eliminar) }
                                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { ir(Modo.Menu) }) { Text("Volver", color = colores.acento) }
                                }
                            }
                            Modo.Eliminar -> {
                                OutlinedTextField(confirmacion, { confirmacion = it.uppercase() }, singleLine = true, placeholder = { Text("ELIMINAR") },
                                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { ir(Modo.Borrar) }) { Text("Cancelar", color = colores.textoSuave) }
                                    Button(
                                        enabled = confirmacion == "ELIMINAR" && !trabajando, shape = RoundedCornerShape(14.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = colores.rojo),
                                        onClick = {
                                            scope.launch {
                                                trabajando = true; error = null
                                                runCatching { Repo.eliminarMiCuenta() }.onFailure { error = "No se pudo eliminar la cuenta. Intenta otra vez." }
                                                trabajando = false
                                            }
                                        }
                                    ) { Text("Eliminar para siempre") }
                                }
                            }
                            Modo.Nada -> {}
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Cinco(
                tam = if (enRecorrido) 110.dp else 64.dp, hablando = hablando, saludando = enRecorrido && paso == 0,
                modifier = Modifier.clickable(remember { MutableInteractionSource() }, null) {
                    if (!enRecorrido) ir(if (modo == Modo.Nada) Modo.Menu else Modo.Nada)
                }
            )
        }
    }

    confirmarBorrar?.let { que ->
        AlertDialog(
            onDismissRequest = { confirmarBorrar = null }, containerColor = colores.tarjeta, shape = RoundedCornerShape(28.dp),
            title = { Text(if (que == "horario") "¿Borrar todas tus clases?" else "¿Reiniciar tu pensum?", color = colores.texto, fontWeight = FontWeight.Bold) },
            text = {
                Text(if (que == "horario") "Se borra tu horario completo. No se puede deshacer."
                    else "Se borran tus notas y el estado de todas tus materias. No se puede deshacer.", color = colores.textoSuave)
            },
            confirmButton = {
                Button(onClick = { confirmarBorrar = null; borrar(que) }, shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = colores.rojo)) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrar = null }) { Text("Cancelar", color = colores.textoSuave) } }
        )
    }
}

@Composable
private fun OpcionCinco(emoji: String, texto: String, color: Color = colores.texto, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colores.tarjetaAlta).clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(texto, color = color, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
