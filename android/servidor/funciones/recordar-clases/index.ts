// Edge Function: avisos de clase para quienes usan MiCarrera desde el navegador (Web Push).
// (La app Android no pasa por aquí: avisa con sus propias alarmas, ver Alertas.kt.)
//
// Usos:
//  - {"llave": true}                     -> sin sesión: devuelve la llave pública VAPID (la web la necesita para suscribirse).
//  - encabezado x-cron-key (pg_cron, cada 5 min) -> avisa las clases que empiezan pronto, con la hora de RD.
//  - {"prueba": true} con sesión         -> manda a TUS navegadores un aviso de ejemplo con tu próxima clase.
//
// verify_jwt=false porque pg_cron no tiene sesión: la autorización la hace esta función.

import { createClient } from "npm:@supabase/supabase-js@2";
import { enviarWeb, huella, llavesVapid } from "../_compartido/webpush.ts";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (c: unknown, status = 200) => new Response(JSON.stringify(c), { status, headers: { ...CORS, "content-type": "application/json" } });

const DIAS = ["lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"];

/** Fecha, día (1 = lunes … 7 = domingo) y minuto del día en República Dominicana. */
function ahoraRD(d = new Date()) {
  const p = Object.fromEntries(new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Santo_Domingo", year: "numeric", month: "2-digit", day: "2-digit",
    hour: "2-digit", minute: "2-digit", hourCycle: "h23", weekday: "short",
  }).formatToParts(d).map((x) => [x.type, x.value]));
  const dia = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].indexOf(p.weekday) + 1;
  return { fecha: `${p.year}-${p.month}-${p.day}`, dia, minuto: Number(p.hour) * 60 + Number(p.minute) };
}

const minutos = (h: string) => { const [a, b] = h.split(":").map(Number); return a * 60 + (b || 0); };
function hora12(h: string) {
  const m = minutos(h), hh = Math.floor(m / 60), mm = m % 60;
  return `${hh % 12 || 12}:${String(mm).padStart(2, "0")} ${hh < 12 ? "a. m." : "p. m."}`;
}
const mismaMateria = (a?: string | null, b?: string | null) => !!a && !!b && a.trim().toLowerCase() === b.trim().toLowerCase();

type Clase = { id: string; usuario_id: string; materia: string; dia: number; hora_inicio: string; aula: string | null };
type Pendiente = { usuario_id: string; materia: string | null; titulo: string };

function aviso(c: Clase, falta: number, pendientes: Pendiente[]) {
  const suyas = pendientes.filter((t) => t.usuario_id === c.usuario_id && mismaMateria(t.materia, c.materia));
  const extra = suyas.length === 1 ? ` · Pendiente: ${suyas[0].titulo}` : suyas.length > 1 ? ` · ${suyas.length} pendientes de esta materia` : "";
  return {
    titulo: `📚 ${c.materia} en ${falta} min`,
    texto: `${hora12(c.hora_inicio)}${c.aula ? ` · Aula ${c.aula}` : ""}${extra}`,
    tipo: "clases", tag: `clase-${c.id}`, url: "/",
  };
}

async function pendientesDe(usuarios: string[]): Promise<Pendiente[]> {
  if (!usuarios.length) return [];
  const { data } = await admin.from("tareas").select("usuario_id, materia, titulo")
    .in("usuario_id", usuarios).eq("hecha", false).not("materia", "is", null)
    .or(`entrega.is.null,entrega.gt.${new Date().toISOString()}`).limit(500);
  return data ?? [];
}

/** pg_cron: clases de hoy que empiezan dentro de los minutos que eligió cada navegador. */
async function recordar() {
  const { fecha, dia, minuto } = ahoraRD();
  const { data: navegadores } = await admin.from("dispositivos").select("usuario_id, token, minutos_clase")
    .eq("plataforma", "web").not("minutos_clase", "is", null);
  if (!navegadores?.length) return { enviados: 0 };

  const usuarios = [...new Set(navegadores.map((n) => n.usuario_id))];
  const { data: clases } = await admin.from("horario").select("id, usuario_id, materia, dia, hora_inicio, aula")
    .eq("dia", dia).in("usuario_id", usuarios);
  const proximas = (clases ?? []) as Clase[];
  if (!proximas.length) return { enviados: 0 };
  const pendientes = await pendientesDe(usuarios);

  let enviados = 0, borrados = 0;
  for (const n of navegadores) {
    const id = await huella(n.token);
    for (const c of proximas.filter((c) => c.usuario_id === n.usuario_id)) {
      const falta = minutos(c.hora_inicio) - minuto;
      if (falta <= 0 || falta > n.minutos_clase) continue;
      // Si ya se avisó esta clase hoy en este navegador, el insert choca con la llave primaria y no se repite.
      const { error: repetido } = await admin.from("avisos_clase").insert({ horario_id: c.id, fecha, dispositivo: id });
      if (repetido) continue;
      const estado = await enviarWeb(admin, n.token, aviso(c, falta, pendientes), falta * 60).catch(() => 0);
      if (estado === 404 || estado === 410) {
        await admin.from("dispositivos").delete().eq("usuario_id", n.usuario_id).eq("token", n.token);
        borrados++;
        break;
      }
      if (estado >= 200 && estado < 300) enviados++;
    }
  }
  // Limpieza: lo de hace más de una semana ya no hace falta.
  const semana = new Date(Date.now() - 8 * 86400_000).toISOString().slice(0, 10);
  await admin.from("avisos_clase").delete().lt("fecha", semana);
  return { enviados, borrados };
}

/** Con sesión: un aviso de ejemplo a los navegadores de quien lo pide, con su próxima clase. */
async function prueba(usuario: string) {
  const { data: navegadores } = await admin.from("dispositivos").select("token").eq("usuario_id", usuario).eq("plataforma", "web");
  if (!navegadores?.length) return json({ error: "Primero activa los avisos en este navegador" }, 404);

  const { dia, minuto } = ahoraRD();
  const { data: clases } = await admin.from("horario").select("id, usuario_id, materia, dia, hora_inicio, aula").eq("usuario_id", usuario);
  // La próxima clase de la semana (hoy más tarde, o el siguiente día con clases).
  let siguiente: { c: Clase; cuando: string } | null = null;
  for (let i = 0; i <= 7 && !siguiente; i++) {
    const d = ((dia - 1 + i) % 7) + 1;
    const c = ((clases ?? []) as Clase[]).filter((x) => x.dia === d && (i > 0 || minutos(x.hora_inicio) > minuto))
      .sort((a, b) => minutos(a.hora_inicio) - minutos(b.hora_inicio))[0];
    if (c) siguiente = { c, cuando: i === 0 ? "hoy" : i === 1 ? "mañana" : `el ${DIAS[d - 1]}` };
  }
  const contenido = siguiente
    ? { titulo: "🔔 ¡Avisos activados!", texto: `Tu próxima clase: ${siguiente.c.materia}, ${siguiente.cuando} a las ${hora12(siguiente.c.hora_inicio)}. Te aviso antes de que empiece.`, tipo: "clases", url: "/" }
    : { titulo: "🔔 ¡Avisos activados!", texto: "Agrega tu horario y te aviso antes de cada clase.", tipo: "clases", url: "/" };

  let enviados = 0;
  for (const n of navegadores) {
    const estado = await enviarWeb(admin, n.token, contenido).catch(() => 0);
    if (estado === 404 || estado === 410) await admin.from("dispositivos").delete().eq("usuario_id", usuario).eq("token", n.token);
    else if (estado >= 200 && estado < 300) enviados++;
  }
  return json({ enviados });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const claveCron = req.headers.get("x-cron-key");
    if (claveCron) {
      const { data: cfg } = await admin.from("config_servidor").select("valor").eq("clave", "cron").maybeSingle();
      if (!cfg || cfg.valor !== claveCron) return json({ error: "No autorizado" }, 403);
      return json(await recordar());
    }

    const cuerpo = await req.json().catch(() => ({}));
    if (cuerpo.llave) return json({ llave: (await llavesVapid(admin)).publica });

    const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
    const { data: u } = await admin.auth.getUser(jwt);
    if (!u?.user) return json({ error: "Inicia sesión" }, 401);
    if (cuerpo.prueba) return await prueba(u.user.id);
    return json({ error: "No autorizado" }, 403);
  } catch (e) {
    console.error(e);
    return json({ error: "No se pudo enviar el aviso" }, 500);
  }
});
