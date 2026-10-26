package com.example.myapplication

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.IDToken
import java.security.MessageDigest
import java.util.UUID

/** El usuario cerró la ventana de Google sin elegir cuenta: no es un error que haya que mostrar. */
class GoogleCancelado : Exception()

/**
 * Entrar con Google: Android muestra sus cuentas de Google, devuelve un "ID token"
 * y Supabase lo cambia por una sesión. La cuenta es la misma que con el código por correo
 * si el correo coincide.
 *
 * [context] debe ser el de la Activity (para poder mostrar la ventana de Google).
 */
suspend fun entrarConGoogle(context: Context) {
    // El nonce evita que alguien reutilice un token robado: Google firma su hash y Supabase verifica el original.
    val nonce = UUID.randomUUID().toString()
    val nonceHash = MessageDigest.getInstance("SHA-256").digest(nonce.toByteArray()).joinToString("") { "%02x".format(it) }

    val opcion = GetSignInWithGoogleOption.Builder(GOOGLE_WEB_CLIENT_ID).setNonce(nonceHash).build()
    val pedido = GetCredentialRequest.Builder().addCredentialOption(opcion).build()
    val respuesta = try {
        CredentialManager.create(context).getCredential(context, pedido)
    } catch (e: GetCredentialCancellationException) {
        throw GoogleCancelado()
    }

    val credencial = GoogleIdTokenCredential.createFrom(respuesta.credential.data)
    supabase.auth.signInWith(IDToken) {
        idToken = credencial.idToken
        provider = Google
        this.nonce = nonce
    }
}
