// Edge Function: trae las tareas del aula virtual (Moodle, Classroom, Canvas...) desde el ENLACE DEL CALENDARIO
// que el estudiante pega en la app. Ese enlace solo sirve para leer el calendario: no usamos su contraseña.
//
//  - Con sesión del estudiante: sincroniza SU aula (máx. una vez por minuto).
//  - Con el encabezado x-cron-key (pg_cron, cada 20 min): sincroniza a todos y manda recordatorios
//    de tareas que vencen en las próximas 24 horas.
// Tareas nuevas y recordatorios llegan como notificación al teléfono (Firebase) y al navegador (Web Push).
//
// Seguridad: solo https, nunca sigue redirecciones, rechaza IPs y nombres internos, solo acepta
// enlaces de calendario, máx. 3 MB y 15 s por aula. verify_jwt=false porque pg_cron no tiene sesión:
// la autorización la hace esta función (sesión válida o clave secreta de cron).

import { createClient } from "npm:@supabase/supabase-js@2";
import { b64url, enviarWeb } from "../_compartido/webpush.ts";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (c: unknown, status = 200) => new Response(JSON.stringify(c), { status, headers: { ...CORS, "content-type": "application/json" } });
const MAX_BYTES = 3_000_000;

// ---------- Validación del enlace ----------
export function urlValida(texto: string): boolean {
  let u: URL;
  try { u = new URL(texto); } catch { return false; }
  if (u.protocol !== "https:" || u.username || u.password || (u.port && u.port !== "443")) return false;
  const h = u.hostname.toLowerCase();
  if (h === "localhost" || h.endsWith(".local") || h.endsWith(".internal") || h.endsWith(".localhost")) return false;
  if (/^[\d.]+$/.test(h) || h.includes(":") || !h.includes(".")) return false; // nada de IPs
  if (h.endsWith("supabase.co") || h.endsWith("supabase.in")) return false;
  return /\/calendar\/export_execute\.php$/.test(u.pathname)            // Moodle
    || (h === "calendar.google.com" && u.pathname.startsWith("/calendar/ical/")) // Google Classroom / Calendar
    || /\/feeds\/calendars\/[^/]+\.ics$/.test(u.pathname)                // Canvas
    || u.pathname.toLowerCase().endsWith(".ics");                        // otros (Blackboard, etc.)
}

// ---------- iCalendar ----------
type Evento = Record<string, { valor: string; params: string }>;
function parsear(texto: string): Evento[] {
  const lineas = texto.replace(/\r?\n[ \t]/g, "").split(/\r?\n/);
  const eventos: Evento[] = [];
  let ev: Evento | null = null;
  for (const l of lineas) {
    if (l === "BEGIN:VEVENT") ev = {};
    else if (l === "END:VEVENT") { if (ev) eventos.push(ev); ev = null; }
    else if (ev) {
      const i = l.indexOf(":");
      if (i < 0) continue;
      const cabeza = l.slice(0, i);
      ev[cabeza.split(";")[0].toUpperCase()] = { valor: l.slice(i + 1), params: cabeza };
    }
    if (eventos.length > 2000) break;
  }
  return eventos;
}
const desescapar = (s = "") => s.replace(/\\n/gi, "\n").replace(/\\([,;\\])/g, "$1");
function sinHtml(s: string): string {
  return s.replace(/<(br|\/p|\/li|\/div|\/h\d)[^>]*>/gi, "\n").replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ").replace(/&amp;/g, "&").replace(/&lt;/g, "<").replace(/&gt;/g, ">").replace(/&quot;/g, '"').replace(/&#39;/g, "'")
    .replace(/\n{3,}/g, "\n\n").trim();
}
function fecha(p?: { valor: string; params: string }): string | null {
  const m = p?.valor.match(/^(\d{4})(\d{2})(\d{2})(?:T(\d{2})(\d{2})(\d{2})(Z)?)?/);
  if (!m) return null;
  const [, y, mo, d, h = "23", mi = "59", s = "00", z] = m;
  const utc = Date.UTC(+y, +mo - 1, +d, +h, +mi, +s) + (z ? 0 : 4 * 3600_000); // sin zona: hora de RD (UTC-4)
  return new Date(utc).toISOString();
}
function titulo(s: string): string | null {
  const t = desescapar(s).trim();
  if (/(se abre|abre|opens)\s*$/i.test(t) || /^(abre|opens)\b/i.test(t)) return null; // aperturas no son tareas
  return t.replace(/\s+(está en fecha de entrega|vence|se cierra|cierra|is due|closes|due)\s*$/i, "").slice(0, 200) || null;
}
const horaRD = (iso: string) => new Date(iso).toLocaleString("es-DO", { timeZone: "America/Santo_Domingo", weekday: "short", day: "numeric", month: "short", hour: "numeric", minute: "2-digit" });

// ---------- Notificaciones (FCM para Android, Web Push para navegadores) ----------
let accesoFcm: { token: string; hasta: number } | null = null;
// deno-lint-ignore no-explicit-any
async function tokenFcm(sa: any) {
  if (accesoFcm && accesoFcm.hasta > Date.now()) return accesoFcm.token;
  const ahora = Math.floor(Date.now() / 1000);
  const sinFirmar = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging", aud: "https://oauth2.googleapis.com/token", iat: ahora, exp: ahora + 3600,
  }));
  const der = Uint8Array.from(atob(sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s/g, "")), (c) => c.charCodeAt(0));
  const llave = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const firma = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", llave, new TextEncoder().encode(sinFirmar)));
  const r = await fetch("https://oauth2.googleapis.com/token", { method: "POST", headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${sinFirmar}.${b64url(firma)}` }) });
  const token = (await r.json()).access_token;
  accesoFcm = { token, hasta: Date.now() + 50 * 60_000 };
  return token;
}
async function avisar(usuario: string, titulo: string, texto: string) {
  const { data } = await admin.from("dispositivos").select("token, plataforma").eq("usuario_id", usuario);
  if (!data?.length) return;
  const borrar = (token: string) => admin.from("dispositivos").delete().eq("usuario_id", usuario).eq("token", token);
  for (const d of data.filter((d) => d.plataforma === "web")) {
    const estado = await enviarWeb(admin, d.token, { titulo, texto, tipo: "tareas", url: "/" }, 24 * 3600).catch(() => 0);
    if (estado === 404 || estado === 410) await borrar(d.token);
  }
  const telefonos = data.filter((d) => d.plataforma !== "web");
  const crudo = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
  if (!telefonos.length || !crudo) return;
  const sa = JSON.parse(crudo);
  const acceso = await tokenFcm(sa);
  for (const d of telefonos) {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST", headers: { "content-type": "application/json", authorization: `Bearer ${acceso}` },
      body: JSON.stringify({ message: { token: d.token, notification: { title: titulo, body: texto }, data: { titulo, texto, tipo: "tareas" },
        android: { priority: "high", notification: { channel_id: "novedades" } } } }),
    });
    if (r.status === 404) await borrar(d.token);
  }
}

// ---------- Sincronizar un aula ----------
async function sincronizar(fila: { usuario_id: string; url: string }): Promise<{ nuevas: number; total: number } | string> {
  const fallo = async (msg: string) => {
    await admin.from("aulas_virtuales").update({ estado: "error", ultimo_error: msg, sincronizada: new Date().toISOString() }).eq("usuario_id", fila.usuario_id);
    return msg;
  };
  if (!urlValida(fila.url)) return fallo("Ese enlace no es un calendario del aula virtual");
  let texto: string;
  try {
    const r = await fetch(fila.url, { redirect: "manual", signal: AbortSignal.timeout(15_000), headers: { "user-agent": "MiCarrera/1.0 (calendario)", accept: "text/calendar,*/*" } });
    if (r.status >= 300 && r.status < 400) return fallo("El aula virtual pidió iniciar sesión: genera el enlace otra vez");
    if (!r.ok) return fallo(`El aula virtual respondió ${r.status}`);
    if (Number(r.headers.get("content-length") ?? 0) > MAX_BYTES) return fallo("El calendario es demasiado grande");
    const bytes = new Uint8Array(await r.arrayBuffer());
    if (bytes.length > MAX_BYTES) return fallo("El calendario es demasiado grande");
    texto = new TextDecoder().decode(bytes);
  } catch {
    return fallo("No se pudo conectar con el aula virtual");
  }
  if (!texto.includes("BEGIN:VCALENDAR")) return fallo("Ese enlace no es un calendario");

  const desde = Date.now() - 7 * 86400_000, hasta = Date.now() + 150 * 86400_000;
  const filas = parsear(texto).map((e) => {
    const t = titulo(e.SUMMARY?.valor ?? "");
    const entrega = fecha(e.DTEND) ?? fecha(e.DTSTART);
    if (!t || !entrega || !e.UID) return null;
    const ms = new Date(entrega).getTime();
    if (ms < desde || ms > hasta) return null;
    return {
      usuario_id: fila.usuario_id, origen: "aula", uid_externo: desescapar(e.UID.valor).slice(0, 300), titulo: t,
      materia: e.CATEGORIES ? desescapar(e.CATEGORIES.valor).split(",")[0].slice(0, 120) : null,
      detalle: e.DESCRIPTION ? sinHtml(desescapar(e.DESCRIPTION.valor)).slice(0, 5000) || null : null,
      entrega,
    };
  }).filter((f): f is NonNullable<typeof f> => !!f).slice(0, 300);

  const { data: previas } = await admin.from("tareas").select("uid_externo").eq("usuario_id", fila.usuario_id).eq("origen", "aula");
  const conocidas = new Set((previas ?? []).map((p) => p.uid_externo));
  const primeraVez = conocidas.size === 0;
  if (filas.length) {
    // No mandamos "hecha": si el estudiante ya la marcó, se queda marcada
    const { error } = await admin.from("tareas").upsert(filas, { onConflict: "usuario_id,uid_externo" });
    if (error) { console.error(error.message); return fallo("No se pudieron guardar las tareas"); }
  }
  const nuevas = filas.filter((f) => !conocidas.has(f.uid_externo) && new Date(f.entrega).getTime() > Date.now());
  if (primeraVez && filas.length) {
    await avisar(fila.usuario_id, "🎓 ¡Aula virtual conectada!", `Encontré ${filas.length} tareas y fechas. Las ves en Estudio.`);
  } else {
    for (const n of nuevas.slice(0, 3)) {
      await avisar(fila.usuario_id, `📚 Nueva tarea${n.materia ? ` · ${n.materia}` : ""}`, `${n.titulo} · entrega ${horaRD(n.entrega)}`);
    }
  }
  await admin.from("aulas_virtuales").update({ estado: "ok", ultimo_error: null, sincronizada: new Date().toISOString() }).eq("usuario_id", fila.usuario_id);
  return { nuevas: nuevas.length, total: filas.length };
}

/** Tareas que vencen en las próximas 24 h y no están hechas: un aviso por tarea. */
async function recordatorios() {
  const { data } = await admin.from("tareas").select("id, usuario_id, titulo, materia, entrega")
    .eq("hecha", false).eq("recordatorio_enviado", false)
    .gt("entrega", new Date().toISOString()).lt("entrega", new Date(Date.now() + 24 * 3600_000).toISOString()).limit(300);
  for (const t of data ?? []) {
    await admin.from("tareas").update({ recordatorio_enviado: true }).eq("id", t.id);
    await avisar(t.usuario_id, `⏰ Vence pronto${t.materia ? ` · ${t.materia}` : ""}`, `${t.titulo} · ${horaRD(t.entrega)}`);
  }
  return data?.length ?? 0;
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const claveCron = req.headers.get("x-cron-key");
    if (claveCron) {
      const { data: cfg } = await admin.from("config_servidor").select("valor").eq("clave", "cron").maybeSingle();
      if (!cfg || cfg.valor !== claveCron) return json({ error: "No autorizado" }, 403);
      const limite = new Date(Date.now() - 15 * 60_000).toISOString();
      const { data: aulas } = await admin.from("aulas_virtuales").select("usuario_id, url")
        .or(`sincronizada.is.null,sincronizada.lt.${limite}`).limit(100);
      let ok = 0;
      for (let i = 0; i < (aulas ?? []).length; i += 5) {
        const r = await Promise.all(aulas!.slice(i, i + 5).map((a) => sincronizar(a).catch(() => "error")));
        ok += r.filter((x) => typeof x !== "string").length;
      }
      return json({ aulas: aulas?.length ?? 0, ok, recordatorios: await recordatorios() });
    }

    const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    const { data: u } = await admin.auth.getUser(jwt);
    if (!u?.user) return json({ error: "Inicia sesión" }, 401);
    const { data: aula } = await admin.from("aulas_virtuales").select("usuario_id, url, sincronizada").eq("usuario_id", u.user.id).maybeSingle();
    if (!aula) return json({ error: "Primero conecta tu aula virtual" }, 404);
    if (aula.sincronizada && Date.now() - new Date(aula.sincronizada).getTime() < 60_000) return json({ ok: true, nota: "Ya se actualizó hace menos de un minuto" });
    const r = await sincronizar(aula);
    return typeof r === "string" ? json({ error: r }, 422) : json({ ok: true, ...r });
  } catch (e) {
    console.error(e);
    return json({ error: "No se pudo actualizar el aula virtual" }, 500);
  }
});
