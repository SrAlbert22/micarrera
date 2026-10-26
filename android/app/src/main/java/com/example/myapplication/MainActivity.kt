package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.myapplication.ui.Apariencia
import com.example.myapplication.ui.AvisoActualizacion
import com.example.myapplication.ui.FondoApp
import com.example.myapplication.ui.InicioScreen
import com.example.myapplication.ui.LoginScreen
import com.example.myapplication.ui.MiCarreraTema
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Apariencia.cargar(this)
        setContent {
            val oscuro = Apariencia.oscuro()
            // Iconos de la barra de arriba (hora, batería) claros u oscuros según el tema elegido.
            LaunchedEffect(oscuro) {
                val estilo = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { oscuro }
                enableEdgeToEdge(statusBarStyle = estilo, navigationBarStyle = estilo)
            }
            MiCarreraTema(oscuro) {
                Surface(Modifier.fillMaxSize(), color = FondoApp) { App() }
            }
        }
    }
}

@Composable
fun App() {
    val estado by supabase.auth.sessionStatus.collectAsState()
    when (estado) {
        is SessionStatus.Authenticated -> InicioScreen()
        is SessionStatus.NotAuthenticated -> LoginScreen()
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
    AvisoActualizacion() // "Hay una versión nueva", con o sin sesión
}
