package com.example.myapplication.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.Universidad
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Logos de las universidades: PNG pequeños (128 px) que sirve la web. Se guardan en la caché del teléfono
 * una semana, así se ven al instante y sin internet.
 */
object CacheLogos {
    private const val UNA_SEMANA = 7L * 24 * 60 * 60 * 1000
    private val memoria = LruCache<String, ImageBitmap>(48)

    suspend fun cargar(context: Context, url: String): ImageBitmap? {
        memoria.get(url)?.let { return it }
        return withContext(Dispatchers.IO) {
            val archivo = File(context.cacheDir, "logos/${url.hashCode().toUInt()}.png")
            val fresco = archivo.exists() && System.currentTimeMillis() - archivo.lastModified() < UNA_SEMANA
            val bytes = if (fresco) archivo.readBytes() else runCatching { bajar(url) }
                .onSuccess { archivo.parentFile?.mkdirs(); archivo.writeBytes(it) }
                .getOrElse { if (archivo.exists()) archivo.readBytes() else null } // sin internet: el viejo sirve
            bytes?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }?.asImageBitmap()
        }?.also { memoria.put(url, it) }
    }

    private fun bajar(url: String): ByteArray {
        val conexion = URL(url).openConnection() as HttpURLConnection
        conexion.connectTimeout = 10_000
        conexion.readTimeout = 10_000
        try {
            check(conexion.responseCode == 200) { "HTTP ${conexion.responseCode}" }
            return conexion.inputStream.use { it.readBytes() }
        } finally {
            conexion.disconnect()
        }
    }
}

/** Logo de la universidad sobre un cuadro blanco; si no tiene logo (o no carga), sus siglas. */
@Composable
fun LogoUniversidad(u: Universidad?, tam: Dp = 48.dp) {
    val context = LocalContext.current
    val url = u?.logoUrl
    val logo by produceState<ImageBitmap?>(null, url) { value = url?.let { CacheLogos.cargar(context, it) } }
    val siglas = u?.siglas ?: u?.nombre?.take(4) ?: "🎓"
    Box(
        Modifier.size(tam).clip(RoundedCornerShape(tam * 0.27f)).background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        val imagen = logo
        if (imagen != null) {
            Image(imagen, siglas, Modifier.fillMaxSize().padding(tam * 0.08f), contentScale = ContentScale.Fit)
        } else {
            Text(
                siglas, color = Color(0xFF2B3A8C), fontWeight = FontWeight.ExtraBold, maxLines = 1,
                fontSize = (tam.value * if (siglas.length > 3) 0.24f else 0.32f).sp
            )
        }
    }
}
