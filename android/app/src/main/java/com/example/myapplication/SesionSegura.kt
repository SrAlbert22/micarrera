package com.example.myapplication

import android.app.Application
import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.jan.supabase.auth.SessionManager
import io.github.jan.supabase.auth.SettingsSessionManager
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Guarda el contexto de la app para lo que se usa fuera de una pantalla (la sesión cifrada). */
class MiCarreraApp : Application() {
    override fun onCreate() {
        super.onCreate()
        contexto = this
    }
    companion object {
        lateinit var contexto: Context
            private set
    }
}

/**
 * La sesión (tokens de Supabase) guardada CIFRADA con AES-256-GCM. La llave vive en el Android Keystore
 * (hardware del teléfono): no se puede copiar ni leer, ni siquiera sacando los archivos de la app.
 * La primera vez pasa la sesión vieja (sin cifrar) a la nueva y borra la vieja, así nadie tiene que volver a entrar.
 */
class SesionSegura(private val contexto: () -> Context) : SessionManager {
    private val prefs by lazy { contexto().getSharedPreferences("sesion_segura", Context.MODE_PRIVATE) }
    private val vieja by lazy { SettingsSessionManager() }
    private val formato = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun llave(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun cifrar(texto: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, llave()) }
        return Base64.encodeToString(c.iv + c.doFinal(texto.toByteArray()), Base64.NO_WRAP)
    }

    private fun descifrar(guardado: String): String {
        val datos = Base64.decode(guardado, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128, datos, 0, 12)) }
        return String(c.doFinal(datos, 12, datos.size - 12))
    }

    override suspend fun saveSession(session: UserSession) {
        prefs.edit().putString(CLAVE, cifrar(formato.encodeToString(UserSession.serializer(), session))).apply()
    }

    /** Sin sesión guardada lanza un error (así lo espera la librería; loadSessionOrNull lo convierte en null). */
    override suspend fun loadSession(): UserSession {
        val guardado = prefs.getString(CLAVE, null)
        if (guardado != null) {
            return runCatching { formato.decodeFromString(UserSession.serializer(), descifrar(guardado)) }
                .getOrElse { prefs.edit().remove(CLAVE).apply(); error("Sesión ilegible") } // llave perdida o datos alterados: se vuelve a entrar
        }
        // Migración desde la versión sin cifrar
        val anterior = runCatching { vieja.loadSessionOrNull() }.getOrNull() ?: error("No hay sesión guardada")
        saveSession(anterior)
        runCatching { vieja.deleteSession() }
        return anterior
    }

    override suspend fun deleteSession() {
        prefs.edit().remove(CLAVE).apply()
        runCatching { vieja.deleteSession() }
    }

    private companion object {
        const val ALIAS = "micarrera_sesion"
        const val CLAVE = "sesion"
    }
}
