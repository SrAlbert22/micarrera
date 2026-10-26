package com.example.myapplication

import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.functions.functions
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.put
import java.time.Instant

object Repo {
    private val uid: String
        get() = supabase.auth.currentUserOrNull()?.id ?: error("No hay sesión iniciada")

    suspend fun universidades(): List<Universidad> =
        supabase.from("universidades").select { order("nombre", Order.ASCENDING) }.decodeList()

    // Test vocacional: se puede hacer sin iniciar sesión (las tablas se leen como anónimo).
    suspend fun preguntasVocacionales(): List<PreguntaVocacional> =
        supabase.from("vocacional_preguntas").select { order("id", Order.ASCENDING) }.decodeList()

    suspend fun carrerasVocacionales(): List<CarreraVocacional> =
        supabase.from("vocacional_carreras").select { order("nombre", Order.ASCENDING) }.decodeList()

    suspend fun carreras(universidadId: String): List<Carrera> =
        supabase.from("carreras").select {
            filter { eq("universidad_id", universidadId) }
            order("nombre", Order.ASCENDING)
        }.decodeList()

    val correo: String? get() = supabase.auth.currentUserOrNull()?.email

    suspend fun ultimaVersion(): Version? =
        supabase.from("versiones").select {
            order("version_code", Order.DESCENDING)
            limit(1)
        }.decodeSingleOrNull()

    suspend fun carreraDetalle(carreraId: String): CarreraDetalle? =
        supabase.from("carreras").select(Columns.raw("id,nombre,universidades(id,nombre,siglas,portal_url,logo_url,aula_url)")) {
            filter { eq("id", carreraId) }
        }.decodeSingleOrNull()

    suspend fun guardarNombre(nombre: String) {
        supabase.from("perfiles").update(buildJsonObject { put("nombre", nombre.trim()) }) {
            filter { eq("id", uid) }
        }
    }

    suspend fun perfil(): Perfil? =
        supabase.from("perfiles").select { filter { eq("id", uid) } }.decodeSingleOrNull()

    suspend fun guardarCarrera(carreraId: String?) {
        supabase.from("perfiles").upsert(Perfil(uid, carreraId))
    }

    suspend fun materias(carreraId: String): List<Materia> =
        supabase.from("materias").select {
            filter { eq("carrera_id", carreraId) }
            order("cuatrimestre", Order.ASCENDING)
        }.decodeList()

    suspend fun progreso(): List<Progreso> =
        supabase.from("progreso").select { filter { eq("usuario_id", uid) } }.decodeList()

    /** Cambio manual de estado. Solo envía estado y origen para no borrar la nota que vino del portal. */
    suspend fun marcar(materiaId: String, estado: String) {
        supabase.from("progreso").upsert(buildJsonObject {
            put("usuario_id", uid)
            put("materia_id", materiaId)
            put("estado", estado)
            put("origen", "manual")
        }) {
            onConflict = "usuario_id,materia_id"
        }
    }

    /** Guarda en progreso las materias del récord que coinciden con el pensum, y el índice en perfiles. */
    suspend fun guardarRecord(carreraId: String, record: RecordPortal): ResultadoImportacion {
        val porCodigo = materias(carreraId).associateBy { normalizarCodigo(it.codigo) }
        // Una materia puede salir varias veces (p. ej. reprobada y luego aprobada): nos quedamos con la mejor.
        val prioridad = mapOf("aprobada" to 3, "en_curso" to 2, "reprobada" to 1, "retirada" to 0)
        val sinCoincidir = mutableListOf<String>()
        val filas = record.materias
            .mapNotNull { m ->
                val materia = porCodigo[normalizarCodigo(m.codigo)]
                if (materia == null) { sinCoincidir += m.codigo; null } else materia.id to m
            }
            .groupBy({ it.first }, { it.second })
            .map { (materiaId, intentos) ->
                val m = intentos.maxBy { prioridad[it.estado] ?: -1 }
                Progreso(
                    usuarioId = uid,
                    materiaId = materiaId,
                    // La tabla solo acepta pendiente/en_curso/aprobada.
                    estado = if (m.estado == "aprobada" || m.estado == "en_curso") m.estado else "pendiente",
                    nota = m.nota,
                    letra = m.letra,
                    origen = "portal"
                )
            }

        if (filas.isNotEmpty()) {
            supabase.from("progreso").upsert(filas) { onConflict = "usuario_id,materia_id" }
        }
        supabase.from("perfiles").update(buildJsonObject {
            record.indice?.let { put("indice", it) }
            put("portal_actualizado", Instant.now().toString())
        }) {
            filter { eq("id", uid) }
        }
        return ResultadoImportacion(filas.size, sinCoincidir.distinct())
    }

    private fun normalizarCodigo(codigo: String) =
        codigo.uppercase().filterNot { it == '-' || it.isWhitespace() }

    suspend fun horario(): List<Clase> =
        supabase.from("horario").select {
            filter { eq("usuario_id", uid) }
            order("hora_inicio", Order.ASCENDING)
        }.decodeList()

    suspend fun agregarClases(clases: List<Clase>) {
        if (clases.isEmpty()) return
        supabase.from("horario").insert(clases.map { it.copy(id = null, usuarioId = uid) })
    }

    /** Agrega al horario las clases importadas del portal que todavía no estén. Devuelve (agregadas, repetidas). */
    suspend fun guardarHorarioImportado(clases: List<Clase>): Pair<Int, Int> {
        fun clave(c: Clase) = Triple(c.materia.trim().lowercase(), c.dia, c.horaInicio.take(5))
        val existentes = horario().map(::clave).toSet()
        val nuevas = clases.filter { clave(it) !in existentes }
        agregarClases(nuevas)
        return nuevas.size to (clases.size - nuevas.size)
    }

    /** Guarda los cambios de una clase (editar). Envía todos los campos para poder borrar los que se vacíen. */
    suspend fun actualizarClase(c: Clase) {
        val id = c.id ?: return
        supabase.from("horario").update(buildJsonObject {
            put("materia", c.materia)
            put("dia", c.dia)
            put("hora_inicio", c.horaInicio)
            put("hora_fin", c.horaFin)
            put("aula", c.aula)
            put("profesor", c.profesor)
            put("codigo", c.codigo)
            put("grupo", c.grupo)
            put("apunte", c.apunte)
        }) {
            filter { eq("id", id) }
        }
    }

    suspend fun borrarClase(id: String) {
        supabase.from("horario").delete { filter { eq("id", id) } }
    }

    /** Envía la foto o el PDF a la Edge Function, que lo lee con IA (Gemini) y devuelve las clases. */
    suspend fun leerHorarioConIA(archivoBase64: String, tipo: String): List<Clase> {
        val resp = supabase.functions.invoke(
            function = "leer-horario",
            body = buildJsonObject {
                put("imagen", archivoBase64)
                put("tipo", tipo)
            }
        )
        return json.decodeFromString(resp.bodyAsText())
    }

    // ---------- Amigos ----------

    suspend fun misAmigos(): List<Persona> = supabase.postgrest.rpc("mis_amigos").decodeList()

    suspend fun sugerencias(): List<Persona> = supabase.postgrest.rpc("sugerencias_amigos").decodeList()

    suspend fun buscarUsuarios(texto: String): List<Persona> =
        supabase.postgrest.rpc("buscar_usuarios", buildJsonObject { put("texto", texto) }).decodeList()

    suspend fun usuarioDisponible(usuario: String): Boolean =
        supabase.postgrest.rpc("usuario_disponible", buildJsonObject { put("u", usuario) }).decodeAs()

    suspend fun guardarUsuario(usuario: String) {
        supabase.from("perfiles").update(buildJsonObject { put("usuario", CalculoAmigos.limpiarUsuario(usuario)) }) {
            filter { eq("id", uid) }
        }
    }

    /** buscable: universidad | usuario | nadie · horarioVisible: amigos | nadie */
    suspend fun guardarPrivacidad(buscable: String? = null, horarioVisible: String? = null) {
        supabase.from("perfiles").update(buildJsonObject {
            buscable?.let { put("buscable", it) }
            horarioVisible?.let { put("horario_visible", it) }
        }) { filter { eq("id", uid) } }
    }

    @kotlinx.serialization.Serializable
    private data class IdFila(val id: String)

    /** Envía la solicitud y le avisa a la otra persona (si el aviso falla, la solicitud queda igual). */
    suspend fun enviarSolicitud(para: String) {
        val fila = supabase.from("amistades").insert(buildJsonObject { put("para", para) }) { select() }.decodeSingle<IdFila>()
        avisarAmistad(fila.id, "solicitud")
    }

    suspend fun aceptarSolicitud(amistad: String) {
        supabase.from("amistades").update(buildJsonObject { put("estado", "aceptada") }) { filter { eq("id", amistad) } }
        avisarAmistad(amistad, "aceptada")
    }

    /** Rechazar, cancelar o eliminar amigo: se borra la amistad. */
    suspend fun quitarAmistad(amistad: String) {
        supabase.from("amistades").delete { filter { eq("id", amistad) } }
    }

    suspend fun bloquear(persona: String) {
        supabase.from("bloqueos").insert(buildJsonObject { put("bloqueado", persona) })
    }

    private suspend fun avisarAmistad(amistad: String, evento: String) {
        runCatching {
            supabase.functions.invoke(function = "enviar-notificacion", body = buildJsonObject {
                put("amistad", amistad); put("evento", evento)
            })
        }
    }

    /** Horarios de mis amigos que lo comparten (las reglas de Supabase ocultan el resto). */
    suspend fun horariosDe(ids: List<String>): Map<String, List<Clase>> {
        if (ids.isEmpty()) return emptyMap()
        // horarios_de: solo amigos que dejan ver su horario, y sin los apuntes privados.
        return supabase.postgrest.rpc("horarios_de", buildJsonObject { putJsonArray("ids") { ids.forEach { add(it) } } })
            .decodeList<Clase>().groupBy { it.usuarioId.orEmpty() }
    }

    // ---------- Notificaciones del servidor ----------

    suspend fun registrarDispositivo(token: String) {
        supabase.from("dispositivos").upsert(buildJsonObject {
            put("usuario_id", uid)
            put("token", token)
            put("plataforma", "android")
            put("actualizado", Instant.now().toString())
        }) {
            onConflict = "usuario_id,token"
        }
    }

    suspend fun quitarDispositivo(token: String) {
        supabase.from("dispositivos").delete { filter { eq("usuario_id", uid); eq("token", token) } }
    }

    /** Pide al servidor que te mande una notificación de prueba a tus teléfonos. */
    suspend fun probarPush() {
        supabase.functions.invoke(function = "enviar-notificacion", body = buildJsonObject { put("prueba", true) })
    }

    suspend fun salir() = supabase.auth.signOut()

    /** Seguridad: cierra la sesión en TODOS los teléfonos y computadoras. */
    suspend fun salirDeTodo() = supabase.auth.signOut(SignOutScope.GLOBAL)

    // ---------- 5inco: tutorial y soporte ----------

    suspend fun marcarTutorialVisto() {
        supabase.from("perfiles").update(buildJsonObject { put("tutorial_visto", true) }) { filter { eq("id", uid) } }
    }

    suspend fun guardarLogrosVisibles(visibles: Boolean) {
        supabase.from("perfiles").update(buildJsonObject { put("logros_visibles", visibles) }) { filter { eq("id", uid) } }
    }

    /** tipo: comentario | problema | ayuda. Le llega una notificación a Albert. */
    suspend fun enviarSoporte(tipo: String, mensaje: String, pantalla: String, version: String) {
        val fila = supabase.from("soporte").insert(buildJsonObject {
            put("tipo", tipo); put("mensaje", mensaje.trim()); put("pantalla", pantalla)
            put("plataforma", "android"); put("version", version)
        }) { select() }.decodeSingle<IdFila>()
        runCatching { supabase.functions.invoke(function = "enviar-notificacion", body = buildJsonObject { put("soporte", fila.id) }) }
    }

    suspend fun borrarMiHorario() { supabase.from("horario").delete { filter { eq("usuario_id", uid) } } }
    suspend fun reiniciarMiProgreso() { supabase.from("progreso").delete { filter { eq("usuario_id", uid) } } }

    /** Borra la cuenta y TODO lo suyo (lo hace el servidor, que confirma quién eres). */
    suspend fun eliminarMiCuenta() {
        supabase.functions.invoke(function = "eliminar-cuenta", body = buildJsonObject { put("confirmar", "ELIMINAR") })
        runCatching { supabase.auth.signOut() }
    }

    // ---------- Estudio: tareas, concentración y aula virtual ----------

    suspend fun tareas(): List<Tarea> =
        supabase.from("tareas").select(Columns.list("id", "titulo", "materia", "detalle", "entrega", "hecha", "origen")) {
            order("entrega", Order.ASCENDING, nullsFirst = false)
        }.decodeList()

    suspend fun guardarTarea(titulo: String, materia: String?, detalle: String?, entrega: String?, id: String? = null) {
        val fila = buildJsonObject {
            put("titulo", titulo.trim()); put("materia", materia?.trim()?.ifBlank { null })
            put("detalle", detalle?.trim()?.ifBlank { null }); put("entrega", entrega)
        }
        if (id != null) supabase.from("tareas").update(fila) { filter { eq("id", id) } }
        else supabase.from("tareas").insert(fila)
    }

    suspend fun marcarTarea(id: String, hecha: Boolean) {
        supabase.from("tareas").update(buildJsonObject { put("hecha", hecha) }) { filter { eq("id", id) } }
    }

    suspend fun borrarTarea(id: String) { supabase.from("tareas").delete { filter { eq("id", id) } } }

    suspend fun guardarSesion(minutos: Int, materia: String?) {
        supabase.from("sesiones_estudio").insert(buildJsonObject { put("minutos", minutos); put("materia", materia) })
    }

    suspend fun ranking(): List<Posicion> = supabase.postgrest.rpc("ranking_concentracion").decodeList()

    suspend fun estadoAula(): EstadoAula? =
        supabase.from("aulas_virtuales").select(Columns.list("estado", "ultimo_error", "sincronizada")).decodeSingleOrNull()

    /** Guarda el enlace del calendario (después nadie puede leerlo, ni la app) y trae las tareas. */
    suspend fun conectarAula(url: String): ResultadoAula {
        // Se guarda con una función del servidor: el enlace es secreto y nadie (ni la app) puede volver a leerlo
        supabase.postgrest.rpc("conectar_aula", buildJsonObject { put("enlace", url.trim()) })
        return sincronizarAula()
    }

    suspend fun sincronizarAula(): ResultadoAula {
        val r = runCatching { supabase.functions.invoke(function = "sincronizar-aula", body = buildJsonObject { }) }
        r.exceptionOrNull()?.let { e ->
            // El servidor explica el error en {"error": "..."}
            val detalle = Regex(""""error"\s*:\s*"([^"]+)"""").find(e.message.orEmpty())?.groupValues?.get(1)
            error(detalle ?: "No se pudo actualizar el aula virtual")
        }
        return json.decodeFromString(r.getOrThrow().bodyAsText())
    }

    suspend fun desconectarAula() { supabase.from("aulas_virtuales").delete { filter { eq("usuario_id", uid) } } }

    // ---------- Amigos: logros, chocar los 5 e invitaciones ----------

    suspend fun logrosAmigos(): List<Logro> = supabase.postgrest.rpc("logros_amigos").decodeList()

    suspend fun interacciones(): List<Interaccion> =
        supabase.from("interacciones").select(Columns.list("id", "de", "para", "tipo", "materia", "cuando", "mensaje", "respuesta", "creada")) {
            order("creada", Order.DESCENDING); limit(50)
        }.decodeList()

    private suspend fun nuevaInteraccion(para: String, tipo: String, materia: String?, cuando: String? = null, mensaje: String? = null) {
        val fila = supabase.from("interacciones").insert(buildJsonObject {
            put("para", para); put("tipo", tipo); put("materia", materia); put("cuando", cuando); put("mensaje", mensaje)
        }) { select() }.decodeSingle<IdFila>()
        runCatching { supabase.functions.invoke(function = "enviar-notificacion", body = buildJsonObject { put("interaccion", fila.id) }) }
    }

    suspend fun chocarLos5(para: String, materia: String) = nuevaInteraccion(para, "choca5", materia)
    suspend fun invitarAEstudiar(para: String, materia: String?, cuando: String?, mensaje: String?) =
        nuevaInteraccion(para, "invitacion", materia, cuando, mensaje)

    suspend fun responderInvitacion(id: String, si: Boolean) {
        supabase.from("interacciones").update(buildJsonObject { put("respuesta", if (si) "si" else "no") }) { filter { eq("id", id) } }
        runCatching {
            supabase.functions.invoke(function = "enviar-notificacion", body = buildJsonObject { put("interaccion", id); put("respuesta", true) })
        }
    }

    // ---------- Carreras que agregan los estudiantes ----------

    /** Lee el pensum (fotos, PDF o texto copiado) con IA; devuelve las materias para revisarlas. */
    suspend fun leerPensumConIA(archivos: List<Pair<String, String>>, texto: String): PensumLeido {
        val resp = supabase.functions.invoke(
            function = "leer-pensum",
            body = buildJsonObject {
                putJsonArray("archivos") { archivos.forEach { (datos, tipo) -> add(buildJsonObject { put("datos", datos); put("tipo", tipo) }) } }
                put("texto", texto)
            }
        )
        return json.decodeFromString(resp.bodyAsText())
    }

    /** Guarda la carrera con su pensum para todos (la base de datos lo valida). Si ya existía con ese nombre, devuelve esa. */
    suspend fun agregarCarrera(universidadId: String, nombre: String, materias: List<MateriaLeida>): CarreraAgregada =
        supabase.postgrest.rpc("agregar_carrera", buildJsonObject {
            put("universidad", universidadId)
            put("nombre", nombre)
            put("materias", json.encodeToJsonElement(materias))
        }).decodeAs()
}
