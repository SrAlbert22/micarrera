package com.example.myapplication.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.CarreraVocacional
import com.example.myapplication.PreguntaVocacional
import com.example.myapplication.Recomendacion
import com.example.myapplication.Repo
import com.example.myapplication.Universidad
import com.example.myapplication.Vocacional
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Fase { INICIO, PREGUNTAS, RESULTADO }

private class DatosTest(
    val preguntas: List<PreguntaVocacional>,
    val carreras: List<CarreraVocacional>,
    val universidades: Map<String, Universidad>
)

// Las últimas respuestas se guardan en el teléfono ("id:valor,id:valor…") para volver a ver el resultado.
private fun guardarRespuestas(context: Context, r: Map<Int, Int>) =
    context.getSharedPreferences("vocacional", Context.MODE_PRIVATE).edit()
        .putString("respuestas", r.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()

private fun leerRespuestas(context: Context): Map<Int, Int>? =
    context.getSharedPreferences("vocacional", Context.MODE_PRIVATE).getString("respuestas", null)
        ?.split(",")?.mapNotNull { par -> par.split(":").takeIf { it.size == 2 }?.let { (a, b) -> a.toIntOrNull()?.let { it to (b.toIntOrNull() ?: 0) } } }
        ?.toMap()?.takeIf { it.isNotEmpty() }

/**
 * "¿Qué debo estudiar?": 30 preguntas sobre lo que te gusta hacer → tu perfil (RIASEC),
 * las carreras que más encajan y en qué universidades de RD estudiarlas. No necesita cuenta.
 */
@Composable
fun TestVocacionalScreen(onCerrar: () -> Unit, onCrearCuenta: (() -> Unit)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var datos by remember { mutableStateOf<DatosTest?>(null) }
    var error by remember { mutableStateOf(false) }
    var fase by remember { mutableStateOf(Fase.INICIO) }
    var actual by remember { mutableIntStateOf(0) }
    val respuestas = remember { mutableStateMapOf<Int, Int>() }
    var avanzando by remember { mutableStateOf(false) }
    val guardadas = remember { leerRespuestas(context) }

    LaunchedEffect(Unit) {
        runCatching {
            DatosTest(
                Repo.preguntasVocacionales(),
                Repo.carrerasVocacionales(),
                Repo.universidades().filter { it.siglas != null }.associateBy { it.siglas!! }
            )
        }.onSuccess { datos = it }.onFailure { error = true }
    }

    BackHandler {
        if (fase == Fase.PREGUNTAS) { if (actual > 0) actual-- else fase = Fase.INICIO } else onCerrar()
    }

    fun empezar() { respuestas.clear(); actual = 0; fase = Fase.PREGUNTAS }

    fun responder(valor: Int) {
        val d = datos ?: return
        if (avanzando) return
        respuestas[d.preguntas[actual].id] = valor
        avanzando = true
        scope.launch {
            delay(160) // que se vea la opción marcada antes de pasar
            if (actual + 1 < d.preguntas.size) actual++
            else { guardarRespuestas(context, respuestas.toMap()); fase = Fase.RESULTADO }
            avanzando = false
        }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        val d = datos
        when {
            error -> {
                Encabezado(onCerrar)
                Text("No se pudo cargar el test. Revisa tu internet e intenta otra vez.",
                    color = colores.rojo, modifier = Modifier.padding(20.dp))
            }
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            fase == Fase.INICIO -> Inicio(
                total = d.preguntas.size, onEmpezar = ::empezar, onCerrar = onCerrar,
                onVerAnterior = guardadas?.let { g -> { respuestas.clear(); respuestas.putAll(g); fase = Fase.RESULTADO } }
            )
            fase == Fase.PREGUNTAS -> Pregunta(
                pregunta = d.preguntas[actual], numero = actual + 1, total = d.preguntas.size,
                elegida = respuestas[d.preguntas[actual].id], onResponder = ::responder,
                onAtras = { if (actual > 0) actual-- else fase = Fase.INICIO }
            )
            else -> Resultado(d, respuestas.toMap(), onRepetir = ::empezar, onCerrar = onCerrar, onCrearCuenta = onCrearCuenta)
        }
    }
}

@Composable
private fun Encabezado(onAtras: () -> Unit, cerrar: Boolean = true, contenido: @Composable ColumnScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAtras) {
            Icon(if (cerrar) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack,
                if (cerrar) "Cerrar" else "Atrás", tint = colores.texto)
        }
        Column(Modifier.weight(1f), content = contenido)
    }
}

@Composable
private fun TarjetaDegradado(contenido: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(DegradadoMarca).padding(horizontal = 22.dp, vertical = 24.dp),
        content = contenido
    )
}

@Composable
private fun Inicio(total: Int, onEmpezar: () -> Unit, onCerrar: () -> Unit, onVerAnterior: (() -> Unit)?) {
    Encabezado(onCerrar)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TarjetaDegradado {
            Text("🧭", fontSize = 52.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(8.dp))
            Text("¿Qué debo estudiar?", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(6.dp))
            Text(
                "Responde $total preguntas rápidas sobre lo que te gusta hacer. Al final te decimos qué carreras encajan " +
                    "contigo y en qué universidades de RD puedes estudiarlas.",
                color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
        }
        TarjetaMC(Modifier.fillMaxWidth()) {
            Punto("⏱️", "Unos 5 minutos", "Una pregunta a la vez. Puedes volver atrás.")
            HorizontalDivider(color = colores.borde)
            Punto("🎯", "No hay respuestas buenas ni malas", "Piensa en lo que disfrutas, no en lo que se te da bien.")
            HorizontalDivider(color = colores.borde)
            Punto("🔓", "No necesitas cuenta", "Tus respuestas se quedan en este teléfono.")
        }
        Button(onClick = onEmpezar, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Empezar el test", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (onVerAnterior != null) TextButton(onClick = onVerAnterior, modifier = Modifier.fillMaxWidth()) {
            Text("Ver mi último resultado", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Punto(emoji: String, titulo: String, detalle: String) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, color = colores.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(detalle, color = colores.textoSuave, fontSize = 14.sp)
        }
    }
}

@Composable
private fun Pregunta(
    pregunta: PreguntaVocacional, numero: Int, total: Int, elegida: Int?,
    onResponder: (Int) -> Unit, onAtras: () -> Unit
) {
    val avance by animateFloatAsState((numero - 1f) / total, label = "avance")
    Encabezado(onAtras, cerrar = false) {
        Text("Pregunta $numero de $total", color = colores.textoSuave, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(colores.tarjetaAlta)) {
            Box(Modifier.fillMaxWidth(avance).fillMaxHeight().background(colores.acento))
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.padding(start = 6.dp, end = 6.dp, top = 24.dp, bottom = 18.dp)) {
            Text("¿Qué tanto te gustaría…", color = colores.textoSuave, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("${pregunta.texto}?", color = colores.texto, fontSize = 25.sp, fontWeight = FontWeight.Bold, lineHeight = 31.sp)
        }
        Vocacional.RESPUESTAS.forEach { r ->
            val sel = elegida == r.valor
            TarjetaMC(
                Modifier.fillMaxWidth(),
                borde = if (sel) colores.acento else colores.borde,
                fondo = if (sel) colores.acentoSuave else colores.tarjeta,
                onClick = { onResponder(r.valor) }
            ) {
                Row(Modifier.padding(horizontal = 18.dp, vertical = 15.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(r.emoji, fontSize = 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Text(r.texto, color = colores.texto, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun Resultado(
    datos: DatosTest, respuestas: Map<Int, Int>,
    onRepetir: () -> Unit, onCerrar: () -> Unit, onCrearCuenta: (() -> Unit)?
) {
    val context = LocalContext.current
    val p = remember(respuestas) { Vocacional.puntajes(datos.preguntas, respuestas) }
    val orden = remember(p) { Vocacional.ranking(p) }
    val carreras = remember(p) { Vocacional.recomendar(datos.carreras, p) }
    val donde = remember(carreras) { Vocacional.universidadesTop(carreras) }
    val (t1, t2) = Vocacional.tipo(orden[0]) to Vocacional.tipo(orden[1])
    val uni = { s: String -> datos.universidades[s] ?: Universidad(id = s, nombre = s, siglas = s) }
    val nadaLeGusta = (p[orden[0]] ?: 0.0) == 0.0

    Encabezado(onCerrar) { Text("Tu resultado", color = colores.texto, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            TarjetaDegradado {
                Text(t1.emoji + t2.emoji, fontSize = 40.sp)
                Spacer(Modifier.height(6.dp))
                Text("Tu perfil", color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text("${t1.nombre} + ${t2.nombre}", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (nadaLeGusta) "Casi nada te llamó la atención. Repite el test con calma pensando en lo que disfrutas en tu tiempo libre."
                    else "${t1.descripcion} ${t2.descripcion.replace("Te gusta", "También te gusta")}",
                    color = Color.White.copy(alpha = 0.88f), fontSize = 15.sp, lineHeight = 22.sp
                )
            }
        }

        item { Seccion("Tus intereses", Modifier.padding(start = 4.dp, top = 4.dp)) }
        item {
            TarjetaMC(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    orden.forEachIndexed { i, letra ->
                        val t = Vocacional.tipo(letra)
                        val valor = (p[letra] ?: 0.0).toFloat()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${t.emoji} ${t.nombre}", color = colores.texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.width(132.dp), maxLines = 1)
                            Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(50)).background(colores.tarjetaAlta)) {
                                Box(Modifier.fillMaxWidth(valor).fillMaxHeight().clip(RoundedCornerShape(50))
                                    .background(if (i < 2) colores.acento else colores.acento.copy(alpha = 0.45f)))
                            }
                            Text("${(valor * 100).toInt()}%", color = colores.textoSuave, fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, modifier = Modifier.width(44.dp))
                        }
                    }
                }
            }
        }

        item { Seccion("Carreras para ti", Modifier.padding(start = 4.dp, top = 4.dp)) }
        items(carreras, key = { it.carrera.id }) { r -> TarjetaCarrera(r, uni) }

        if (donde.isNotEmpty()) {
            item { Seccion("Dónde estudiar", Modifier.padding(start = 4.dp, top = 4.dp)) }
            item {
                TarjetaMC(Modifier.fillMaxWidth()) {
                    donde.forEachIndexed { i, (siglas, cuantas) ->
                        if (i > 0) HorizontalDivider(color = colores.borde)
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            LogoUniversidad(uni(siglas), 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(uni(siglas).nombre, color = colores.texto, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Ofrece $cuantas de tus ${carreras.size} carreras", color = colores.textoSuave, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Este test es una guía, no una decisión final. Las carreras de cada universidad pueden cambiar: " +
                    "confírmalas en su página y, si puedes, habla con un orientador.",
                color = colores.textoSuave, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
        if (onCrearCuenta != null) item {
            Button(onClick = onCrearCuenta, modifier = Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(16.dp)) {
                Text("Crear mi cuenta en MiCarrera", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f)) {
                    BotonAncho("Compartir", Icons.Default.Share) {
                        val texto = "🧭 Hice el test vocacional de MiCarrera: soy ${t1.emoji} ${t1.nombre} + ${t2.emoji} ${t2.nombre}" +
                            " y me salió ${carreras.firstOrNull()?.carrera?.nombre}. ¿Y a ti?\n👉 https://micarrera-beta.vercel.app/?test"
                        val enviar = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto)
                        context.startActivity(Intent.createChooser(enviar, "Compartir mi resultado"))
                    }
                }
                Box(Modifier.weight(1f)) { BotonAncho("Repetir", Icons.Default.Refresh, onRepetir) }
            }
        }
    }
}

@Composable
private fun BotonAncho(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, colores.borde),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = colores.tarjeta, contentColor = colores.texto)
    ) {
        Icon(icono, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(texto, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TarjetaCarrera(r: Recomendacion, uni: (String) -> Universidad) {
    val c = r.carrera
    TarjetaMC(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(c.emoji, fontSize = 28.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.nombre, color = colores.texto, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(c.descripcion, color = colores.textoSuave, fontSize = 14.sp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${r.afinidad}%", color = colores.acento, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("afinidad", color = colores.textoSuave, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.universidades.forEach { s ->
                    val u = uni(s)
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).background(colores.tarjetaAlta)
                            .padding(start = if (u.logoUrl != null) 3.dp else 10.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (u.logoUrl != null) {
                            LogoUniversidad(u, 22.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(s, color = colores.texto, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = if (u.logoUrl != null) 0.dp else 3.dp))
                    }
                }
            }
        }
    }
}
