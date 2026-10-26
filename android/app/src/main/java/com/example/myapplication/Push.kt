package com.example.myapplication

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Notificaciones que manda el servidor (Firebase Cloud Messaging): novedades, amigos, etc.
 * Las alertas de clase NO pasan por aquí: esas las programa el teléfono (ver [Alertas]).
 */
object Push {
    const val CANAL_NOVEDADES = "novedades"

    fun crearCanal(ctx: Context) {
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CANAL_NOVEDADES, "Novedades", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Novedades de tu carrera y de MiCarrera." }
        )
    }

    /** Token de este teléfono en Firebase (o null si Firebase no está disponible). */
    // Firebase 25.x marca getToken/onNewToken como obsoletos pero aún no trae reemplazo: siguen siendo la vía oficial.
    @Suppress("DEPRECATION")
    suspend fun token(): String? = suspendCancellableCoroutine { cont ->
        runCatching {
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resume(null) }
        }.onFailure { cont.resume(null) }
    }

    /** Guarda el token en Supabase para que el servidor sepa a qué teléfono escribir. */
    suspend fun registrar() {
        val t = token() ?: return
        runCatching { Repo.registrarDispositivo(t) }
    }

    /** Al cerrar sesión: este teléfono deja de recibir avisos de esa cuenta. */
    suspend fun olvidar() {
        val t = token() ?: return
        runCatching { Repo.quitarDispositivo(t) }
    }
}

class MensajeriaService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onNewToken(token: String) {
        // Firebase cambia el token a veces: si hay sesión, lo actualizamos.
        if (supabase.auth.currentUserOrNull() != null) {
            scope.launch { runCatching { Repo.registrarDispositivo(token) } }
        }
    }

    /**
     * Con la app abierta, Android no muestra la notificación solo: la mostramos nosotros.
     * (Con la app cerrada, Android la muestra con el canal e ícono del manifest.)
     */
    override fun onMessageReceived(message: RemoteMessage) {
        val titulo = message.notification?.title ?: message.data["titulo"] ?: return
        val texto = message.notification?.body ?: message.data["texto"].orEmpty()
        Push.crearCanal(this)
        Alertas.mostrar(this, Push.CANAL_NOVEDADES, (System.currentTimeMillis() % 100000).toInt(), titulo, texto)
    }
}
