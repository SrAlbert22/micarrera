package com.example.myapplication.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Alertas
import com.example.myapplication.CalculoAmigos
import com.example.myapplication.Clase
import com.example.myapplication.DIAS
import com.example.myapplication.HuecoLibre
import com.example.myapplication.Interaccion
import com.example.myapplication.Logro
import com.example.myapplication.Persona
import com.example.myapplication.compartirInvitacion
import com.example.myapplication.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.border
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun Avatar(p: Persona, tam: Dp = 48.dp) {
    Box(Modifier.size(tam).background(Indigo, CircleShape), contentAlignment = Alignment.Center) {
        Text(p.iniciales, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (tam.value * 0.38f).sp)
    }
}

/** Sección Amigos: tu @usuario, buscar, solicitudes, tus amigos y "Quizás conozcas". */
@Composable
fun AmigosScreen(onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var cargando by remember { mutableStateOf(true) }
    var miUsuario by remember { mutableStateOf<String?>(null) }
    var gente by remember { mutableStateOf<List<Persona>>(emptyList()) }
    var sugerencias by remember { mutableStateOf<List<Persona>>(emptyList()) }
    var busqueda by remember { mutableStateOf("") }
    var resultados by remember { mutableStateOf<List<Persona>>(emptyList()) }
    var enviadas by remember { mutableStateOf(setOf<String>()) }
    var abierto by remember { mutableStateOf<Persona?>(null) }
    var creandoUsuario by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var miId by remember { mutableStateOf<String?>(null) }
    var horarios by remember { mutableStateOf<Map<String, List<Clase>>>(emptyMap()) }
    var logros by remember { mutableStateOf<List<Logro>>(emptyList()) }
    var inters by remember { mutableStateOf<List<Interaccion>>(emptyList()) }
    var chocados by remember { mutableStateOf(setOf<String>()) }
    var ahora by remember { mutableStateOf(LocalDateTime.now()) }

    suspend fun recargar() {
        runCatching { Repo.perfil() }.getOrNull()?.let { miUsuario = it.usuario; miId = it.id }
        runCatching { gente = Repo.misAmigos() }
        runCatching { horarios = Repo.horariosDe(gente.filter { it.estado == "aceptada" }.map { it.id }) }
        runCatching { sugerencias = Repo.sugerencias() }
        runCatching { logros = Repo.logrosAmigos() }
        runCatching { inters = Repo.interacciones() }
        cargando = false
    }
    LaunchedEffect(Unit) { recargar() }
    // "Libres ahora" se actualiza solo cada minuto
    LaunchedEffect(Unit) { while (true) { delay(60_000); ahora = LocalDateTime.now() } }
    LaunchedEffect(busqueda) {
        val q = CalculoAmigos.limpiarUsuario(busqueda)
        if (q.length < 2) { resultados = emptyList(); return@LaunchedEffect }
        delay(350) // espera a que termine de escribir
        resultados = runCatching { Repo.buscarUsuarios(q) }.getOrDefault(emptyList())
    }

    fun accion(texto: String, bloque: suspend () -> Unit) {
        scope.launch {
            mensaje = runCatching { bloque() }.fold({ texto }, { "No se pudo completar. Intenta otra vez." })
            recargar()
        }
    }

    fun chocar(l: Logro) {
        chocados = chocados + (l.amigo + l.materia)
        accion("🙌 Le chocaste los 5 a ${l.nombreVisible}.") { Repo.chocarLos5(l.amigo, l.materia) }
    }
    fun responder(i: Interaccion, si: Boolean) {
        inters = inters.map { if (it.id == i.id) it.copy(respuesta = if (si) "si" else "no") else it }
        accion(if (si) "¡Genial! Le avisamos que vas." else "Le avisamos que esta vez no puedes.") { Repo.responderInvitacion(i.id, si) }
    }

    abierto?.let { p ->
        AmigoScreen(
            p,
            onVolver = { abierto = null },
            onInvitado = { mensaje = "📚 Invitación enviada a ${p.nombreVisible}."; abierto = null; scope.launch { recargar() } },
            onQuitar = { accion("Ya no son amigos.") { p.amistad?.let { Repo.quitarAmistad(it) } }; abierto = null },
            onBloquear = { accion("Bloqueaste a ${p.nombreVisible}.") { Repo.bloquear(p.id) }; abierto = null }
        )
        return
    }
    BackHandler(onBack = onCerrar)

    val recibidas = gente.filter { it.estado == "pendiente" && !it.enviadaPorMi }
    val amigos = gente.filter { it.estado == "aceptada" }
    val pendientes = gente.filter { it.estado == "pendiente" && it.enviadaPorMi }
    val relacionados = gente.map { it.id }.toSet()
    val porId = gente.associateBy { it.id }
    fun nombreDe(id: String) = porId[id]?.nombreVisible ?: "Un amigo"
    // ¿Quién está libre ahora? Solo amigos que comparten su horario
    val estados = amigos.filter { horarios[it.id].orEmpty().isNotEmpty() }
        .map { it to CalculoAmigos.estadoAhora(horarios[it.id].orEmpty(), ahora.dayOfWeek.value, ahora.hour * 60 + ahora.minute) }
    val libres = estados.filter { it.second.libre }
    val enClase = estados.filter { !it.second.libre }
    val hace7 = Instant.now().minusSeconds(7 * 86400)
    fun reciente(i: Interaccion) = runCatching { OffsetDateTime.parse(i.creada).toInstant().isAfter(hace7) }.getOrDefault(false)
    val recibidasInter = inters.filter { it.para == miId && reciente(it) }
    val invitacionesNuevas = recibidasInter.filter { it.tipo == "invitacion" && it.respuesta == null }
    val novedades = (recibidasInter.filter { it.tipo == "choca5" } +
        inters.filter { it.de == miId && it.tipo == "invitacion" && it.respuesta != null && reciente(it) })
        .sortedByDescending { it.creada }.take(8)

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        EncabezadoMarca("Amigos", "Conecta con quien comparte tus clases", alVolver = onCerrar)
        if (cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tu @usuario
            item {
                TarjetaMC(Modifier.fillMaxWidth(), onClick = { creandoUsuario = true }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Tu usuario", color = TextoSuave, fontSize = 13.sp)
                            Text(miUsuario?.let { "@$it" } ?: "Crea tu @usuario para que te encuentren",
                                color = if (miUsuario != null) AzulNoche else Indigo, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text(if (miUsuario != null) "Cambiar" else "Crear", color = Indigo, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Invitar amigos
            item {
                TarjetaMC(
                    Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.5f), fondo = Lavanda.copy(alpha = 0.35f),
                    onClick = { compartirInvitacion(context, miUsuario) }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💌", fontSize = 24.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Invitar amigos", color = Indigo, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                if (miUsuario != null) "Comparte tu enlace: al registrarse, te agregan como amigo con un toque."
                                else "Comparte MiCarrera. Crea tu @usuario para que te agreguen al registrarse.",
                                color = TextoSuave, fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Buscar
            item {
                OutlinedTextField(
                    value = busqueda, onValueChange = { busqueda = it },
                    placeholder = { Text("Buscar por nombre o @usuario…") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = TextoSuave) },
                    singleLine = true, shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            mensaje?.let { item { Text(it, color = VerdeOk, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp)) } }

            if (busqueda.isNotBlank()) {
                item { Seccion("Resultados", Modifier.padding(start = 4.dp)) }
                if (resultados.isEmpty()) item {
                    Text(if (CalculoAmigos.limpiarUsuario(busqueda).length < 2) "Escribe al menos 2 letras." else "No encontramos a nadie con ese usuario.",
                        color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(start = 4.dp))
                }
                item {
                    if (resultados.isNotEmpty()) TarjetaMC(Modifier.fillMaxWidth()) {
                        resultados.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = colores.borde)
                            FilaPersona(p, detalle = listOfNotNull(p.universidad, p.carrera).joinToString(" · ")) {
                                when {
                                    p.id in relacionados || p.id in enviadas -> Text("Enviada", color = TextoSuave, fontSize = 13.sp)
                                    else -> BotonAgregar { enviadas = enviadas + p.id; accion("Solicitud enviada a ${p.nombreVisible}.") { Repo.enviarSolicitud(p.id) } }
                                }
                            }
                        }
                    }
                }
            }

            // Solicitudes recibidas
            if (recibidas.isNotEmpty()) {
                item { Seccion("Solicitudes (${recibidas.size})", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.5f)) {
                        recibidas.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = colores.borde)
                            FilaPersona(p, detalle = "quiere ser tu amigo") {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { accion("Solicitud rechazada.") { Repo.quitarAmistad(p.amistad!!) } }) {
                                        Text("Rechazar", color = TextoSuave)
                                    }
                                    Button(onClick = { accion("¡Ahora eres amigo de ${p.nombreVisible}!") { Repo.aceptarSolicitud(p.amistad!!) } },
                                        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Aceptar") }
                                }
                            }
                        }
                    }
                }
            }

            // Invitaciones a estudiar
            if (invitacionesNuevas.isNotEmpty()) {
                item { Seccion("Te invitaron a estudiar 📚", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.5f)) {
                        invitacionesNuevas.forEachIndexed { n, i ->
                            if (n > 0) HorizontalDivider(color = colores.borde)
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(buildString {
                                    append(nombreDe(i.de)); append(" te invita a estudiar")
                                    i.materia?.let { append(" $it") }
                                    i.cuando?.let { append(" · ${cuandoTexto(it)}") }
                                }, color = AzulNoche, fontSize = 15.sp)
                                i.mensaje?.let { Text("“$it”", color = TextoSuave, fontSize = 14.sp) }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    TextButton(onClick = { responder(i, false) }) { Text("No puedo", color = TextoSuave) }
                                    Button(onClick = { responder(i, true) }, shape = RoundedCornerShape(12.dp)) { Text("¡Voy! 🙌") }
                                }
                            }
                        }
                    }
                }
            }

            // ¿Quién está libre ahora?
            if (estados.isNotEmpty()) {
                item { Seccion("¿Quién está libre ahora?", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (libres.isEmpty()) Text("Todos tus amigos están en clase ahora mismo 📖", color = TextoSuave, fontSize = 14.sp)
                            else Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                libres.forEach { (p, e) ->
                                    Column(Modifier.width(76.dp).clickable { abierto = p }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box {
                                            Avatar(p, 52.dp)
                                            Box(Modifier.align(Alignment.BottomEnd).size(14.dp).background(VerdeOk, CircleShape).border(2.dp, Tarjeta, CircleShape))
                                        }
                                        Text(p.nombreVisible.split(" ").first(), color = AzulNoche, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(e.proxima?.let { "hasta ${Alertas.horaBonita(it.horaInicio)}" } ?: "libre hoy", color = TextoSuave, fontSize = 11.sp)
                                    }
                                }
                            }
                            if (enClase.isNotEmpty()) Text(
                                "En clase: " + enClase.joinToString(", ") { (p, e) -> p.nombreVisible.split(" ").first() + (e.clase?.let { " (hasta ${Alertas.horaBonita(it.horaFin)})" } ?: "") },
                                color = TextoSuave, fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Novedades: logros para chocar los 5, los que te chocaron y respuestas a tus invitaciones
            if (logros.isNotEmpty() || novedades.isNotEmpty()) {
                item { Seccion("Novedades de tus amigos", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        var primero = true
                        novedades.forEach { i ->
                            if (!primero) HorizontalDivider(color = colores.borde); primero = false
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(if (i.tipo == "choca5") "🙌" else if (i.respuesta == "si") "✅" else "🙁", fontSize = 26.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    if (i.tipo == "choca5") "${nombreDe(i.de)} te chocó los 5" + (i.materia?.let { " por aprobar $it" } ?: "")
                                    else if (i.respuesta == "si") "${nombreDe(i.para)} va a estudiar" + (i.materia?.let { " $it" } ?: "") + " contigo 🙌"
                                    else "${nombreDe(i.para)} esta vez no puede estudiar" + (i.materia?.let { " $it" } ?: ""),
                                    color = AzulNoche, fontSize = 14.sp, modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        logros.forEach { l ->
                            if (!primero) HorizontalDivider(color = colores.borde); primero = false
                            val ya = l.yaChocaste || (l.amigo + l.materia) in chocados
                            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("🎉", fontSize = 26.sp)
                                Spacer(Modifier.width(12.dp))
                                Text("${l.nombreVisible} aprobó ${l.materia}", color = AzulNoche, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(8.dp))
                                if (ya) Text("🙌 Listo", color = TextoSuave, fontSize = 13.sp)
                                else Button(onClick = { chocar(l) }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                                    Text("🙌 Chocar los 5", fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Tus amigos
            item { Seccion("Tus amigos", Modifier.padding(start = 4.dp, top = 4.dp)) }
            item {
                if (amigos.isEmpty()) TarjetaMC(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("👥", fontSize = 36.sp)
                        Text("Aún no tienes amigos", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text("Agrega compañeros para ver con quién coincides en clase y a qué horas están libres.",
                            color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                } else TarjetaMC(Modifier.fillMaxWidth()) {
                    amigos.forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider(color = colores.borde)
                        FilaPersona(p, detalle = listOfNotNull(p.carrera, p.avance?.let { "$it % de avance" }).joinToString(" · "),
                            onClick = { abierto = p }) {
                            Text("›", color = TextoSuave, fontSize = 22.sp)
                        }
                    }
                }
            }

            // Enviadas
            if (pendientes.isNotEmpty()) {
                item { Seccion("Enviadas", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        pendientes.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = colores.borde)
                            FilaPersona(p, detalle = "esperando respuesta") {
                                TextButton(onClick = { accion("Solicitud cancelada.") { Repo.quitarAmistad(p.amistad!!) } }) {
                                    Text("Cancelar", color = TextoSuave)
                                }
                            }
                        }
                    }
                }
            }

            // Quizás conozcas
            val sug = sugerencias.filter { it.id !in relacionados }
            if (sug.isNotEmpty()) {
                item { Seccion("Quizás conozcas", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        sug.forEachIndexed { i, p ->
                            if (i > 0) HorizontalDivider(color = colores.borde)
                            FilaPersona(p, detalle = if (p.enComun > 0) "${p.enComun} ${if (p.enComun == 1) "materia" else "materias"} en común"
                                else listOfNotNull(p.universidad, p.carrera).joinToString(" · ")) {
                                if (p.id in enviadas) Text("Enviada", color = TextoSuave, fontSize = 13.sp)
                                else BotonAgregar { enviadas = enviadas + p.id; accion("Solicitud enviada a ${p.nombreVisible}.") { Repo.enviarSolicitud(p.id) } }
                            }
                        }
                    }
                }
            }
        }
    }

    if (creandoUsuario) DialogoUsuario(miUsuario) { nuevo ->
        creandoUsuario = false
        if (nuevo != null) { miUsuario = nuevo; mensaje = "Listo, ahora eres @$nuevo." }
    }
}

@Composable
private fun FilaPersona(p: Persona, detalle: String, onClick: (() -> Unit)? = null, derecha: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickable(onClick = onClick) else it }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.nombreVisible, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                p.usuario?.takeIf { p.nombre != null }?.let {
                    Text("  @$it", color = TextoSuave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (detalle.isNotBlank()) Text(detalle, color = TextoSuave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        derecha()
    }
}

@Composable
private fun BotonAgregar(onClick: () -> Unit) {
    Button(onClick = onClick, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 14.dp)) { Text("Agregar") }
}

/** Ficha de un amigo: clases en común, horas libres en común y su horario (si lo comparte). */
@Composable
private fun AmigoScreen(p: Persona, onVolver: () -> Unit, onQuitar: () -> Unit, onBloquear: () -> Unit, onInvitado: () -> Unit) {
    var mias by remember { mutableStateOf<List<Clase>>(emptyList()) }
    var suyas by remember { mutableStateOf<List<Clase>?>(null) }
    var confirmar by remember { mutableStateOf<String?>(null) } // "quitar" | "bloquear"
    var invitando by remember { mutableStateOf(false) }
    LaunchedEffect(p.id) {
        mias = runCatching { Repo.horario() }.getOrDefault(emptyList())
        suyas = runCatching { Repo.horariosDe(listOf(p.id))[p.id].orEmpty() }.getOrDefault(emptyList())
    }
    BackHandler(onBack = onVolver)

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        EncabezadoMarca(p.nombreVisible, p.usuario?.let { "@$it" }, alVolver = onVolver)
        val s = suyas
        if (s == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }
        val comunes = CalculoAmigos.clasesEnComun(mias, s)
        val libres = CalculoAmigos.horasLibresEnComun(mias, s)
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                TarjetaMC(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p, 60.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(listOfNotNull(p.universidad, p.carrera).joinToString(" · ").ifBlank { "Estudiante" },
                                color = AzulNoche, fontWeight = FontWeight.SemiBold)
                            p.avance?.let { Text("$it % de su carrera aprobada", color = TextoSuave, fontSize = 13.sp) }
                        }
                    }
                }
            }
            item {
                Button(onClick = { invitando = true }, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Text("📚 Invitar a estudiar", fontSize = 16.sp)
                }
            }

            item { Seccion("Clases en común", Modifier.padding(start = 4.dp, top = 4.dp)) }
            item {
                TarjetaMC(Modifier.fillMaxWidth()) {
                    if (s.isEmpty()) Text("${p.nombreVisible} no comparte su horario, o todavía no lo tiene.",
                        color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    else if (comunes.isEmpty()) Text("No tienen clases juntos este período.", color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
                    else comunes.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider(color = colores.borde)
                        FilaClaseCompacta(c)
                    }
                }
            }

            if (s.isNotEmpty() && libres.isNotEmpty()) {
                item { Seccion("Libres los dos", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            libres.groupBy { it.dia }.forEach { (dia, huecos) ->
                                Row {
                                    Text(DIAS[dia - 1], color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.width(92.dp))
                                    Text(huecos.joinToString("  ·  ") { "${Alertas.horaBonita(it.desde)} – ${Alertas.horaBonita(it.hasta)}" },
                                        color = TextoSuave, fontSize = 14.sp)
                                }
                            }
                            Text("Para reunirse o estudiar juntos (entre 7 am y 10 pm).", color = TextoSuave, fontSize = 12.sp)
                        }
                    }
                }
            }

            if (s.isNotEmpty()) {
                item { Seccion("Su horario", Modifier.padding(start = 4.dp, top = 4.dp)) }
                item {
                    TarjetaMC(Modifier.fillMaxWidth()) {
                        s.sortedWith(compareBy({ it.dia }, { it.horaInicio })).forEachIndexed { i, c ->
                            if (i > 0) HorizontalDivider(color = colores.borde)
                            FilaClaseCompacta(c, mostrarDia = true, resaltar = comunes.any { CalculoAmigos.mismaClase(it, c) })
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { confirmar = "quitar" }) { Text("Eliminar amigo", color = TextoSuave) }
                    TextButton(onClick = { confirmar = "bloquear" }) { Text("Bloquear", color = colores.rojo) }
                }
            }
        }
    }

    confirmar?.let { que ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
            title = { Text(if (que == "quitar") "¿Eliminar a ${p.nombreVisible}?" else "¿Bloquear a ${p.nombreVisible}?", color = AzulNoche, fontWeight = FontWeight.Bold) },
            text = {
                Text(if (que == "quitar") "Dejarán de ver sus horarios. Pueden volver a agregarse cuando quieran."
                    else "No podrá encontrarte, verte en sugerencias ni enviarte solicitudes. No se le avisa.", color = TextoSuave)
            },
            confirmButton = {
                Button(onClick = { confirmar = null; if (que == "quitar") onQuitar() else onBloquear() }, shape = RoundedCornerShape(14.dp),
                    colors = if (que == "bloquear") ButtonDefaults.buttonColors(containerColor = colores.rojo) else ButtonDefaults.buttonColors()) {
                    Text(if (que == "quitar") "Eliminar" else "Bloquear")
                }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar", color = TextoSuave) } }
        )
    }
    if (invitando) {
        val s = suyas.orEmpty()
        DialogoInvitar(p, CalculoAmigos.clasesEnComun(mias, s).map { it.materia }.distinct(),
            if (s.isEmpty()) emptyList() else CalculoAmigos.horasLibresEnComun(mias, s)) { enviada ->
            invitando = false
            if (enviada) onInvitado()
        }
    }
}

private val ES = Locale("es", "DO")

/** "hoy 3:00 pm", "mañana 10:00 am", "vie 26 sept · 4:00 pm" */
private fun cuandoTexto(iso: String): String {
    val d = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }.getOrNull() ?: return ""
    val h = Alertas.horaBonita("%02d:%02d".format(d.hour, d.minute))
    return when (d.toLocalDate()) {
        LocalDate.now() -> "hoy $h"
        LocalDate.now().plusDays(1) -> "mañana $h"
        else -> d.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)) + " · $h"
    }
}

/** Próxima vez (desde ahora) que los dos están libres; si no hay, dentro de 2 horas en punto. */
private fun proximoHueco(huecos: List<HuecoLibre>): LocalDateTime {
    val ahora = LocalDateTime.now()
    for (k in 0..7) {
        val dia = ahora.toLocalDate().plusDays(k.toLong())
        huecos.filter { it.dia == dia.dayOfWeek.value }.forEach { h ->
            val (hh, mm) = h.desde.split(":").map { it.toIntOrNull() ?: 0 }
            val t = dia.atTime(hh, mm)
            if (t.isAfter(ahora)) return t
        }
    }
    return ahora.plusHours(2).withMinute(0).withSecond(0).withNano(0)
}

@Composable
private fun DialogoInvitar(p: Persona, materias: List<String>, huecos: List<HuecoLibre>, onCerrar: (enviada: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var materia by remember { mutableStateOf(materias.firstOrNull().orEmpty()) }
    var cuando by remember { mutableStateOf(proximoHueco(huecos)) }
    var mensaje by remember { mutableStateOf("") }
    var enviando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun elegir() {
        DatePickerDialog(context, { _, a, m, d ->
            TimePickerDialog(context, { _, h, min -> cuando = LocalDateTime.of(a, m + 1, d, h, min) }, cuando.hour, cuando.minute, false).show()
        }, cuando.year, cuando.monthValue - 1, cuando.dayOfMonth).show()
    }

    AlertDialog(
        onDismissRequest = { if (!enviando) onCerrar(false) }, containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text("Invitar a ${p.nombreVisible}", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Le llega una notificación y puede responder “¡Voy!” o “No puedo”.", color = TextoSuave, fontSize = 14.sp)
                OutlinedTextField(materia, { if (it.length <= 120) materia = it }, label = { Text("¿Qué van a estudiar?") }, singleLine = true,
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                if (materias.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    materias.forEach { m -> FilterChip(selected = materia == m, onClick = { materia = m }, label = { Text(m, fontSize = 12.sp) }) }
                }
                OutlinedButton(onClick = ::elegir, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text("🗓️ " + cuando.format(DateTimeFormatter.ofPattern("EEE d MMM", ES)) + " · " + Alertas.horaBonita("%02d:%02d".format(cuando.hour, cuando.minute)),
                        color = AzulNoche)
                }
                if (huecos.isNotEmpty()) Text("Ya elegimos una hora en que los dos están libres.", color = TextoSuave, fontSize = 12.sp)
                OutlinedTextField(mensaje, { if (it.length <= 200) mensaje = it }, placeholder = { Text("Mensaje (opcional): ¿en la biblioteca?") },
                    shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !enviando, shape = RoundedCornerShape(14.dp), onClick = {
                scope.launch {
                    enviando = true; error = null
                    runCatching {
                        Repo.invitarAEstudiar(p.id, materia.trim().ifBlank { null }, cuando.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString(),
                            mensaje.trim().ifBlank { null })
                    }.onSuccess { onCerrar(true) }.onFailure {
                        error = if (it.message.orEmpty().contains("límite")) "Ya enviaste muchas invitaciones hoy." else "No se pudo enviar. Intenta otra vez."
                    }
                    enviando = false
                }
            }) { Text(if (enviando) "Enviando…" else "Enviar invitación") }
        },
        dismissButton = { TextButton(onClick = { onCerrar(false) }) { Text("Cancelar", color = TextoSuave) } }
    )
}

@Composable
private fun FilaClaseCompacta(c: Clase, mostrarDia: Boolean = true, resaltar: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.width(92.dp)) {
            if (mostrarDia) Text(DIAS.getOrElse(c.dia - 1) { "?" }, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${Alertas.horaBonita(c.horaInicio)}", color = TextoSuave, fontSize = 12.sp)
        }
        Column(Modifier.weight(1f)) {
            Text(c.materia, color = AzulNoche, fontSize = 15.sp, fontWeight = if (resaltar) FontWeight.Bold else FontWeight.Normal)
            val detalle = listOfNotNull(c.codigo, c.grupo?.let { "Grupo $it" }, c.aula?.let { "Aula $it" }).joinToString(" · ")
            if (detalle.isNotBlank()) Text(detalle, color = TextoSuave, fontSize = 12.sp, fontFamily = FontFamily.Default)
        }
        if (resaltar) EtiquetaEstado("Juntos", Indigo, Lavanda)
    }
}

/** Crear o cambiar el @usuario, revisando que esté libre mientras escribes. */
@Composable
fun DialogoUsuario(actual: String?, onCerrar: (nuevo: String?) -> Unit) {
    val scope = rememberCoroutineScope()
    var texto by remember { mutableStateOf(actual.orEmpty()) }
    var disponible by remember { mutableStateOf<Boolean?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val limpio = CalculoAmigos.limpiarUsuario(texto)
    val formatoOk = CalculoAmigos.FORMATO_USUARIO.matches(limpio)

    LaunchedEffect(limpio) {
        disponible = null
        if (!formatoOk || limpio == actual) return@LaunchedEffect
        delay(400)
        disponible = runCatching { Repo.usuarioDisponible(limpio) }.getOrNull()
    }

    AlertDialog(
        onDismissRequest = { if (!guardando) onCerrar(null) },
        containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text(if (actual == null) "Crea tu @usuario" else "Cambiar @usuario", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Así tus amigos te encuentran. Solo letras minúsculas, números, punto o guion bajo (3 a 20).",
                    color = TextoSuave, fontSize = 14.sp)
                OutlinedTextField(
                    value = texto, onValueChange = { texto = it.lowercase().replace(" ", "") },
                    prefix = { Text("@", color = TextoSuave) }, singleLine = true, shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth()
                )
                when {
                    limpio.isEmpty() || limpio == actual -> {}
                    !formatoOk -> Text("Entre 3 y 20 caracteres: a-z, 0-9, punto o guion bajo.", color = colores.rojo, fontSize = 13.sp)
                    disponible == true -> Text("✓ @$limpio está disponible", color = VerdeOk, fontSize = 13.sp)
                    disponible == false -> Text("@$limpio ya lo tiene otra persona", color = colores.rojo, fontSize = 13.sp)
                    else -> Text("Revisando…", color = TextoSuave, fontSize = 13.sp)
                }
                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(
                enabled = formatoOk && disponible == true && !guardando, shape = RoundedCornerShape(14.dp),
                onClick = {
                    scope.launch {
                        guardando = true
                        runCatching { Repo.guardarUsuario(limpio) }
                            .onSuccess { onCerrar(limpio) }
                            .onFailure { error = "No se pudo guardar. Quizás alguien lo tomó justo ahora." }
                        guardando = false
                    }
                }
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { onCerrar(null) }) { Text("Cancelar", color = TextoSuave) } }
    )
}

/** Elegir una opción de privacidad (radio). */
@Composable
fun DialogoOpciones(titulo: String, opciones: List<Triple<String, String, String>>, actual: String, onElegir: (String?) -> Unit) {
    var sel by remember { mutableStateOf(actual) }
    AlertDialog(
        onDismissRequest = { onElegir(null) },
        containerColor = Tarjeta, shape = RoundedCornerShape(28.dp),
        title = { Text(titulo, color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                opciones.forEach { (valor, nombre, detalle) ->
                    Row(Modifier.fillMaxWidth().clickable { sel = valor }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = sel == valor, onClick = { sel = valor })
                        Column {
                            Text(nombre, color = AzulNoche, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text(detalle, color = TextoSuave, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onElegir(sel) }, shape = RoundedCornerShape(14.dp)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = { onElegir(null) }) { Text("Cancelar", color = TextoSuave) } }
    )
}

val OPCIONES_BUSCABLE = listOf(
    Triple("universidad", "Todos", "Te encuentran por tu nombre o @usuario, y apareces en \"Quizás conozcas\" de tu universidad."),
    Triple("usuario", "Solo con mi @usuario", "No apareces en sugerencias; te encuentra quien sepa tu usuario."),
    Triple("nadie", "Nadie", "Nadie te encuentra. Tus amigos actuales se quedan.")
)
val OPCIONES_HORARIO = listOf(
    Triple("amigos", "Mis amigos", "Ven tu horario y las clases que tienen en común."),
    Triple("nadie", "Nadie", "Nadie ve tu horario, ni tus amigos.")
)
