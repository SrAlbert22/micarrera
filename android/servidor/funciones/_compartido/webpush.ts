// Web Push estándar (avisos al navegador: Chrome, Edge, Firefox, Safari/iPhone con la web instalada).
// Sin librerías: firma VAPID (RFC 8292) y cifrado aes128gcm (RFC 8291) con WebCrypto.
//
// Las llaves VAPID se crean UNA vez en el servidor y se guardan en config_servidor (clave "vapid"),
// así la llave privada nunca sale de Supabase. La pública la pide la web al activar los avisos.
//
// Este archivo se sube junto a cada función que manda avisos (recordar-clases, enviar-notificacion, sincronizar-aula).

// deno-lint-ignore no-explicit-any
type Admin = any;

const enc = new TextEncoder();

export function b64url(datos: Uint8Array | string): string {
  const bytes = typeof datos === "string" ? enc.encode(datos) : datos;
  let bin = "";
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function deB64url(s: string): Uint8Array {
  const b = atob(s.replace(/-/g, "+").replace(/_/g, "/") + "===".slice((s.length + 3) % 4));
  return Uint8Array.from(b, (c) => c.charCodeAt(0));
}

const unir = (...partes: Uint8Array[]) => {
  const r = new Uint8Array(partes.reduce((n, p) => n + p.length, 0));
  let i = 0;
  for (const p of partes) { r.set(p, i); i += p.length; }
  return r;
};

async function hmac(llave: Uint8Array, datos: Uint8Array): Promise<Uint8Array> {
  const k = await crypto.subtle.importKey("raw", llave, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return new Uint8Array(await crypto.subtle.sign("HMAC", k, datos));
}

/** sha-256 en hexadecimal (para identificar un navegador sin guardar su token otra vez). */
export async function huella(texto: string): Promise<string> {
  const h = new Uint8Array(await crypto.subtle.digest("SHA-256", enc.encode(texto)));
  return [...h].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// ---------- Llaves VAPID ----------

let vapid: { publica: string; privada: CryptoKey } | null = null;

export async function llavesVapid(admin: Admin): Promise<{ publica: string; privada: CryptoKey }> {
  if (vapid) return vapid;
  let { data } = await admin.from("config_servidor").select("valor").eq("clave", "vapid").maybeSingle();
  if (!data) {
    // Primera vez: las creamos. Si dos llamadas coinciden, gana la primera (ignoreDuplicates) y ambas leen la misma.
    const par = await crypto.subtle.generateKey({ name: "ECDSA", namedCurve: "P-256" }, true, ["sign", "verify"]);
    const publica = b64url(new Uint8Array(await crypto.subtle.exportKey("raw", par.publicKey)));
    const jwk = await crypto.subtle.exportKey("jwk", par.privateKey);
    await admin.from("config_servidor").upsert({ clave: "vapid", valor: JSON.stringify({ publica, jwk }) }, { onConflict: "clave", ignoreDuplicates: true });
    ({ data } = await admin.from("config_servidor").select("valor").eq("clave", "vapid").maybeSingle());
  }
  const { publica, jwk } = JSON.parse(data.valor);
  const privada = await crypto.subtle.importKey("jwk", jwk, { name: "ECDSA", namedCurve: "P-256" }, false, ["sign"]);
  vapid = { publica, privada };
  return vapid;
}

// ---------- Enviar ----------

export interface AvisoWeb {
  titulo: string;
  texto: string;
  tipo?: string; // clases, tareas, amigos, soporte, novedad…
  url?: string;  // a dónde lleva al tocarlo (por defecto, la web)
  tag?: string;  // avisos con el mismo tag se reemplazan en vez de amontonarse
}

/**
 * Manda un aviso a un navegador. `token` es la suscripción guardada en dispositivos (JSON con endpoint y llaves).
 * Devuelve el código HTTP del servicio de avisos: 201 = enviado; 404 o 410 = ese navegador ya no existe (bórralo).
 */
export async function enviarWeb(admin: Admin, token: string, aviso: AvisoWeb, ttlSegundos = 3600): Promise<number> {
  let sub: { endpoint: string; keys: { p256dh: string; auth: string } };
  try { sub = JSON.parse(token); } catch { return 410; }
  const endpoint = new URL(sub.endpoint);
  if (endpoint.protocol !== "https:") return 410;

  // 1) Firma VAPID: le demuestra al servicio de avisos (Google, Apple, Mozilla…) que el aviso viene de MiCarrera.
  const { publica, privada } = await llavesVapid(admin);
  const sinFirmar = b64url(JSON.stringify({ typ: "JWT", alg: "ES256" })) + "." + b64url(JSON.stringify({
    aud: endpoint.origin, exp: Math.floor(Date.now() / 1000) + 12 * 3600, sub: "https://micarrera-beta.vercel.app",
  }));
  const firma = new Uint8Array(await crypto.subtle.sign({ name: "ECDSA", hash: "SHA-256" }, privada, enc.encode(sinFirmar)));
  const jwt = `${sinFirmar}.${b64url(firma)}`;

  // 2) Cifrado: solo ese navegador puede leer el aviso (RFC 8291, aes128gcm).
  const uaPublica = deB64url(sub.keys.p256dh);
  const secretoAuth = deB64url(sub.keys.auth);
  const efimera = await crypto.subtle.generateKey({ name: "ECDH", namedCurve: "P-256" }, true, ["deriveBits"]) as CryptoKeyPair;
  const asPublica = new Uint8Array(await crypto.subtle.exportKey("raw", efimera.publicKey));
  const llaveUa = await crypto.subtle.importKey("raw", uaPublica, { name: "ECDH", namedCurve: "P-256" }, false, []);
  const compartido = new Uint8Array(await crypto.subtle.deriveBits({ name: "ECDH", public: llaveUa }, efimera.privateKey, 256));

  const prkClave = await hmac(secretoAuth, compartido);
  const ikm = await hmac(prkClave, unir(enc.encode("WebPush: info\0"), uaPublica, asPublica, new Uint8Array([1])));
  const sal = crypto.getRandomValues(new Uint8Array(16));
  const prk = await hmac(sal, ikm);
  const cek = (await hmac(prk, unir(enc.encode("Content-Encoding: aes128gcm\0"), new Uint8Array([1])))).slice(0, 16);
  const nonce = (await hmac(prk, unir(enc.encode("Content-Encoding: nonce\0"), new Uint8Array([1])))).slice(0, 12);

  const texto = unir(enc.encode(JSON.stringify(aviso)), new Uint8Array([2])); // 2 = último (y único) bloque
  const llaveAes = await crypto.subtle.importKey("raw", cek, "AES-GCM", false, ["encrypt"]);
  const cifrado = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv: nonce }, llaveAes, texto));
  const cabecera = unir(sal, new Uint8Array([0, 0, 0x10, 0]), new Uint8Array([asPublica.length]), asPublica); // rs = 4096

  const r = await fetch(sub.endpoint, {
    method: "POST",
    headers: {
      authorization: `vapid t=${jwt}, k=${publica}`,
      "content-encoding": "aes128gcm",
      "content-type": "application/octet-stream",
      ttl: String(ttlSegundos),
      urgency: "high",
    },
    body: unir(cabecera, cifrado),
    signal: AbortSignal.timeout(10_000),
  });
  if (r.status >= 400) console.error(`Web Push ${r.status} (${endpoint.host}): ${(await r.text()).slice(0, 200)}`);
  return r.status;
}
