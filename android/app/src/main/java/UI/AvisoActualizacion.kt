package com.example.myapplication.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Actualizador
import kotlinx.coroutines.launch
import java.io.File

/**
 * Al abrir la app, mira en Supabase si hay una versión más nueva y la ofrece.
 * "Actualizar" la baja dentro de la app, la comprueba y abre el instalador de Android.
 * Si la versión es obligatoria, no deja cerrar el aviso.
 */
@Composable
fun AvisoActualizacion() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var progreso by remember { mutableStateOf<Float?>(null) }
    var archivo by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { runCatching { Actualizador.buscar(context) } }

    val v = Actualizador.nueva ?: return
    if (Actualizador.descartada && !v.obligatoria) return
    val descargando = progreso != null && archivo == null

    fun instalar(a: File) {
        if (Actualizador.puedeInstalar(context)) Actualizador.instalar(context, a) else Actualizador.pedirPermiso(context)
    }

    AlertDialog(
        onDismissRequest = { if (!v.obligatoria && !descargando) Actualizador.descartada = true },
        containerColor = Tarjeta,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Hay una versión nueva 🎉", color = AzulNoche, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("MiCarrera ${v.versionName} ya está lista.", color = AzulNoche, fontSize = 15.sp)
                v.notas?.let { Text(it, color = TextoSuave, fontSize = 14.sp) }
                if (v.obligatoria) Text("Esta actualización es necesaria para seguir usando la app.",
                    color = AmbarCurso, fontSize = 13.sp)
                progreso?.let { p ->
                    if (archivo == null) {
                        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                        Text("Descargando… ${(p * 100).toInt()}%", color = TextoSuave, fontSize = 13.sp)
                    }
                }
                if (archivo != null && !Actualizador.puedeInstalar(context)) {
                    Text("Android te pedirá permitir que MiCarrera instale actualizaciones. Actívalo, vuelve aquí y toca Instalar.",
                        color = TextoSuave, fontSize = 13.sp)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            Button(shape = RoundedCornerShape(14.dp), enabled = !descargando, onClick = {
                archivo?.let { instalar(it); return@Button }
                error = null
                progreso = 0f
                scope.launch {
                    runCatching { Actualizador.descargar(context, v) { progreso = it } }
                        .onSuccess { archivo = it; instalar(it) }
                        .onFailure {
                            progreso = null
                            error = "${it.message ?: "No se pudo descargar"}. Toca \"Abrir en el navegador\" para bajarla desde allí."
                        }
                }
            }) { Text(if (archivo != null) "Instalar" else "Actualizar") }
        },
        dismissButton = {
            when {
                error != null -> TextButton(onClick = {
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(v.url))) }
                }) { Text("Abrir en el navegador") }
                !v.obligatoria && !descargando -> TextButton(onClick = { Actualizador.descartada = true }) {
                    Text("Luego", color = TextoSuave)
                }
            }
        }
    )
}
