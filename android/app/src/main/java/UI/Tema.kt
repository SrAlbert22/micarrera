package com.example.myapplication.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 🎨 Colores de MiCarrera. Hay una paleta oscura y una clara; se usa la del teléfono.
@Immutable
data class Paleta(
    val fondo: Color,
    val tarjeta: Color,
    val tarjetaAlta: Color,   // pastillas, chips, campos
    val borde: Color,
    val texto: Color,
    val textoSuave: Color,
    val acento: Color,
    val acentoSuave: Color,   // fondo de chips/etiquetas del acento
    val verde: Color,
    val verdeFondo: Color,
    val ambar: Color,
    val ambarFondo: Color,
    val gris: Color,
    val rojo: Color,
    val degradado: List<Color>,
    // Gráficas: tonos validados para rellenar barras sobre la tarjeta (contraste y daltonismo).
    val graficaAprobado: Color,
    val graficaCurso: Color
)

private val Oscura = Paleta(
    fondo = Color(0xFF12132A),
    tarjeta = Color(0xFF1C1E3D),
    tarjetaAlta = Color(0xFF262953),
    borde = Color(0xFF2E3163),
    texto = Color(0xFFECEDFF),
    textoSuave = Color(0xFF9A9EC9),
    acento = Color(0xFF7C83F7),
    acentoSuave = Color(0xFF2C3070),
    verde = Color(0xFF3DD68C),
    verdeFondo = Color(0xFF15372A),
    ambar = Color(0xFFF5B83D),
    ambarFondo = Color(0xFF3A2E14),
    gris = Color(0xFF6E739E),
    rojo = Color(0xFFFF6B6B),
    degradado = listOf(Color(0xFF12132A), Color(0xFF232567), Color(0xFF3D3A9E)),
    graficaAprobado = Color(0xFF26A36D),
    graficaCurso = Color(0xFFBF8A1F)
)

private val Clara = Paleta(
    fondo = Color(0xFFF4F5FB),
    tarjeta = Color.White,
    tarjetaAlta = Color(0xFFEDEFF7),
    borde = Color(0xFFE1E3F0),
    texto = Color(0xFF1F2244),
    textoSuave = Color(0xFF6B7094),
    acento = Color(0xFF5B63D3),
    acentoSuave = Color(0xFFE8EAFF),
    verde = Color(0xFF1E9E63),
    verdeFondo = Color(0xFFE3F5EC),
    ambar = Color(0xFFD48A0B),
    ambarFondo = Color(0xFFFDF1DC),
    gris = Color(0xFFA3A7C2),
    rojo = Color(0xFFD64545),
    degradado = listOf(Color(0xFF1F2244), Color(0xFF363B8C), Color(0xFF5B63D3)),
    graficaAprobado = Color(0xFF1E9E63),
    graficaCurso = Color(0xFFB97A00)
)

private val LocalPaleta = staticCompositionLocalOf { Oscura }

val colores: Paleta
    @Composable @ReadOnlyComposable get() = LocalPaleta.current

// Atajos con los nombres que ya usan las pantallas.
val FondoApp: Color @Composable @ReadOnlyComposable get() = colores.fondo
val Tarjeta: Color @Composable @ReadOnlyComposable get() = colores.tarjeta
val AzulNoche: Color @Composable @ReadOnlyComposable get() = colores.texto
val TextoSuave: Color @Composable @ReadOnlyComposable get() = colores.textoSuave
val Indigo: Color @Composable @ReadOnlyComposable get() = colores.acento
val Lavanda: Color @Composable @ReadOnlyComposable get() = colores.acentoSuave
val VerdeOk: Color @Composable @ReadOnlyComposable get() = colores.verde
val AmbarCurso: Color @Composable @ReadOnlyComposable get() = colores.ambar
val GrisPendiente: Color @Composable @ReadOnlyComposable get() = colores.gris
val DegradadoMarca: Brush @Composable @ReadOnlyComposable get() = Brush.linearGradient(colores.degradado)

/** Tema elegido en Cuenta → Apariencia: "sistema" (como el teléfono), "oscuro" o "claro". */
object Apariencia {
    val OPCIONES = listOf(
        Triple("sistema", "Automático", "Igual que tu teléfono"),
        Triple("oscuro", "Oscuro", "Fondo azul noche, ideal de noche"),
        Triple("claro", "Claro", "Fondo blanco, se lee mejor al sol"),
    )
    var tema by mutableStateOf("sistema")
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("apariencia", Context.MODE_PRIVATE)
    fun cargar(c: Context) { tema = prefs(c).getString("tema", "sistema") ?: "sistema" }
    fun cambiar(c: Context, nuevo: String) { tema = nuevo; prefs(c).edit().putString("tema", nuevo).apply() }

    /** ¿Toca pintar oscuro? */
    @Composable
    fun oscuro(): Boolean = when (tema) { "oscuro" -> true; "claro" -> false; else -> isSystemInDarkTheme() }
}

@Composable
fun MiCarreraTema(oscuro: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val p = if (oscuro) Oscura else Clara
    val base = if (oscuro) darkColorScheme() else lightColorScheme()
    val esquema = base.copy(
        primary = p.acento, onPrimary = Color.White,
        primaryContainer = p.acentoSuave, onPrimaryContainer = p.texto,
        secondary = p.acento, onSecondary = Color.White,
        background = p.fondo, onBackground = p.texto,
        surface = p.tarjeta, onSurface = p.texto,
        surfaceVariant = p.tarjetaAlta, onSurfaceVariant = p.textoSuave,
        surfaceContainerLowest = p.tarjeta, surfaceContainerLow = p.tarjeta,
        surfaceContainer = p.tarjeta, surfaceContainerHigh = p.tarjeta,
        surfaceContainerHighest = p.tarjetaAlta,
        outline = p.borde, outlineVariant = p.borde,
        error = p.rojo
    )
    CompositionLocalProvider(LocalPaleta provides p) {
        MaterialTheme(
            colorScheme = esquema,
            shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(24.dp)
            ),
            content = content
        )
    }
}

/** Título grande de cada pestaña ("Pensum", "Horario"…) con acciones a la derecha. */
@Composable
fun TituloPantalla(titulo: String, acciones: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(titulo, color = colores.texto, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f))
        acciones()
    }
}

/** Tarjeta redondeada con borde fino, la base de casi todo el diseño. */
@Composable
fun TarjetaMC(
    modifier: Modifier = Modifier,
    borde: Color = colores.borde,
    fondo: Color = colores.tarjeta,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val forma = RoundedCornerShape(24.dp)
    Column(
        modifier
            .clip(forma)
            .background(fondo)
            .border(1.dp, borde, forma)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        content = content
    )
}

/** Botón redondo tipo pastilla ("Actualizar", "Cambiar ▾"…). */
@Composable
fun BotonPastilla(
    texto: String,
    onClick: () -> Unit,
    icono: ImageVector? = null,
    destacado: Boolean = false,
    punto: Color? = null,
    enabled: Boolean = true,
    iconoFinal: ImageVector? = null
) {
    val c = colores
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (destacado) c.acento.copy(alpha = 0.6f) else c.borde),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (destacado) c.acentoSuave else c.tarjeta,
            contentColor = c.texto
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        modifier = Modifier.height(40.dp)
    ) {
        if (punto != null) {
            Box(Modifier.size(8.dp).background(punto, RoundedCornerShape(50)))
            Spacer(Modifier.width(8.dp))
        }
        if (icono != null) {
            Icon(icono, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(texto, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        if (iconoFinal != null) {
            Spacer(Modifier.width(4.dp))
            Icon(iconoFinal, null, modifier = Modifier.size(18.dp))
        }
    }
}

/** Encabezado de pantallas secundarias (con flecha para volver). */
@Composable
fun EncabezadoMarca(
    titulo: String,
    subtitulo: String? = null,
    alVolver: (() -> Unit)? = null,
    acciones: @Composable RowScope.() -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alVolver != null) {
            IconButton(onClick = alVolver) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = colores.texto)
            }
        } else Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, color = colores.texto, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            subtitulo?.let { Text(it, color = colores.textoSuave, fontSize = 14.sp) }
        }
        acciones()
    }
}

/** Etiqueta redondeada de color (Aprobada, En curso, Hoy…). */
@Composable
fun EtiquetaEstado(texto: String, color: Color, fondo: Color = color.copy(alpha = 0.16f)) {
    Text(
        texto, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(fondo, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** Título pequeño en mayúsculas para separar secciones ("PRIVACIDAD", "FILTRO"…). */
@Composable
fun Seccion(texto: String, modifier: Modifier = Modifier) {
    Text(
        texto.uppercase(), color = colores.textoSuave, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp, modifier = modifier
    )
}
