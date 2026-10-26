package com.example.myapplication

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** versionCode de la app instalada. */
fun versionInstalada(ctx: Context): Long =
    PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0))

/** versionName de la app instalada ("1.4"). */
fun nombreVersionInstalada(ctx: Context): String =
    runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "?"

/**
 * Actualizaciones sin Play Store y sin cable: la tabla "versiones" de Supabase dice cuál es la última versión y
 * dónde está su APK (en la web). La app lo baja, comprueba su huella SHA-256 y abre el instalador de Android.
 * Android además exige que el APK esté firmado con la misma llave que la app instalada.
 */
object Actualizador {
    /** La versión nueva que hay que ofrecer (null = estás al día). */
    var nueva by mutableStateOf<Version?>(null)
        private set
    var descartada by mutableStateOf(false)

    /** Mira si hay una versión más nueva que la instalada. */
    suspend fun buscar(context: Context): Version? {
        if (BuildConfig.TIENDA_PLAY) return null // en Play Store las actualizaciones las da la tienda
        val instalada = runCatching { versionInstalada(context) }.getOrDefault(Long.MAX_VALUE)
        val v = Repo.ultimaVersion()?.takeIf { it.versionCode > instalada }
        nueva = v
        descartada = false
        return v
    }

    private fun carpeta(context: Context) = File(context.cacheDir, "actualizaciones")

    /** Baja el APK (avisando el progreso de 0 a 1) y comprueba que sea el correcto. */
    suspend fun descargar(context: Context, v: Version, progreso: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        require(v.url.startsWith("https://")) { "La dirección de la actualización no es segura" }
        val dir = carpeta(context).apply { deleteRecursively(); mkdirs() }
        val archivo = File(dir, "MiCarrera-${v.versionCode}.apk")
        val sha = MessageDigest.getInstance("SHA-256")
        val conexion = URL(v.url).openConnection() as HttpURLConnection
        conexion.connectTimeout = 15_000
        conexion.readTimeout = 30_000
        try {
            check(conexion.responseCode == 200) { "El servidor respondió ${conexion.responseCode}" }
            val total = conexion.contentLengthLong
            var leidos = 0L
            conexion.inputStream.use { entrada ->
                archivo.outputStream().use { salida ->
                    val bufer = ByteArray(64 * 1024)
                    while (true) {
                        val n = entrada.read(bufer)
                        if (n < 0) break
                        salida.write(bufer, 0, n)
                        sha.update(bufer, 0, n)
                        leidos += n
                        if (total > 0) progreso(leidos.toFloat() / total)
                    }
                }
            }
        } finally {
            conexion.disconnect()
        }

        val huella = sha.digest().joinToString("") { "%02x".format(it) }
        if (v.sha256 != null && !huella.equals(v.sha256, ignoreCase = true)) {
            archivo.delete()
            error("La descarga no coincide con la versión publicada")
        }
        val info = context.packageManager.getPackageArchiveInfo(archivo.path, 0)
        if (info?.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(info) < v.versionCode) {
            archivo.delete()
            error("El archivo descargado no es MiCarrera ${v.versionName}")
        }
        archivo
    }

    /** Android 8+: ¿el usuario ya dejó que MiCarrera instale actualizaciones? */
    fun puedeInstalar(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Abre el ajuste "Instalar apps desconocidas" de MiCarrera (se pide una sola vez). */
    fun pedirPermiso(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /** Abre el instalador de Android con el APK ya comprobado. */
    fun instalar(context: Context, archivo: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.archivos", archivo)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
