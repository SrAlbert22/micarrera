package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.CalculoAmigos
import com.example.myapplication.Repo
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Primera vez que entras (igual que en la web): tu nombre y un @usuario sugerido.
 * Sin esto tus amigos no te encuentran. Si ya tienes @usuario, se queda el tuyo.
 */
@Composable
fun CompletarPerfilScreen(usuarioActual: String?, onListo: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nombre by remember { mutableStateOf("") }
    var usuario by remember { mutableStateOf(usuarioActual.orEmpty()) }
    var tocado by remember { mutableStateOf(usuarioActual != null) } // si lo edita a mano, no lo cambiamos al escribir el nombre
    var revisado by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // disponibilidad de cuál @usuario
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val limpio = CalculoAmigos.limpiarUsuario(usuario)
    val formatoOk = CalculoAmigos.FORMATO_USUARIO.matches(limpio)
    val nuevoUsuario = usuarioActual == null || limpio != usuarioActual
    val disponible = revisado?.takeIf { it.first == limpio }?.second

    LaunchedEffect(nombre, tocado) { if (!tocado) usuario = CalculoAmigos.sugerirUsuario(nombre) }
    LaunchedEffect(limpio) {
        if (!formatoOk || !nuevoUsuario) return@LaunchedEffect
        delay(400)
        runCatching { Repo.usuarioDisponible(limpio) }.onSuccess { revisado = limpio to it }
    }
    // Si el sugerido ya lo tiene otra persona, probamos con un número al final
    LaunchedEffect(disponible) {
        if (disponible == false && !tocado && formatoOk) usuario = limpio.take(17) + (10..98).random()
    }

    val listo = nombre.trim().length >= 2 && (usuario.isEmpty() || !nuevoUsuario || (formatoOk && disponible == true))

    fun guardar() {
        guardando = true; error = null
        scope.launch {
            runCatching {
                Repo.guardarNombre(nombre)
                if (usuario.isNotEmpty() && nuevoUsuario) Repo.guardarUsuario(limpio)
            }.onSuccess { onListo() }
                .onFailure { error = "No se pudo guardar. Revisa tu internet e intenta otra vez."; guardando = false }
        }
    }

    Box(Modifier.fillMaxSize().background(DegradadoMarca)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Text("👋", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text("¡Bienvenido a MiCarrera!", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text("¿Cómo te llamas? Así tus amigos te encuentran.", color = Color.White.copy(alpha = 0.85f), fontSize = 16.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 24.dp))

            Card(
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Tarjeta), elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = nombre, onValueChange = { nombre = it.take(60) }, label = { Text("Tu nombre") },
                        placeholder = { Text("Ej. María Rodríguez") }, singleLine = true, shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words), modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = usuario, onValueChange = { tocado = true; usuario = it.lowercase().replace(" ", "").take(20) },
                        label = { Text("Tu @usuario") }, prefix = { Text("@", color = TextoSuave) }, singleLine = true,
                        shape = RoundedCornerShape(16.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (usuario.isNotEmpty() && nuevoUsuario) {
                        val (color, texto) = when {
                            !formatoOk -> MaterialTheme.colorScheme.error to "Entre 3 y 20 caracteres: a-z, 0-9, punto o guion bajo."
                            disponible == true -> VerdeOk to "✓ @$limpio está disponible"
                            disponible == false -> MaterialTheme.colorScheme.error to "@$limpio ya lo tiene otra persona"
                            else -> TextoSuave to "Revisando…"
                        }
                        Text(texto, color = color, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Default.Lock, null, tint = TextoSuave, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Tus notas e índice nunca se comparten. Puedes cambiar quién te encuentra en Cuenta.", color = TextoSuave, fontSize = 13.sp)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    Button(
                        onClick = ::guardar, enabled = listo && !guardando,
                        modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)
                    ) {
                        if (guardando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text("Continuar", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            TextButton(onClick = onListo, modifier = Modifier.padding(top = 8.dp)) {
                Text("Ahora no", color = Color.White.copy(alpha = 0.75f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
