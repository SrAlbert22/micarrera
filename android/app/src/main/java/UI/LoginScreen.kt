package com.example.myapplication.ui

import android.content.Context
import com.example.myapplication.Contrasenas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.GOOGLE_WEB_CLIENT_ID
import com.example.myapplication.GoogleCancelado
import com.example.myapplication.R
import com.example.myapplication.correccionCorreo
import com.example.myapplication.entrarConGoogle
import com.example.myapplication.supabase
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch

/**
 * Pasos del inicio de sesión:
 *  - ENTRAR: correo + contraseña (lo normal; no llega ningún correo).
 *  - CREAR: correo + contraseña nueva → llega un código UNA vez para confirmar el correo (CONFIRMAR).
 *  - CODIGO: entrar con un código al correo (olvidé mi contraseña, o cuentas viejas sin contraseña).
 *    Después de entrar así, la app pide crear una contraseña (ver [pedirContrasenaAlEntrar]).
 */
private enum class Paso { ENTRAR, CREAR, CONFIRMAR, CODIGO, CODIGO_ENVIADO }

const val MIN_CONTRASENA = 8

/** Traduce los errores de Supabase a mensajes claros. */
private fun mensajeDeError(e: Exception, paso: Paso): String {
    val m = e.message.orEmpty().lowercase()
    return when {
        "invalid login credentials" in m ->
            "Correo o contraseña incorrectos. Si antes entrabas con un código, usa \"Entrar con un código\" y crea tu contraseña."
        "signups not allowed" in m || "otp_disabled" in m || "user not found" in m ->
            "No hay ninguna cuenta con ese correo. Revisa que esté bien escrito o toca \"Crear cuenta\"."
        "email not confirmed" in m -> "Todavía no confirmas tu correo. Te enviamos el código otra vez."
        "already registered" in m || "already been registered" in m -> "Ya existe una cuenta con ese correo. Toca \"Entrar\"."
        "password" in m && ("weak" in m || "short" in m || "at least" in m) -> "La contraseña es muy débil. Usa al menos $MIN_CONTRASENA caracteres."
        "rate limit" in m || "security purposes" in m -> "Pediste muchos códigos seguidos. Espera un minuto e intenta otra vez."
        paso == Paso.CONFIRMAR || paso == Paso.CODIGO_ENVIADO -> "Código incorrecto o vencido. Intenta de nuevo."
        else -> "No se pudo completar. Revisa tu internet e intenta otra vez."
    }
}

@Composable
fun LoginScreen() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("login", Context.MODE_PRIVATE)
    var paso by remember { mutableStateOf(Paso.ENTRAR) }
    var correo by remember { mutableStateOf(prefs.getString("ultimoCorreo", "") ?: "") }
    var contrasena by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var codigo by remember { mutableStateOf("") }
    var verContrasena by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var haciendoTest by rememberSaveable { mutableStateOf(false) }

    fun ir(nuevo: Paso) { paso = nuevo; error = null; aviso = null; codigo = "" }

    fun hacer(accion: suspend () -> Unit) {
        scope.launch {
            cargando = true; error = null; aviso = null
            try {
                accion()
                prefs.edit().putString("ultimoCorreo", correo).apply()
            } catch (e: Exception) {
                error = mensajeDeError(e, paso)
                // Cuenta creada pero sin confirmar: reenviamos el código y pasamos a confirmarla.
                if ("email not confirmed" in e.message.orEmpty().lowercase()) {
                    runCatching { supabase.auth.resendEmail(OtpType.Email.SIGNUP, correo) }
                    paso = Paso.CONFIRMAR
                }
            }
            cargando = false
        }
    }

    val correoOk = correo.contains("@") && correo.contains(".")
    val (titulo, subtitulo) = when (paso) {
        Paso.ENTRAR -> "Entra a tu cuenta" to "Con tu correo y tu contraseña."
        Paso.CREAR -> "Crea tu cuenta" to "Te enviaremos un código una sola vez para confirmar tu correo."
        Paso.CONFIRMAR -> "Confirma tu correo 📩" to "Escribe el código que te enviamos a $correo. Si no llega en un minuto, revisa Spam o Promociones."
        Paso.CODIGO -> "Entrar con un código" to "Te enviamos un código a tu correo. Luego podrás crear tu contraseña."
        Paso.CODIGO_ENVIADO -> "Revisa tu correo 📩" to "Escribe el código que te enviamos a $correo. Si no llega en un minuto, revisa Spam o Promociones."
    }
    val campoContrasena = @Composable { valor: String, cambiar: (String) -> Unit, etiqueta: String ->
        OutlinedTextField(
            value = valor, onValueChange = cambiar,
            placeholder = { Text(etiqueta) },
            leadingIcon = { Icon(Icons.Default.Lock, null, tint = Indigo) },
            trailingIcon = {
                TextButton(onClick = { verContrasena = !verContrasena }) { Text(if (verContrasena) "Ocultar" else "Ver", fontSize = 13.sp) }
            },
            visualTransformation = if (verContrasena) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
    }

    if (haciendoTest) {
        TestVocacionalScreen(onCerrar = { haciendoTest = false }, onCrearCuenta = { haciendoTest = false; ir(Paso.CREAR) })
        return
    }

    Box(Modifier.fillMaxSize().background(DegradadoMarca)) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = "MiCarrera",
                modifier = Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).graphicsLayer { scaleX = 1.4f; scaleY = 1.4f }
            )
            Spacer(Modifier.height(16.dp))
            Text("MiCarrera", color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
            Text("Tu pensum y tu horario, en orden", color = Color.White.copy(alpha = 0.8f), fontSize = 16.sp)
            Spacer(Modifier.height(20.dp))
            // Test vocacional: arriba y a la vista, se hace sin cuenta
            BotonTest { haciendoTest = true }
            Spacer(Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = Tarjeta),
                elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Pestañas Entrar / Crear cuenta
                    if (paso == Paso.ENTRAR || paso == Paso.CREAR) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colores.tarjetaAlta).padding(4.dp)
                        ) {
                            listOf(Paso.ENTRAR to "Entrar", Paso.CREAR to "Crear cuenta").forEach { (p, t) ->
                                val sel = paso == p
                                TextButton(
                                    onClick = { ir(p) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        containerColor = if (sel) Tarjeta else Color.Transparent,
                                        contentColor = if (sel) AzulNoche else TextoSuave
                                    )
                                ) { Text(t, fontWeight = FontWeight.SemiBold) }
                            }
                        }
                    }

                    Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AzulNoche)
                    Text(subtitulo, fontSize = 14.sp, color = TextoSuave)

                    when (paso) {
                        Paso.ENTRAR, Paso.CREAR, Paso.CODIGO -> {
                            if (paso == Paso.ENTRAR && GOOGLE_WEB_CLIENT_ID.isNotBlank()) {
                                OutlinedButton(
                                    enabled = !cargando, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp),
                                    onClick = {
                                        scope.launch {
                                            cargando = true; error = null
                                            try { entrarConGoogle(context) } catch (e: GoogleCancelado) { } catch (e: Exception) {
                                                error = "No se pudo entrar con Google. Intenta otra vez o usa tu correo."
                                            }
                                            cargando = false
                                        }
                                    }
                                ) {
                                    Text("G", color = Color(0xFF4285F4), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                                    Spacer(Modifier.width(10.dp))
                                    Text("Continuar con Google", color = AzulNoche, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            OutlinedTextField(
                                value = correo, onValueChange = { correo = it.trim() },
                                placeholder = { Text("tucorreo@gmail.com") },
                                leadingIcon = { Icon(Icons.Default.Email, null, tint = Indigo) },
                                singleLine = true, shape = RoundedCornerShape(16.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                modifier = Modifier.fillMaxWidth()
                            )
                            correccionCorreo(correo)?.let { bien ->
                                TextButton(onClick = { correo = bien }, contentPadding = PaddingValues(0.dp)) {
                                    Text("⚠️ ¿Quisiste decir $bien? Toca para corregir", color = AmbarCurso, fontSize = 14.sp)
                                }
                            }
                            if (paso != Paso.CODIGO) campoContrasena(contrasena, { contrasena = it },
                                if (paso == Paso.CREAR) "Contraseña (mín. $MIN_CONTRASENA)" else "Contraseña")
                            if (paso == Paso.CREAR) campoContrasena(confirmar, { confirmar = it }, "Repite la contraseña")
                        }
                        Paso.CONFIRMAR, Paso.CODIGO_ENVIADO -> OutlinedTextField(
                            value = codigo, onValueChange = { codigo = it.filter(Char::isDigit).take(8) },
                            placeholder = {
                                Text("000000", Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                                    fontSize = 26.sp, letterSpacing = 8.sp, color = GrisPendiente)
                            },
                            textStyle = TextStyle(fontSize = 26.sp, letterSpacing = 8.sp, fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center, color = AzulNoche),
                            singleLine = true, shape = RoundedCornerShape(16.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    aviso?.let { Text(it, color = VerdeOk, fontSize = 13.sp) }

                    val listo = !cargando && when (paso) {
                        Paso.ENTRAR -> correoOk && contrasena.isNotEmpty()
                        Paso.CREAR -> correoOk && contrasena.length >= MIN_CONTRASENA && contrasena == confirmar
                        Paso.CODIGO -> correoOk
                        Paso.CONFIRMAR, Paso.CODIGO_ENVIADO -> codigo.length >= 6
                    }
                    if (paso == Paso.CREAR && confirmar.isNotEmpty() && contrasena != confirmar) {
                        Text("Las contraseñas no coinciden.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }

                    Button(
                        enabled = listo,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        onClick = {
                            when (paso) {
                                Paso.ENTRAR -> hacer {
                                    supabase.auth.signInWith(Email) { email = correo; password = contrasena }
                                }
                                Paso.CREAR -> hacer {
                                    Contrasenas.revisar(contrasena, correo)?.let { error = it; return@hacer }
                                    supabase.auth.signUpWith(Email) { email = correo; password = contrasena }
                                    // Si el proyecto no pide confirmar el correo, ya hay sesión y no hace falta el código.
                                    if (supabase.auth.currentSessionOrNull() == null) { paso = Paso.CONFIRMAR; codigo = "" }
                                }
                                Paso.CONFIRMAR -> hacer {
                                    supabase.auth.verifyEmailOtp(type = OtpType.Email.SIGNUP, email = correo, token = codigo)
                                }
                                Paso.CODIGO -> hacer {
                                    supabase.auth.signInWith(OTP) { email = correo; createUser = false }
                                    paso = Paso.CODIGO_ENVIADO
                                }
                                Paso.CODIGO_ENVIADO -> hacer {
                                    supabase.auth.verifyEmailOtp(type = OtpType.Email.EMAIL, email = correo, token = codigo)
                                    pedirContrasenaAlEntrar(context, true) // entró con código: que cree su contraseña
                                }
                            }
                        }
                    ) {
                        if (cargando) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(
                            when (paso) {
                                Paso.ENTRAR -> "Entrar"
                                Paso.CREAR -> "Crear cuenta"
                                Paso.CODIGO -> "Enviarme el código"
                                else -> "Confirmar"
                            },
                            fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Enlaces de abajo
                    when (paso) {
                        Paso.ENTRAR -> TextButton(onClick = { ir(Paso.CODIGO) }, modifier = Modifier.fillMaxWidth()) {
                            Text("¿Olvidaste tu contraseña? Entra con un código")
                        }
                        Paso.CONFIRMAR -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = {
                                hacer { supabase.auth.resendEmail(OtpType.Email.SIGNUP, correo); aviso = "Te enviamos otro código." }
                            }) { Text("Reenviar código") }
                            TextButton(onClick = { ir(Paso.CREAR) }) { Text("Cambiar correo") }
                        }
                        Paso.CODIGO, Paso.CODIGO_ENVIADO -> TextButton(onClick = { ir(Paso.ENTRAR) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Volver a entrar con contraseña")
                        }
                        else -> {}
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                buildAnnotatedString {
                    append("Al usar MiCarrera aceptas los ")
                    withLink(LinkAnnotation.Url(URL_LEGAL + "terminos.html", TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline)))) {
                        append("Términos y condiciones")
                    }
                    append(" y la ")
                    withLink(LinkAnnotation.Url(URL_LEGAL + "privacidad.html", TextLinkStyles(SpanStyle(color = Color.White, textDecoration = TextDecoration.Underline)))) {
                        append("Política de privacidad")
                    }
                    append(".")
                },
                color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp
            )
            Spacer(Modifier.height(6.dp))
            Text("Creado por Albert Silvestre", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Botón de la pantalla de entrada que abre el test vocacional (no necesita cuenta). */
@Composable
private fun BotonTest(onClick: () -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        Modifier.fillMaxWidth().clip(forma).background(Color.White.copy(alpha = 0.12f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), forma).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("🧭", fontSize = 28.sp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("¿Qué debo estudiar?", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Haz el test gratis, sin crear cuenta", color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
    }
}
