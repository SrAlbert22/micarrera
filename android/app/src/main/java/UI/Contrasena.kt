package com.example.myapplication.ui

import com.example.myapplication.Contrasenas
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.supabase
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

/** Marca (o desmarca) que al entrar hay que pedirle al usuario que cree su contraseña. */
fun pedirContrasenaAlEntrar(context: Context, pedir: Boolean) {
    context.getSharedPreferences("login", Context.MODE_PRIVATE).edit().putBoolean("pedirContrasena", pedir).apply()
}

fun debePedirContrasena(context: Context) =
    context.getSharedPreferences("login", Context.MODE_PRIVATE).getBoolean("pedirContrasena", false)

/**
 * Crear o cambiar la contraseña de la cuenta (ya con sesión iniciada).
 * [primeraVez]: viene de entrar con un código; se puede dejar para después.
 */
@Composable
fun DialogoContrasena(primeraVez: Boolean, onCerrar: () -> Unit) {
    val scope = rememberCoroutineScope()
    var nueva by remember { mutableStateOf("") }
    var repetir by remember { mutableStateOf("") }
    var guardando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var listo by remember { mutableStateOf(false) }
    val valida = nueva.length >= MIN_CONTRASENA && nueva == repetir

    AlertDialog(
        onDismissRequest = { if (!guardando) onCerrar() },
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = {
            Text(if (listo) "¡Contraseña guardada! 🔒" else if (primeraVez) "Crea tu contraseña" else "Cambiar contraseña",
                color = AzulNoche, fontWeight = FontWeight.Bold)
        },
        text = {
            if (listo) {
                Text("La próxima vez entra con tu correo y esta contraseña. Ya no te llegarán códigos.", color = TextoSuave)
            } else Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (primeraVez) Text("Así la próxima vez entras con tu contraseña, sin esperar un código por correo.",
                    color = TextoSuave, fontSize = 14.sp)
                OutlinedTextField(nueva, { nueva = it }, placeholder = { Text("Contraseña (mín. $MIN_CONTRASENA)") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(repetir, { repetir = it }, placeholder = { Text("Repite la contraseña") },
                    singleLine = true, shape = RoundedCornerShape(14.dp), visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
                if (repetir.isNotEmpty() && nueva != repetir) Text("Las contraseñas no coinciden.", color = colores.rojo, fontSize = 13.sp)
                error?.let { Text(it, color = colores.rojo, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            if (listo) Button(onClick = onCerrar, shape = RoundedCornerShape(14.dp)) { Text("Listo") }
            else Button(enabled = valida && !guardando, shape = RoundedCornerShape(14.dp), onClick = {
                scope.launch {
                    guardando = true; error = null
                    Contrasenas.revisar(nueva, supabase.auth.currentUserOrNull()?.email)?.let { error = it; guardando = false; return@launch }
                    runCatching { supabase.auth.updateUser { password = nueva } }
                        .onSuccess { listo = true }
                        .onFailure {
                            error = if ("different from the old" in it.message.orEmpty()) "Esa ya es tu contraseña actual."
                            else "No se pudo guardar. Intenta otra vez."
                        }
                    guardando = false
                }
            }) {
                if (guardando) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                else Text("Guardar")
            }
        },
        dismissButton = {
            if (!listo) TextButton(onClick = onCerrar, enabled = !guardando) {
                Text(if (primeraVez) "Después" else "Cancelar", color = TextoSuave)
            }
        }
    )
}
