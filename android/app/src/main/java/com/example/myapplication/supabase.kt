package com.example.myapplication

import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.functions.Functions
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.serialization.json.Json

private const val SUPABASE_URL = "https://ivtgsjnjpkcufjkrbvft.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_QRa8SoW9t5e46BYrXUeLag_qjtOqNSx"

/**
 * ID del cliente OAuth "Aplicación web" de Google Cloud (el mismo que se pone en Supabase → Google).
 * No es secreto. Mientras esté vacío, el botón "Continuar con Google" no aparece.
 */
const val GOOGLE_WEB_CLIENT_ID = ""

val json = Json { ignoreUnknownKeys = true }

val supabase = createSupabaseClient(SUPABASE_URL, SUPABASE_KEY) {
    defaultSerializer = KotlinXSerializer(json)
    install(Auth) {
        sessionManager = SesionSegura { MiCarreraApp.contexto } // sesión cifrada con el Keystore del teléfono
    }
    install(Postgrest)
    install(Functions)
}