package com.example.myapplication

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

/** Reduce la foto (máx. 1600 px) y la convierte a JPEG en base64 para enviarla a la IA. */
fun uriAJpegBase64(context: Context, uri: Uri, maxLado: Int = 1600): String {
    val limites = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, limites) }

    var muestra = 1
    while (limites.outWidth / muestra > maxLado * 2 || limites.outHeight / muestra > maxLado * 2) muestra *= 2

    val original = context.contentResolver.openInputStream(uri)!!.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = muestra })
    } ?: error("No se pudo leer la imagen")

    val escala = minOf(1f, maxLado.toFloat() / maxOf(original.width, original.height))
    val final = if (escala < 1f) Bitmap.createScaledBitmap(
        original, (original.width * escala).toInt(), (original.height * escala).toInt(), true
    ) else original

    val salida = ByteArrayOutputStream()
    final.compress(Bitmap.CompressFormat.JPEG, 85, salida)
    return Base64.encodeToString(salida.toByteArray(), Base64.NO_WRAP)
}

private const val MAX_PDF_BYTES = 8 * 1024 * 1024

/**
 * Prepara una foto o un PDF (horario o pensum) para la IA. Devuelve (base64, tipo MIME).
 * Las fotos se reducen; los PDF se mandan tal cual (máx. 8 MB).
 */
fun archivoParaIA(context: Context, uri: Uri, maxLado: Int = 1600): Pair<String, String> {
    val tipo = context.contentResolver.getType(uri).orEmpty()
    if (tipo != "application/pdf") return uriAJpegBase64(context, uri, maxLado) to "image/jpeg"

    val bytes = context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
    require(bytes.size <= MAX_PDF_BYTES) { "El PDF pesa más de 8 MB" }
    return Base64.encodeToString(bytes, Base64.NO_WRAP) to tipo
}

