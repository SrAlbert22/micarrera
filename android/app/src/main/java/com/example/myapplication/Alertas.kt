package com.example.myapplication

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.serialization.builtins.ListSerializer
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Notificaciones del teléfono (sin servidor):
 *  - Alerta X minutos antes de cada clase.
 *  - Resumen cada mañana con las clases del día.
 *  - Fin de cada ronda del modo concentración.
 *  - En la alerta de clase, las tareas pendientes de esa materia.
 *
 * El horario se guarda en el teléfono para poder reprogramar las alarmas al reiniciar
 * y sin abrir la app. Cada vez que suena una alarma se programa la siguiente.
 */
object Alertas {
    const val CANAL_CLASES = "clases"
    const val CANAL_RESUMEN = "resumen"
    const val CANAL_ESTUDIO = "estudio"

    private const val PREFS = "alertas"
    private const val ACCION_CLASE = "com.example.myapplication.ALERTA_CLASE"
    private const val ACCION_RESUMEN = "com.example.myapplication.RESUMEN_DIA"
    private const val ACCION_AVISO = "com.example.myapplication.AVISO_ESTUDIO"
    private const val CODIGO_RESUMEN = 1
    private const val CODIGO_AVISO = 2
    private const val CODIGO_BASE_CLASES = 1000
    val MINUTOS_OPCIONES = listOf(5, 10, 15, 30)
    val HORA_RESUMEN: LocalTime = LocalTime.of(7, 0)

    // ---------- Preferencias ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clasesActivadas(ctx: Context) = prefs(ctx).getBoolean("clases", true)
    fun resumenActivado(ctx: Context) = prefs(ctx).getBoolean("resumen", true)
    fun minutosAntes(ctx: Context) = prefs(ctx).getInt("minutos", 15)

    fun configurar(ctx: Context, clases: Boolean? = null, resumen: Boolean? = null, minutos: Int? = null) {
        prefs(ctx).edit().apply {
            clases?.let { putBoolean("clases", it) }
            resumen?.let { putBoolean("resumen", it) }
            minutos?.let { putInt("minutos", it) }
        }.apply()
        reprogramar(ctx)
    }

    fun tienePermiso(ctx: Context) =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    // ---------- Programar ----------

    /** Guarda el horario en el teléfono y reprograma todas las alarmas. */
    fun programar(ctx: Context, clases: List<Clase>) {
        prefs(ctx).edit().putString("horario", json.encodeToString(ListSerializer(Clase.serializer()), clases)).apply()
        reprogramar(ctx)
    }

    /** Guarda en el teléfono los pendientes (sin hacer) para mencionarlos en la alerta de cada clase. */
    fun guardarPendientes(ctx: Context, tareas: List<Tarea>) {
        val pendientes = tareas.filter { !it.hecha && it.materia != null }.take(60)
        prefs(ctx).edit().putString("pendientes", json.encodeToString(ListSerializer(Tarea.serializer()), pendientes)).apply()
    }

    private fun pendientesGuardados(ctx: Context): List<Tarea> =
        prefs(ctx).getString("pendientes", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(Tarea.serializer()), it) }.getOrNull() }
            .orEmpty()

    /** "📌 Pendiente: Práctica 3 (vence mañana)" para la materia de la clase, o null. */
    fun textoPendientes(tareas: List<Tarea>, materia: String, ahora: LocalDateTime = LocalDateTime.now()): String? {
        fun norm(t: String) = java.text.Normalizer.normalize(t, java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase().trim()
        val m = norm(materia)
        val deEsta = tareas.filter { t -> !t.hecha && t.materia?.let { norm(it) }?.let { it.isNotEmpty() && (it.contains(m) || m.contains(it)) } == true }
            .sortedBy { it.entrega ?: "9999" }
        if (deEsta.isEmpty()) return null
        val primera = deEsta.first()
        val vence = primera.entrega?.let { runCatching { java.time.OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.systemDefault()).toLocalDate() }.getOrNull() }
        val cuando = when (vence) {
            null -> ""
            ahora.toLocalDate() -> " (vence hoy)"
            ahora.toLocalDate().plusDays(1) -> " (vence mañana)"
            else -> if (vence.isBefore(ahora.toLocalDate())) " (atrasada)" else " (vence el ${vence.dayOfMonth}/${vence.monthValue})"
        }
        val mas = if (deEsta.size > 1) " y ${deEsta.size - 1} más" else ""
        return "📌 Pendiente: ${primera.titulo}$cuando$mas"
    }

    /** Aviso del modo concentración dentro de [ms] milisegundos (suena aunque la app esté cerrada). */
    fun avisarEn(ctx: Context, ms: Long, titulo: String, texto: String) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val extras = Intent().apply { putExtra("titulo", titulo); putExtra("texto", texto) }
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, intentAlarma(ctx, ACCION_AVISO, CODIGO_AVISO, extras))
    }

    fun cancelarAviso(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(intentAlarma(ctx, ACCION_AVISO, CODIGO_AVISO, null))
    }

    private fun horarioGuardado(ctx: Context): List<Clase> =
        prefs(ctx).getString("horario", null)
            ?.let { runCatching { json.decodeFromString(ListSerializer(Clase.serializer()), it) }.getOrNull() }
            .orEmpty()

    fun reprogramar(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val p = prefs(ctx)

        // Cancela lo que había.
        repeat(p.getInt("programadas", 0)) { i -> am.cancel(intentAlarma(ctx, ACCION_CLASE, CODIGO_BASE_CLASES + i, null)) }
        am.cancel(intentAlarma(ctx, ACCION_RESUMEN, CODIGO_RESUMEN, null))

        val ahora = LocalDateTime.now()
        val clases = horarioGuardado(ctx)
        var n = 0
        if (clasesActivadas(ctx)) {
            val antes = minutosAntes(ctx)
            clases.forEachIndexed { i, c ->
                val cuando = proximaAlerta(c.dia, c.horaInicio, antes, ahora) ?: return@forEachIndexed
                val extras = Intent().apply {
                    putExtra("titulo", "${c.materia} en $antes min")
                    putExtra("materia", c.materia)
                    putExtra("texto", listOfNotNull(horaBonita(c.horaInicio), c.aula?.let { "Aula $it" }, c.profesor).joinToString(" · "))
                }
                poner(am, cuando, intentAlarma(ctx, ACCION_CLASE, CODIGO_BASE_CLASES + i, extras))
                n = i + 1
            }
        }
        p.edit().putInt("programadas", n).apply()

        if (resumenActivado(ctx) && clases.isNotEmpty()) {
            poner(am, proximoResumen(ahora), intentAlarma(ctx, ACCION_RESUMEN, CODIGO_RESUMEN, null))
        }
    }

    private fun poner(am: AlarmManager, cuando: LocalDateTime, pi: PendingIntent) {
        val ms = cuando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Alarma "casi exacta": no pide permisos especiales; Android puede correrla unos minutos.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
    }

    private fun intentAlarma(ctx: Context, accion: String, codigo: Int, extras: Intent?): PendingIntent {
        val i = Intent(ctx, AlarmaReceiver::class.java).setAction(accion).putExtra("codigo", codigo)
        extras?.extras?.let { i.putExtras(it) }
        return PendingIntent.getBroadcast(ctx, codigo, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // ---------- Cálculos (se prueban en LectorAlertasTest) ----------

    /** Próximo momento en que debe sonar la alerta de una clase (día 1 = lunes). */
    fun proximaAlerta(dia: Int, hora: String, minutosAntes: Int, ahora: LocalDateTime): LocalDateTime? {
        if (dia !in 1..7) return null
        val (h, m) = hora.take(5).split(":").map { it.toIntOrNull() ?: return null }.let { it[0] to it.getOrElse(1) { 0 } }
        var inicio = ahora.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(dia))).atTime(h, m)
        if (!inicio.minusMinutes(minutosAntes.toLong()).isAfter(ahora)) inicio = inicio.plusWeeks(1)
        return inicio.minusMinutes(minutosAntes.toLong())
    }

    fun proximoResumen(ahora: LocalDateTime): LocalDateTime {
        val hoy = ahora.toLocalDate().atTime(HORA_RESUMEN)
        return if (hoy.isAfter(ahora)) hoy else hoy.plusDays(1)
    }

    fun textoResumen(clases: List<Clase>, dia: Int): String? {
        val deHoy = clases.filter { it.dia == dia }.sortedBy { it.horaInicio.take(5) }
        if (deHoy.isEmpty()) return null
        val primera = deHoy.first()
        return if (deHoy.size == 1) "Hoy tienes ${primera.materia} a las ${horaBonita(primera.horaInicio)}."
        else "Hoy tienes ${deHoy.size} clases. La primera: ${primera.materia} a las ${horaBonita(primera.horaInicio)}."
    }

    fun horaBonita(h: String): String {
        val (hh, mm) = h.take(5).split(":").map { it.toIntOrNull() ?: 0 }.let { it[0] to it.getOrElse(1) { 0 } }
        val h12 = when { hh == 0 -> 12; hh > 12 -> hh - 12; else -> hh }
        return "%d:%02d %s".format(h12, mm, if (hh < 12) "am" else "pm")
    }

    // ---------- Mostrar ----------

    fun crearCanales(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_CLASES, "Alertas de clase", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Te avisa antes de que empiece cada clase." }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_RESUMEN, "Resumen del día", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Cada mañana, tus clases de hoy." }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ESTUDIO, "Modo concentración", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Te avisa cuando termina tu ronda de estudio o tu descanso." }
        )
    }

    fun mostrar(ctx: Context, canal: String, id: Int, titulo: String, texto: String) {
        if (!tienePermiso(ctx)) return
        crearCanales(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, canal)
            .setSmallIcon(R.drawable.ic_notificacion)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setColor(0xFF7C83F7.toInt())
            .setContentIntent(abrir)
            .setAutoCancel(true)
            .setCategory(if (canal == CANAL_CLASES) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_EVENT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(id, n) }
    }

    fun probar(ctx: Context) {
        mostrar(ctx, CANAL_CLASES, 9999, "¡Las alertas funcionan! 🔔", "Así te avisaremos antes de cada clase.")
    }

    internal fun alSonar(ctx: Context, intent: Intent) {
        when (intent.action) {
            ACCION_CLASE -> {
                val pendiente = intent.getStringExtra("materia")?.let { textoPendientes(pendientesGuardados(ctx), it) }
                mostrar(
                    ctx, CANAL_CLASES, intent.getIntExtra("codigo", 0), intent.getStringExtra("titulo").orEmpty(),
                    listOfNotNull(intent.getStringExtra("texto")?.ifBlank { null }, pendiente).joinToString("\n")
                )
            }
            ACCION_AVISO -> {
                mostrar(ctx, CANAL_ESTUDIO, CODIGO_AVISO, intent.getStringExtra("titulo").orEmpty(), intent.getStringExtra("texto").orEmpty())
                return // no toca las alarmas de clase
            }
            ACCION_RESUMEN -> textoResumen(horarioGuardado(ctx), LocalDateTime.now().dayOfWeek.value)?.let {
                mostrar(ctx, CANAL_RESUMEN, CODIGO_RESUMEN, "Tu día ☀️", it)
            }
        }
        reprogramar(ctx) // programa la siguiente vez
    }
}

/** Recibe las alarmas que programa [Alertas]. Solo la usa la propia app. */
class AlarmaReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Alertas.alSonar(context, intent)
}

/** Al reiniciar el teléfono, Android borra las alarmas: las volvemos a programar. */
class ReinicioReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Alertas.reprogramar(context)
        }
    }
}
