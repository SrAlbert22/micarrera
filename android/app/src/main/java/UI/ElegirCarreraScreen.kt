package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Carrera
import com.example.myapplication.Repo
import com.example.myapplication.Universidad
import kotlinx.coroutines.launch

@Composable
fun ElegirCarreraScreen(onElegida: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var universidades by remember { mutableStateOf<List<Universidad>>(emptyList()) }
    var carreras by remember { mutableStateOf<List<Carrera>>(emptyList()) }
    var uniElegida by remember { mutableStateOf<Universidad?>(null) }
    var cargando by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var agregando by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { Repo.universidades() }
            .onSuccess { universidades = it }
            .onFailure { error = "No se pudieron cargar las universidades. Revisa tu internet." }
        cargando = false
    }
    LaunchedEffect(uniElegida) {
        val u = uniElegida ?: return@LaunchedEffect
        cargando = true
        carreras = runCatching { Repo.carreras(u.id) }.getOrDefault(emptyList())
        cargando = false
    }

    fun elegir(id: String) { scope.launch { runCatching { Repo.guardarCarrera(id) }; onElegida(id) } }

    uniElegida?.let { u ->
        if (agregando) {
            AgregarCarreraScreen(universidad = u, onLista = ::elegir, onCancelar = { agregando = false })
            return
        }
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        EncabezadoMarca(
            titulo = if (uniElegida == null) "¿Dónde estudias?" else "¿Qué estudias?",
            subtitulo = uniElegida?.nombre ?: "Elige tu universidad para empezar",
            alVolver = if (uniElegida != null) ({ uniElegida = null; carreras = emptyList() }) else null
        ) { uniElegida?.let { LogoUniversidad(it, 44.dp) } }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error) } }

            if (cargando) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            } else if (uniElegida == null) {
                items(universidades, key = { it.id }) { u ->
                    FilaOpcion(
                        inicial = (u.siglas ?: u.nombre).take(1),
                        universidad = u,
                        titulo = u.siglas ?: u.nombre,
                        subtitulo = if (u.siglas != null) u.nombre else null
                    ) { uniElegida = u }
                }
            } else {
                items(carreras, key = { it.id }) { c ->
                    FilaOpcion(inicial = "📘", titulo = c.nombre, subtitulo = if (c.aportadaPor != null) "👥 Aportada por estudiantes" else null) {
                        elegir(c.id)
                    }
                }
                if (carreras.isEmpty()) item { EstadoVacio(uniElegida?.siglas) { agregando = true } }
                else item {
                    FilaOpcion(inicial = "＋", titulo = "¿No está tu carrera?", subtitulo = "Agrégala subiendo tu pensum") { agregando = true }
                }
            }
        }
    }
}

@Composable
private fun FilaOpcion(inicial: String, titulo: String, subtitulo: String?, universidad: Universidad? = null, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Tarjeta),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (universidad != null) LogoUniversidad(universidad, 48.dp)
            else Box(
                Modifier.size(48.dp).background(Lavanda, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(inicial, color = Indigo, fontWeight = FontWeight.Bold, fontSize = 20.sp) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = AzulNoche)
                subtitulo?.let {
                    Text(it, fontSize = 13.sp, color = TextoSuave, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = GrisPendiente)
        }
    }
}

@Composable
private fun EstadoVacio(siglas: String?, onAgregar: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Tarjeta)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("📄✨", fontSize = 44.sp)
            Spacer(Modifier.height(12.dp))
            Text("Sé el primero de ${siglas ?: "tu universidad"}", fontWeight = FontWeight.Bold,
                fontSize = 17.sp, color = AzulNoche, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text("Todavía nadie ha agregado una carrera aquí. Sube el pensum de la tuya (foto, PDF o texto) y la IA lo organiza en segundos.",
                fontSize = 14.sp, color = TextoSuave, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAgregar, shape = RoundedCornerShape(16.dp)) { Text("Agregar mi carrera", fontWeight = FontWeight.SemiBold) }
        }
    }
}
