// Edge Function: envía notificaciones push a teléfonos Android (Firebase Cloud Messaging, API HTTP v1)
// y a navegadores (Web Push, ver _compartido/webpush.ts).
//
// Usos (todos con sesión; cada aviso se manda UNA sola vez y solo si quien lo pide tiene derecho):
//  - {"prueba": true}                       -> una prueba a TUS teléfonos y navegadores.
//  - {"amistad": uuid, "evento": "solicitud" | "aceptada"} -> avisa a la otra persona.
//  - {"soporte": uuid}                       -> avisa a Albert (creador) de un comentario o problema nuevo.
//  - {"interaccion": uuid}                   -> chocar los 5 / invitación a estudiar: avisa al amigo.
//  - {"interaccion": uuid, "respuesta": true} -> el amigo respondió la invitación: avisa a quien invitó.
//  - {"titulo", "texto", "usuarios"|"todos"} -> solo con el encabezado x-admin-key = secreto ADMIN_KEY.

import { createClient } from "npm:@supabase/supabase-js@2";
import { b64url, enviarWeb } from "../_compartido/webpush.ts";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);
const CREADOR = "b70f128f-b015-4c0f-be88-d92389340281"; // Albert: recibe los mensajes de soporte

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-admin-key",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

async function tokenDeGoogle(sa: { client_email: string; private_key: string }): Promise<string> {
  const ahora = Math.floor(Date.now() / 1000);
  const sinFirmar = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" })) + "." + b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: ahora, exp: ahora + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const llave = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const firma = new Uint8Array(await crypto.subtle.sign("RSASSA-PKCS1-v1_5", llave, new TextEncoder().encode(sinFirmar)));
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion: `${sinFirmar}.${b64url(firma)}` }),
  });
  if (!r.ok) throw new Error(`Google rechazó la cuenta de servicio: ${await r.text()}`);
  return (await r.json()).access_token;
}

const json = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { ...CORS, "content-type": "application/json" } });

async function usuarioDeLaSesion(req: Request): Promise<string | null> {
  const jwt = (req.headers.get("Authorization") ?? "").replace(/^Bearer\s+/i, "");
  if (!jwt) return null;
  const { data, error } = await admin.auth.getUser(jwt);
  return error || !data.user ? null : data.user.id;
}

async function nombreDe(id: string): Promise<string> {
  const { data } = await admin.from("perfiles").select("nombre, usuario").eq("id", id).maybeSingle();
  return data?.nombre || (data?.usuario ? `@${data.usuario}` : "Alguien");
}

const reciente = (fecha: string, minutos = 10) => Date.now() - new Date(fecha).getTime() < minutos * 60_000;
const horaRD = (iso: string) => new Date(iso).toLocaleString("es-DO", { timeZone: "America/Santo_Domingo", weekday: "short", hour: "numeric", minute: "2-digit" });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });
  try {
    const cuerpo = await req.json().catch(() => ({}));

    let usuarios: string[] | null = null; // null = todos
    let titulo = String(cuerpo.titulo ?? "");
    let texto = String(cuerpo.texto ?? "");
    let tipo = "novedad";
    const esAdmin = !!Deno.env.get("ADMIN_KEY") && req.headers.get("x-admin-key") === Deno.env.get("ADMIN_KEY");

    if (!esAdmin) {
      const yo = await usuarioDeLaSesion(req);
      if (!yo) return json({ error: "Inicia sesión" }, 401);

      if (cuerpo.prueba) {
        usuarios = [yo];
        titulo = "🔔 ¡Los avisos funcionan!";
        texto = "Este aviso te lo mandó el servidor de MiCarrera.";
      } else if (cuerpo.amistad) {
        const { data: a } = await admin.from("amistades").select("de, para, estado").eq("id", cuerpo.amistad).maybeSingle();
        if (!a) return json({ error: "No existe esa solicitud" }, 404);
        if (cuerpo.evento === "solicitud" && a.estado === "pendiente" && a.de === yo) {
          usuarios = [a.para]; titulo = "Nueva solicitud de amistad 👋"; texto = `${await nombreDe(yo)} quiere ser tu amigo en MiCarrera.`;
        } else if (cuerpo.evento === "aceptada" && a.estado === "aceptada" && a.para === yo) {
          usuarios = [a.de]; titulo = "¡Solicitud aceptada! 🎉"; texto = `${await nombreDe(yo)} aceptó tu solicitud. Mira las clases que tienen en común.`;
        } else return json({ error: "No autorizado" }, 403);
        const { error: repetido } = await admin.from("avisos_amistad").insert({ amistad_id: cuerpo.amistad, evento: cuerpo.evento });
        if (repetido) return json({ enviados: 0, nota: "Ya se avisó" });
        tipo = "amigos";
      } else if (cuerpo.soporte) {
        const { data: s } = await admin.from("soporte").select("usuario_id, tipo, mensaje, notificado, creada").eq("id", cuerpo.soporte).maybeSingle();
        if (!s || s.usuario_id !== yo || s.notificado || !reciente(s.creada)) return json({ error: "No autorizado" }, 403);
        await admin.from("soporte").update({ notificado: true }).eq("id", cuerpo.soporte);
        usuarios = [CREADOR];
        titulo = s.tipo === "problema" ? "🐞 Nuevo problema reportado" : s.tipo === "ayuda" ? "🆘 Alguien pide ayuda" : "💬 Nuevo comentario";
        texto = `${await nombreDe(yo)}: ${String(s.mensaje).slice(0, 140)}`;
        tipo = "soporte";
      } else if (cuerpo.interaccion) {
        const { data: i } = await admin.from("interacciones").select("*").eq("id", cuerpo.interaccion).maybeSingle();
        if (!i) return json({ error: "No existe" }, 404);
        if (cuerpo.respuesta) {
          if (i.para !== yo || i.tipo !== "invitacion" || !i.respuesta || i.respuesta_notificada) return json({ error: "No autorizado" }, 403);
          await admin.from("interacciones").update({ respuesta_notificada: true }).eq("id", i.id);
          usuarios = [i.de];
          titulo = i.respuesta === "si" ? "¡Van a estudiar juntos! 📚" : "Invitación respondida";
          texto = i.respuesta === "si"
            ? `${await nombreDe(yo)} aceptó estudiar${i.materia ? ` ${i.materia}` : ""}${i.cuando ? ` el ${horaRD(i.cuando)}` : ""}.`
            : `${await nombreDe(yo)} no puede esta vez${i.materia ? ` (${i.materia})` : ""}.`;
        } else {
          if (i.de !== yo || i.notificada || !reciente(i.creada)) return json({ error: "No autorizado" }, 403);
          await admin.from("interacciones").update({ notificada: true }).eq("id", i.id);
          usuarios = [i.para];
          const quien = await nombreDe(yo);
          if (i.tipo === "choca5") {
            titulo = "🙌 ¡Te chocaron los cinco!";
            texto = `${quien} te felicita por aprobar ${i.materia ?? "una materia"}.`;
          } else {
            titulo = "📚 Invitación a estudiar";
            texto = `${quien}: ¿Estudiamos${i.materia ? ` ${i.materia}` : ""}${i.cuando ? ` el ${horaRD(i.cuando)}` : ""}? ${i.mensaje ?? ""}`.trim();
          }
        }
        tipo = "amigos";
      } else return json({ error: "No autorizado" }, 403);
    } else {
      if (!titulo) return json({ error: "Falta el título" }, 400);
      usuarios = cuerpo.todos ? null : (Array.isArray(cuerpo.usuarios) ? cuerpo.usuarios : []);
    }

    let consulta = admin.from("dispositivos").select("usuario_id, token, plataforma");
    if (usuarios) consulta = consulta.in("usuario_id", usuarios);
    const { data: dispositivos, error } = await consulta;
    if (error) { console.error(error.message); return json({ error: "No se pudo enviar" }, 500); }
    if (!dispositivos?.length) return json({ enviados: 0, nota: "No hay teléfonos ni navegadores registrados" });

    let enviados = 0, fallidos = 0;
    const borrar = (d: { usuario_id: string; token: string }) =>
      admin.from("dispositivos").delete().eq("usuario_id", d.usuario_id).eq("token", d.token);

    // Navegadores (Web Push)
    for (const d of dispositivos.filter((d) => d.plataforma === "web")) {
      const estado = await enviarWeb(admin, d.token, { titulo, texto, tipo }, 24 * 3600).catch(() => 0);
      if (estado >= 200 && estado < 300) { enviados++; continue; }
      fallidos++;
      if (estado === 404 || estado === 410) await borrar(d);
    }

    // Teléfonos (Firebase)
    const telefonos = dispositivos.filter((d) => d.plataforma !== "web");
    if (telefonos.length) {
      const crudo = Deno.env.get("FIREBASE_SERVICE_ACCOUNT");
      if (!crudo) return json({ error: "Falta configurar FIREBASE_SERVICE_ACCOUNT", enviados }, 500);
      const sa = JSON.parse(crudo);
      const acceso = await tokenDeGoogle(sa);
      for (const d of telefonos) {
        const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
          method: "POST",
          headers: { "content-type": "application/json", authorization: `Bearer ${acceso}` },
          body: JSON.stringify({ message: {
            token: d.token, notification: { title: titulo, body: texto }, data: { titulo, texto, tipo },
            android: { priority: "high", notification: { channel_id: "novedades" } },
          } }),
        });
        if (r.ok) { enviados++; continue; }
        fallidos++;
        const err = await r.text();
        if (r.status === 404 || err.includes("UNREGISTERED")) await borrar(d);
      }
    }
    return json({ enviados, fallidos });
  } catch (e) {
    console.error(e);
    return json({ error: "No se pudo enviar" }, 500);
  }
});
