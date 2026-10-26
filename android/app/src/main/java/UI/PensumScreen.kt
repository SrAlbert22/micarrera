package com.example.myapplication.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.CarreraDetalle
import com.example.myapplication.Materia
import com.example.myapplication.Progreso
import com.example.myapplication.Repo
import kotlinx.coroutines.launch

private val SIGUIENTE = mapOf("pendiente" to "en_curso", "en_curso" to "aprobada", "aprobada" to "pendiente")

private enum class Filtro(val nombre: String) {
    TODAS("Todas"), DISPONIBLES("Disponibles"), EN_CURSO("En curso"), APROBADAS("Aprobadas"), PENDIENTES("Pendientes")
}

private fun norm(codigo: String) = codigo.uppercase().filterNot { it == '-' || it.isWhitespace() }

/** 1 → "1.º" */
private fun ordinal(n: Int) = "$n.º"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PensumScreen(carreraId: String, onActualizarPortal: () -> Unit, onCambiarCarrera: () -> Unit) {
    val scope = rememberCoroutineScope()
    var materias by remember { mutableStateOf<List<Materia>>(emptyList()) }
    val estados = remember { mutableStateMapOf<String, String>() }
    val notas = remember { mutableStateMapOf<String, Progreso>() }
    var carrera by remember { mutableStateOf<CarreraDetalle?>(null) }
    var indice by remember { mutableStateOf<Double?>(null) }
    var conectado by remember { mutableStateOf(false) }
    var cargando by remember { mutableStateOf(true) }
    var filtro by remember { mutableStateOf(Filtro.TODAS) }
    val abiertos = remember { mutableStateMapOf<Int, Boolean>() }

    LaunchedEffect(carreraId) {
        runCatching {
            materias = Repo.materias(carreraId)
            Repo.progreso().forEach {
                estados[it.materiaId] = it.estado
                if (it.nota != null || it.letra != null) notas[it.materiaId] = it
            }
        }
        runCatching { carrera = Repo.carreraDetalle(carreraId) }
        runCatching { Repo.perfil()?.let { indice = it.indice; conectado = it.portalActualizado != null } }
        cargando = false
    }

    Column(Modifier.fillMaxSize().background(FondoApp)) {
        TituloPantalla("Pensum") {
            BotonPastilla("Actualizar", onActualizarPortal, punto = if (conectado) VerdeOk else GrisPendiente)
            BotonPastilla("Cambiar", onCambiarCarrera, iconoFinal = Icons.Default.KeyboardArrowDown)
        }

        if (cargando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Column
        }

        val estadoDe = { m: Materia -> estados[m.id] ?: "pendiente" }
        val porCodigo = materias.associateBy { norm(it.codigo) }
        val estadoDeCodigo = { c: String -> porCodigo[norm(c)]?.let(estadoDe) }
        val disponible = { m: Materia ->
            estadoDe(m) == "pendiente" && m.prerequisitos.all { estadoDeCodigo(it) == "aprobada" || porCodigo[norm(it)] == null }
        }
        val cuenta = mapOf(
            Filtro.TODAS to materias.size,
            Filtro.DISPONIBLES to materias.count(disponible),
            Filtro.EN_CURSO to materias.count { estadoDe(it) == "en_curso" },
            Filtro.APROBADAS to materias.count { estadoDe(it) == "aprobada" },
            Filtro.PENDIENTES to materias.count { estadoDe(it) == "pendiente" }
        )
        val pasa = { m: Materia ->
            when (filtro) {
                Filtro.TODAS -> true
                Filtro.DISPONIBLES -> disponible(m)
                Filtro.EN_CURSO -> estadoDe(m) == "en_curso"
                Filtro.APROBADAS -> estadoDe(m) == "aprobada"
                Filtro.PENDIENTES -> estadoDe(m) == "pendiente"
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { TarjetaResumen(carrera, materias, estadoDe, indice, onActualizarPortal) }

            // Filtros
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Seccion("Filtro")
                    Filtro.entries.forEach { f ->
                        val (color, fondo) = when (f) {
                            Filtro.EN_CURSO -> AmbarCurso to colores.ambarFondo
                            Filtro.APROBADAS -> VerdeOk to colores.verdeFondo
                            Filtro.DISPONIBLES -> Indigo to Lavanda
                            else -> TextoSuave to colores.tarjeta
                        }
                        ChipFiltro("${f.nombre} ${cuenta[f]}", color, fondo, seleccionado = filtro == f) {
                            filtro = if (filtro == f) Filtro.TODAS else f
                        }
                    }
                }
            }

            // Ayudante: materias que ya se pueden inscribir
            val nDisponibles = cuenta.getValue(Filtro.DISPONIBLES)
            if (filtro == Filtro.TODAS && nDisponibles > 0) item {
                TarjetaMC(
                    Modifier.fillMaxWidth(), borde = Indigo.copy(alpha = 0.6f), fondo = Lavanda.copy(alpha = 0.35f),
                    onClick = { filtro = Filtro.DISPONIBLES }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✨", fontSize = 22.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Puedes inscribir $nDisponibles materias", color = Indigo,
                                fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Ya cumples sus requisitos. Toca para verlas.", color = TextoSuave, fontSize = 13.sp)
                        }
                    }
                }
            }

            val grupos = materias.groupBy { it.cuatrimestre }.toSortedMap()
            grupos.forEach { (periodo, lista) ->
                val visibles = lista.filter(pasa)
                if (visibles.isEmpty()) return@forEach
                val completo = lista.all { estadoDe(it) == "aprobada" }
                val abierto = if (filtro != Filtro.TODAS) true else abiertos[periodo] ?: !completo

                item(key = "periodo-$periodo") {
                    if (completo && !abierto) {
                        PeriodoCompleto(periodo, lista) { abiertos[periodo] = true }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            EncabezadoPeriodo(periodo, lista, estadoDe, abierto) { abiertos[periodo] = !abierto }
                            if (abierto) TarjetaMC(Modifier.fillMaxWidth()) {
                                visibles.forEachIndexed { i, m ->
                                    if (i > 0) HorizontalDivider(color = colores.borde)
                                    FilaMateria(
                                        m = m,
                                        estado = estadoDe(m),
                                        disponible = disponible(m),
                                        nota = notas[m.id],
                                        estadoDeCodigo = estadoDeCodigo
                                    ) {
                                        val nuevo = SIGUIENTE.getValue(estadoDe(m))
                                        estados[m.id] = nuevo
                                        scope.launch { runCatching { Repo.marcar(m.id, nuevo) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (materias.isEmpty()) item {
                Text("Esta carrera todavía no tiene materias cargadas.", color = TextoSuave,
                    modifier = Modifier.padding(16.dp))
            } else item {
                Text("Toca una materia para cambiar su estado: pendiente → en curso → aprobada.",
                    fontSize = 12.sp, color = TextoSuave, modifier = Modifier.padding(horizontal = 4.dp))
            }
        }
    }
}

@Composable
private fun TarjetaResumen(
    carrera: CarreraDetalle?,
    materias: List<Materia>,
    estadoDe: (Materia) -> String,
    indice: Double?,
    onActualizarPortal: () -> Unit
) {
    val credTotal = materias.sumOf { it.creditos }
    val credAprob = materias.filter { estadoDe(it) == "aprobada" }.sumOf { it.creditos }
    val credCurso = materias.filter { estadoDe(it) == "en_curso" }.sumOf { it.creditos }
    val nAprob = materias.count { estadoDe(it) == "aprobada" }
    val nCurso = materias.count { estadoDe(it) == "en_curso" }

    TarjetaMC(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            LogoUniversidad(carrera?.universidades, 56.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(carrera?.nombre ?: "Mi carrera", color = AzulNoche, fontSize = 19.sp,
                    fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                carrera?.universidades?.let {
                    Text(it.nombre, color = TextoSuave, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        HorizontalDivider(color = colores.borde)
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Estadistica(Modifier.weight(1f), "Créditos", "$credAprob", "/ $credTotal", credAprob, credCurso, credTotal)
            Estadistica(Modifier.weight(1f), "Materias", "$nAprob", "/ ${materias.size}", nAprob, nCurso, materias.size)
            Column(Modifier.weight(1f)) {
                Seccion("Índice")
                Spacer(Modifier.height(6.dp))
                if (indice != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%.2f".format(indice), color = Indigo, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Text(" / 4.00", color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    BarraDoble((indice / 4.0).toFloat(), 0f)
                } else {
                    Text("—", color = TextoSuave, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Conectar portal", color = Indigo, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onActualizarPortal))
                }
            }
        }
    }
}

@Composable
private fun Estadistica(
    modifier: Modifier, titulo: String, valor: String, total: String, hechos: Int, enCurso: Int, de: Int
) {
    val base = de.coerceAtLeast(1)
    Column(modifier) {
        Seccion(titulo)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(valor, color = AzulNoche, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(" $total", color = TextoSuave, fontSize = 14.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(8.dp))
        BarraDoble(hechos.toFloat() / base, enCurso.toFloat() / base)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${hechos * 100 / base}%", color = Indigo, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (enCurso > 0) Text(" +${enCurso * 100 / base}% en curso", color = TextoSuave, fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp))
        }
    }
}

/** Barra con lo aprobado (sólido) y lo que está en curso (más claro). */
@Composable
private fun BarraDoble(hecho: Float, enCurso: Float) {
    val forma = RoundedCornerShape(50)
    Box(Modifier.fillMaxWidth().height(8.dp).clip(forma).background(colores.tarjetaAlta)) {
        Box(Modifier.fillMaxWidth((hecho + enCurso).coerceIn(0f, 1f)).fillMaxHeight().background(Indigo.copy(alpha = 0.4f)))
        Box(Modifier.fillMaxWidth(hecho.coerceIn(0f, 1f)).fillMaxHeight().background(Indigo))
    }
}

@Composable
private fun ChipFiltro(texto: String, color: Color, fondo: Color, seleccionado: Boolean, onClick: () -> Unit) {
    val forma = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .clip(forma)
            .background(if (seleccionado) fondo else colores.tarjeta)
            .border(1.dp, if (seleccionado) color else colores.borde, forma)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(texto, color = if (seleccionado) color else AzulNoche, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun PeriodoCompleto(periodo: Int, lista: List<Materia>, onAbrir: () -> Unit) {
    TarjetaMC(
        Modifier.fillMaxWidth(), borde = VerdeOk.copy(alpha = 0.3f), fondo = colores.verdeFondo, onClick = onAbrir
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(VerdeOk, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Check, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("${ordinal(periodo)} período", color = VerdeOk, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("${lista.size} materias aprobadas · ${lista.sumOf { it.creditos }} cr.",
                    color = VerdeOk.copy(alpha = 0.8f), fontSize = 13.sp)
            }
            Icon(Icons.Default.KeyboardArrowDown, "Ver materias", tint = VerdeOk)
        }
    }
}

@Composable
private fun EncabezadoPeriodo(
    periodo: Int, lista: List<Materia>, estadoDe: (Materia) -> String, abierto: Boolean, onToggle: () -> Unit
) {
    val enCurso = lista.count { estadoDe(it) == "en_curso" }
    val hechas = lista.count { estadoDe(it) == "aprobada" }
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle)
            .padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("${ordinal(periodo)} período", color = AzulNoche, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        if (enCurso > 0) EtiquetaEstado("$enCurso en curso", AmbarCurso, colores.ambarFondo)
        Spacer(Modifier.weight(1f))
        Text("$hechas/${lista.size} mat.", color = TextoSuave, fontSize = 14.sp)
        Icon(if (abierto) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = TextoSuave)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilaMateria(
    m: Materia,
    estado: String,
    disponible: Boolean,
    nota: Progreso?,
    estadoDeCodigo: (String) -> String?,
    onClick: () -> Unit
) {
    val aprobada = estado == "aprobada"
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconoEstado(estado, disponible)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.nombre, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                color = if (aprobada) TextoSuave else AzulNoche,
                textDecoration = if (aprobada) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(m.codigo, color = TextoSuave, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                if (m.creditos > 0) Text("· ${m.creditos} cr.", color = TextoSuave, fontSize = 13.sp)
                m.prerequisitos.forEach { p ->
                    val color = when (estadoDeCodigo(p)) {
                        "aprobada" -> VerdeOk
                        "en_curso" -> AmbarCurso
                        null -> TextoSuave
                        else -> colores.rojo
                    }
                    Text("🔗 $p", color = color, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        when {
            nota != null && (nota.nota != null || nota.letra != null) -> Row(verticalAlignment = Alignment.Bottom) {
                nota.nota?.let {
                    Text(if (it % 1.0 == 0.0) it.toInt().toString() else "%.1f".format(it),
                        color = AzulNoche, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
                nota.letra?.let {
                    Text(" $it", color = TextoSuave, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            estado == "en_curso" -> EtiquetaEstado("En curso", AmbarCurso, colores.ambarFondo)
            disponible -> EtiquetaEstado("Disponible", Indigo, Lavanda)
        }
    }
}

@Composable
private fun IconoEstado(estado: String, disponible: Boolean) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        when (estado) {
            "aprobada" -> Box(
                Modifier.fillMaxSize().background(VerdeOk, CircleShape), contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Check, "Aprobada", tint = Color.White, modifier = Modifier.size(18.dp)) }
            "en_curso" -> CircularProgressIndicator(
                progress = { 0.65f }, modifier = Modifier.fillMaxSize(), color = AmbarCurso,
                trackColor = Color.Transparent, strokeWidth = 3.dp, strokeCap = StrokeCap.Round
            )
            else -> Box(
                Modifier.fillMaxSize().border(2.dp, if (disponible) Indigo else GrisPendiente, CircleShape)
            )
        }
    }
}
