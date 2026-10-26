import { useEffect, useState, type ReactNode } from 'react'
import * as repo from '../repo'
import type { Clase, Interaccion, Logro } from '../tipos'
import { Cargando, Etiqueta, Icono, Modal } from '../comunes'
import { compartirInvitacion } from '../invitar'
import { DIAS, hora12 } from '../util'
import {
  FORMATO_USUARIO, OPCIONES_BUSCABLE, OPCIONES_HORARIO, clasesEnComun, estadoAhora, horasLibresEnComun, iniciales,
  limpiarUsuario, mismaClase, nombreVisible, type Persona,
} from '../amigos'

export function Avatar({ p, tam = 44 }: { p: Persona; tam?: number }) {
  return (
    <span style={{ width: tam, height: tam, borderRadius: '50%', background: 'var(--acento)', color: '#fff', flex: 'none',
      display: 'grid', placeItems: 'center', fontWeight: 700, fontSize: tam * 0.38 }}>{iniciales(p)}</span>
  )
}

function Encabezado({ titulo, subtitulo, onVolver }: { titulo: string; subtitulo?: string; onVolver: () => void }) {
  return (
    <div className="titulo-pantalla" style={{ paddingLeft: 8 }}>
      <button className="boton-texto" style={{ color: 'var(--texto)' }} onClick={onVolver} aria-label="Volver"><Icono n="atras" /></button>
      <div style={{ flex: 1 }}>
        <h1 style={{ margin: 0, fontSize: 26 }}>{titulo}</h1>
        {subtitulo && <div className="suave" style={{ fontSize: 14 }}>{subtitulo}</div>}
      </div>
    </div>
  )
}

/** Sección Amigos: tu @usuario, buscar, solicitudes, tus amigos y "Quizás conozcas". */
export default function Amigos({ onCerrar }: { onCerrar: () => void }) {
  const [cargando, setCargando] = useState(true)
  const [miUsuario, setMiUsuario] = useState<string | null>(null)
  const [gente, setGente] = useState<Persona[]>([])
  const [sug, setSug] = useState<Persona[]>([])
  const [busqueda, setBusqueda] = useState('')
  const [resultados, setResultados] = useState<Persona[]>([])
  const [enviadas, setEnviadas] = useState<Set<string>>(new Set())
  const [abierto, setAbierto] = useState<Persona | null>(null)
  const [creandoUsuario, setCreandoUsuario] = useState(false)
  const [mensaje, setMensaje] = useState<string | null>(null)
  const [miId, setMiId] = useState<string | null>(null)
  const [horarios, setHorarios] = useState<Record<string, Clase[]>>({})
  const [logros, setLogros] = useState<Logro[]>([])
  const [inters, setInters] = useState<Interaccion[]>([])
  const [chocados, setChocados] = useState<Set<string>>(new Set())
  const [ahora, setAhora] = useState(() => new Date())

  const recargar = () => Promise.allSettled([
    repo.perfil().then((p) => { setMiUsuario(p?.usuario ?? null); setMiId(p?.id ?? null) }),
    repo.misAmigos().then((g) => {
      setGente(g)
      return repo.horariosDe(g.filter((p) => p.estado === 'aceptada').map((p) => p.id)).then(setHorarios)
    }),
    repo.sugerencias().then(setSug),
    repo.logrosAmigos().then(setLogros),
    repo.interacciones().then(setInters),
  ]).finally(() => setCargando(false))
  // "Libres ahora" se actualiza solo cada minuto
  useEffect(() => { const t = setInterval(() => setAhora(new Date()), 60_000); return () => clearInterval(t) }, [])
  useEffect(() => { recargar() }, [])

  useEffect(() => {
    const q = limpiarUsuario(busqueda)
    if (q.length < 2) { setResultados([]); return }
    const t = setTimeout(() => repo.buscarUsuarios(q).then(setResultados).catch(() => setResultados([])), 350)
    return () => clearTimeout(t)
  }, [busqueda])

  async function accion(texto: string, hacer: () => Promise<unknown>) {
    try { await hacer(); setMensaje(texto) } catch { setMensaje('No se pudo completar. Intenta otra vez.') }
    recargar()
  }
  const agregar = (p: Persona) => {
    setEnviadas((s) => new Set(s).add(p.id))
    accion(`Solicitud enviada a ${nombreVisible(p)}.`, () => repo.enviarSolicitud(p.id))
  }

  function chocar(l: Logro) {
    const clave = l.amigo + l.materia
    setChocados((s) => new Set(s).add(clave))
    accion(`🙌 Le chocaste los 5 a ${l.nombre?.trim() || '@' + (l.usuario ?? 'tu amigo')}.`, () => repo.chocarLos5(l.amigo, l.materia))
  }
  function responder(i: Interaccion, si: boolean) {
    setInters((xs) => xs.map((x) => (x.id === i.id ? { ...x, respuesta: si ? 'si' : 'no' } : x)))
    accion(si ? '¡Genial! Le avisamos que vas.' : 'Le avisamos que esta vez no puedes.', () => repo.responderInvitacion(i.id, si))
  }

  if (abierto) return (
    <AmigoDetalle p={abierto} onVolver={() => setAbierto(null)}
      onInvitado={(n) => { setMensaje(`📚 Invitación enviada a ${n}.`); setAbierto(null); recargar() }}
      onQuitar={() => { accion('Ya no son amigos.', () => repo.quitarAmistad(abierto.amistad!)); setAbierto(null) }}
      onBloquear={() => { accion(`Bloqueaste a ${nombreVisible(abierto)}.`, () => repo.bloquear(abierto.id)); setAbierto(null) }} />
  )

  const recibidas = gente.filter((p) => p.estado === 'pendiente' && !p.enviada_por_mi)
  const amigos = gente.filter((p) => p.estado === 'aceptada')
  const pendientes = gente.filter((p) => p.estado === 'pendiente' && p.enviada_por_mi)
  const relacionados = new Set(gente.map((p) => p.id))
  const sugeridos = sug.filter((p) => !relacionados.has(p.id))
  const porId = new Map(gente.map((p) => [p.id, p]))
  const nombreDe = (id: string) => { const p = porId.get(id); return p ? nombreVisible(p) : 'Un amigo' }
  // ¿Quién está libre ahora? Solo amigos que comparten su horario
  const conHorario = amigos.filter((p) => (horarios[p.id] ?? []).length > 0)
  const estados = conHorario.map((p) => ({ p, e: estadoAhora(horarios[p.id], ahora) }))
  const libres = estados.filter((x) => x.e.libre)
  const enClase = estados.filter((x) => !x.e.libre)
  const hace7 = Date.now() - 7 * 864e5
  const recibidasInter = inters.filter((i) => i.para === miId && new Date(i.creada).getTime() > hace7)
  const invitacionesNuevas = recibidasInter.filter((i) => i.tipo === 'invitacion' && !i.respuesta)
  const otrasNovedades = [
    ...recibidasInter.filter((i) => i.tipo === 'choca5'),
    ...inters.filter((i) => i.de === miId && i.tipo === 'invitacion' && i.respuesta && new Date(i.creada).getTime() > hace7),
  ].sort((a, b) => b.creada.localeCompare(a.creada)).slice(0, 8)

  return (
    <>
      <Encabezado titulo="Amigos" subtitulo="Conecta con quien comparte tus clases" onVolver={onCerrar} />
      {cargando ? <Cargando /> : (
        <div className="contenido" style={{ gap: 12 }}>
          <button className="tarjeta clic" onClick={() => setCreandoUsuario(true)}
            style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 16, textAlign: 'left' }}>
            <span style={{ flex: 1 }}>
              <span className="suave" style={{ display: 'block', fontSize: 13 }}>Tu usuario</span>
              <strong style={{ fontSize: 17, color: miUsuario ? 'var(--texto)' : 'var(--acento)' }}>
                {miUsuario ? `@${miUsuario}` : 'Crea tu @usuario para que te encuentren'}
              </strong>
            </span>
            <span style={{ color: 'var(--acento)', fontWeight: 600 }}>{miUsuario ? 'Cambiar' : 'Crear'}</span>
          </button>

          <button className="tarjeta clic" onClick={async () => {
            const r = await compartirInvitacion(miUsuario)
            if (r === 'copiado') setMensaje('Mensaje de invitación copiado. Pégalo en WhatsApp o donde quieras.')
          }} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 16, textAlign: 'left',
            borderColor: 'color-mix(in srgb, var(--acento) 50%, transparent)', background: 'color-mix(in srgb, var(--acento-suave) 35%, var(--tarjeta))' }}>
            <span style={{ fontSize: 24 }}>💌</span>
            <span style={{ flex: 1 }}>
              <strong style={{ display: 'block', color: 'var(--acento)' }}>Invitar amigos</strong>
              <span className="suave" style={{ fontSize: 13 }}>
                {miUsuario ? 'Comparte tu enlace: al registrarse, te agregan como amigo con un toque.' : 'Comparte MiCarrera. Crea tu @usuario para que te agreguen al registrarse.'}
              </span>
            </span>
          </button>

          <div style={{ position: 'relative' }}>
            <span style={{ position: 'absolute', left: 14, top: 15 }} className="suave"><Icono n="persona" /></span>
            <input className="campo" style={{ paddingLeft: 44 }} placeholder="Buscar por nombre o @usuario…" value={busqueda}
              onChange={(e) => setBusqueda(e.target.value)} autoCapitalize="none" autoCorrect="off" />
          </div>
          {mensaje && <div style={{ color: 'var(--verde)', fontSize: 14, marginLeft: 4 }}>{mensaje}</div>}

          {busqueda.trim() && (
            <Grupo titulo="Resultados">
              {resultados.length === 0 ? (
                <p className="suave" style={{ margin: 16, fontSize: 14 }}>
                  {limpiarUsuario(busqueda).length < 2 ? 'Escribe al menos 2 letras.' : 'No encontramos a nadie con ese usuario.'}
                </p>
              ) : resultados.map((p) => (
                <FilaPersona key={p.id} p={p} detalle={[p.universidad, p.carrera].filter(Boolean).join(' · ')}>
                  {relacionados.has(p.id) || enviadas.has(p.id)
                    ? <span className="suave" style={{ fontSize: 13 }}>Enviada</span>
                    : <button className="boton" style={{ height: 38, borderRadius: 12 }} onClick={() => agregar(p)}>Agregar</button>}
                </FilaPersona>
              ))}
            </Grupo>
          )}

          {recibidas.length > 0 && (
            <Grupo titulo={`Solicitudes (${recibidas.length})`} resaltado>
              {recibidas.map((p) => (
                <FilaPersona key={p.id} p={p} detalle="quiere ser tu amigo">
                  <button className="boton-texto" style={{ color: 'var(--texto-suave)' }}
                    onClick={() => accion('Solicitud rechazada.', () => repo.quitarAmistad(p.amistad!))}>Rechazar</button>
                  <button className="boton" style={{ height: 38, borderRadius: 12 }}
                    onClick={() => accion(`¡Ahora eres amigo de ${nombreVisible(p)}!`, () => repo.aceptarSolicitud(p.amistad!))}>Aceptar</button>
                </FilaPersona>
              ))}
            </Grupo>
          )}

          {invitacionesNuevas.length > 0 && (
            <Grupo titulo="Te invitaron a estudiar 📚" resaltado>
              {invitacionesNuevas.map((i) => (
                <div key={i.id} style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 8 }}>
                  <span><strong>{nombreDe(i.de)}</strong> te invita a estudiar{i.materia ? <> <strong>{i.materia}</strong></> : ''}
                    {i.cuando && <span className="suave"> · {cuandoTexto(i.cuando)}</span>}</span>
                  {i.mensaje && <span className="suave" style={{ fontSize: 14 }}>“{i.mensaje}”</span>}
                  <span style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
                    <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => responder(i, false)}>No puedo</button>
                    <button className="boton" style={{ height: 38, borderRadius: 12 }} onClick={() => responder(i, true)}>¡Voy! 🙌</button>
                  </span>
                </div>
              ))}
            </Grupo>
          )}

          {conHorario.length > 0 && (
            <Grupo titulo="¿Quién está libre ahora?">
              <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 10 }}>
                {libres.length === 0 ? <span className="suave" style={{ fontSize: 14 }}>Todos tus amigos están en clase ahora mismo 📖</span> : (
                  <div style={{ display: 'flex', gap: 14, overflowX: 'auto', paddingBottom: 2 }}>
                    {libres.map(({ p, e }) => (
                      <button key={p.id} className="boton-texto" onClick={() => setAbierto(p)}
                        style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 4, width: 76, flex: 'none', padding: 0, color: 'var(--texto)' }}>
                        <span style={{ position: 'relative' }}>
                          <Avatar p={p} tam={52} />
                          <span style={{ position: 'absolute', right: 1, bottom: 1, width: 14, height: 14, borderRadius: '50%', background: 'var(--verde)', border: '2px solid var(--tarjeta)' }} />
                        </span>
                        <span style={{ fontSize: 12, fontWeight: 600, maxWidth: 76, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{nombreVisible(p).split(' ')[0]}</span>
                        <span className="suave" style={{ fontSize: 11 }}>{e.libre && e.proxima ? `hasta ${hora12(e.proxima.hora_inicio)}` : 'libre hoy'}</span>
                      </button>
                    ))}
                  </div>
                )}
                {enClase.length > 0 && (
                  <span className="suave" style={{ fontSize: 13 }}>
                    En clase: {enClase.map(({ p, e }) => `${nombreVisible(p).split(' ')[0]}${!e.libre ? ` (hasta ${hora12(e.clase.hora_fin)})` : ''}`).join(', ')}
                  </span>
                )}
              </div>
            </Grupo>
          )}

          {(logros.length > 0 || otrasNovedades.length > 0) && (
            <Grupo titulo="Novedades de tus amigos">
              {otrasNovedades.map((i) => (
                <div key={i.id} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14 }}>
                  <span style={{ fontSize: 26 }}>{i.tipo === 'choca5' ? '🙌' : i.respuesta === 'si' ? '✅' : '🙁'}</span>
                  <span style={{ flex: 1, fontSize: 14 }}>
                    {i.tipo === 'choca5'
                      ? <><strong>{nombreDe(i.de)}</strong> te chocó los 5{i.materia ? <> por aprobar <strong>{i.materia}</strong></> : ''}</>
                      : <><strong>{nombreDe(i.para)}</strong> {i.respuesta === 'si' ? 'va a estudiar' : 'esta vez no puede estudiar'}{i.materia ? <> {i.materia}</> : ''}{i.respuesta === 'si' ? ' contigo 🙌' : ''}</>}
                  </span>
                </div>
              ))}
              {logros.map((l) => {
                const ya = l.ya_chocaste || chocados.has(l.amigo + l.materia)
                return (
                  <div key={l.amigo + l.materia} style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14 }}>
                    <span style={{ fontSize: 26 }}>🎉</span>
                    <span style={{ flex: 1, fontSize: 14 }}>
                      <strong>{l.nombre?.trim() || (l.usuario ? '@' + l.usuario : 'Tu amigo')}</strong> aprobó <strong>{l.materia}</strong>
                    </span>
                    <button className={ya ? 'pastilla chica' : 'boton'} style={ya ? { opacity: .7 } : { height: 36, borderRadius: 12, padding: '0 12px' }}
                      disabled={ya} onClick={() => chocar(l)}>{ya ? '🙌 Listo' : '🙌 Chocar los 5'}</button>
                  </div>
                )
              })}
            </Grupo>
          )}

          <Grupo titulo="Tus amigos">
            {amigos.length === 0 ? (
              <div style={{ padding: 24, textAlign: 'center' }}>
                <div style={{ fontSize: 36 }}>👥</div>
                <strong style={{ fontSize: 17 }}>Aún no tienes amigos</strong>
                <p className="suave" style={{ margin: '4px 0 0', fontSize: 14 }}>
                  Agrega compañeros para ver con quién coincides en clase y a qué horas están libres.
                </p>
              </div>
            ) : amigos.map((p) => (
              <FilaPersona key={p.id} p={p} onClick={() => setAbierto(p)}
                detalle={[p.carrera, p.avance != null ? `${p.avance} % de avance` : null].filter(Boolean).join(' · ')}>
                <Icono n="derecha" color="var(--texto-suave)" />
              </FilaPersona>
            ))}
          </Grupo>

          {pendientes.length > 0 && (
            <Grupo titulo="Enviadas">
              {pendientes.map((p) => (
                <FilaPersona key={p.id} p={p} detalle="esperando respuesta">
                  <button className="boton-texto" style={{ color: 'var(--texto-suave)' }}
                    onClick={() => accion('Solicitud cancelada.', () => repo.quitarAmistad(p.amistad!))}>Cancelar</button>
                </FilaPersona>
              ))}
            </Grupo>
          )}

          {sugeridos.length > 0 && (
            <Grupo titulo="Quizás conozcas">
              {sugeridos.map((p) => (
                <FilaPersona key={p.id} p={p} detalle={p.en_comun
                  ? `${p.en_comun} ${p.en_comun === 1 ? 'materia' : 'materias'} en común`
                  : [p.universidad, p.carrera].filter(Boolean).join(' · ')}>
                  {enviadas.has(p.id)
                    ? <span className="suave" style={{ fontSize: 13 }}>Enviada</span>
                    : <button className="boton" style={{ height: 38, borderRadius: 12 }} onClick={() => agregar(p)}>Agregar</button>}
                </FilaPersona>
              ))}
            </Grupo>
          )}
        </div>
      )}

      {creandoUsuario && (
        <DialogoUsuario actual={miUsuario} onCerrar={(nuevo) => {
          setCreandoUsuario(false)
          if (nuevo) { setMiUsuario(nuevo); setMensaje(`Listo, ahora eres @${nuevo}.`) }
        }} />
      )}
    </>
  )
}

function Grupo({ titulo, resaltado, children }: { titulo: string; resaltado?: boolean; children: ReactNode }) {
  return (
    <>
      <div className="seccion">{titulo}</div>
      <div className="tarjeta lista-divisores" style={resaltado ? { borderColor: 'color-mix(in srgb, var(--acento) 50%, transparent)' } : undefined}>
        {children}
      </div>
    </>
  )
}

function FilaPersona({ p, detalle, onClick, children }: { p: Persona; detalle: string; onClick?: () => void; children?: ReactNode }) {
  return (
    <div onClick={onClick} role={onClick ? 'button' : undefined} tabIndex={onClick ? 0 : undefined}
      style={{ display: 'flex', alignItems: 'center', gap: 12, padding: 14, cursor: onClick ? 'pointer' : undefined }}>
      <Avatar p={p} />
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: 600, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
          {nombreVisible(p)}{p.nombre && p.usuario && <span className="suave" style={{ fontWeight: 400, fontSize: 13 }}>  @{p.usuario}</span>}
        </span>
        {detalle && <span className="suave" style={{ display: 'block', fontSize: 13, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{detalle}</span>}
      </span>
      <span style={{ display: 'flex', alignItems: 'center', gap: 6 }} onClick={(e) => e.stopPropagation()}>{children}</span>
    </div>
  )
}

/** Ficha de un amigo: clases en común, horas libres en común y su horario (si lo comparte). */
function AmigoDetalle({ p, onVolver, onQuitar, onBloquear, onInvitado }: {
  p: Persona; onVolver: () => void; onQuitar: () => void; onBloquear: () => void; onInvitado: (nombre: string) => void
}) {
  const [mias, setMias] = useState<Clase[]>([])
  const [suyas, setSuyas] = useState<Clase[] | null>(null)
  const [confirmar, setConfirmar] = useState<'quitar' | 'bloquear' | null>(null)
  const [invitando, setInvitando] = useState(false)

  useEffect(() => {
    repo.horario().then(setMias).catch(() => {})
    repo.horariosDe([p.id]).then((r) => setSuyas(r[p.id] ?? [])).catch(() => setSuyas([]))
  }, [p.id])

  const comunes = suyas ? clasesEnComun(mias, suyas) : []
  const libres = suyas ? horasLibresEnComun(mias, suyas) : []
  const porDia = new Map<number, typeof libres>()
  for (const h of libres) porDia.set(h.dia, [...(porDia.get(h.dia) ?? []), h])

  return (
    <>
      <Encabezado titulo={nombreVisible(p)} subtitulo={p.usuario ? `@${p.usuario}` : undefined} onVolver={onVolver} />
      {!suyas ? <Cargando /> : (
        <div className="contenido" style={{ gap: 12 }}>
          <div className="tarjeta" style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16 }}>
            <Avatar p={p} tam={60} />
            <span>
              <strong style={{ display: 'block' }}>{[p.universidad, p.carrera].filter(Boolean).join(' · ') || 'Estudiante'}</strong>
              {p.avance != null && <span className="suave" style={{ fontSize: 13 }}>{p.avance} % de su carrera aprobada</span>}
            </span>
          </div>

          <button className="boton" style={{ alignSelf: 'stretch' }} onClick={() => setInvitando(true)}>📚 Invitar a estudiar</button>

          <div className="rejilla">
            <div>
              <Grupo titulo="Clases en común">
                {suyas.length === 0 ? <p className="suave" style={{ margin: 16, fontSize: 14 }}>{nombreVisible(p)} no comparte su horario, o todavía no lo tiene.</p>
                  : comunes.length === 0 ? <p className="suave" style={{ margin: 16, fontSize: 14 }}>No tienen clases juntos este período.</p>
                  : comunes.map((c, i) => <FilaClase key={i} c={c} />)}
              </Grupo>
            </div>

            {suyas.length > 0 && libres.length > 0 && (
              <div>
                <Grupo titulo="Libres los dos">
                  <div style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 6 }}>
                    {[...porDia.entries()].map(([dia, huecos]) => (
                      <div key={dia} style={{ display: 'flex', gap: 8, fontSize: 14 }}>
                        <strong style={{ width: 92, flex: 'none' }}>{DIAS[dia - 1]}</strong>
                        <span className="suave">{huecos.map((h) => `${hora12(h.desde)} – ${hora12(h.hasta)}`).join('  ·  ')}</span>
                      </div>
                    ))}
                    <span className="suave" style={{ fontSize: 12 }}>Para reunirse o estudiar juntos (entre 7 am y 10 pm).</span>
                  </div>
                </Grupo>
              </div>
            )}

            {suyas.length > 0 && (
              <div>
                <Grupo titulo="Su horario">
                  {[...suyas].sort((a, b) => a.dia - b.dia || a.hora_inicio.localeCompare(b.hora_inicio)).map((c, i) => (
                    <FilaClase key={i} c={c} juntos={comunes.some((m) => mismaClase(m, c))} />
                  ))}
                </Grupo>
              </div>
            )}
          </div>

          <div style={{ display: 'flex', justifyContent: 'space-evenly', marginTop: 8 }}>
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setConfirmar('quitar')}>Eliminar amigo</button>
            <button className="boton-texto" style={{ color: 'var(--rojo)' }} onClick={() => setConfirmar('bloquear')}>Bloquear</button>
          </div>
        </div>
      )}

      {confirmar && (
        <Modal onCerrar={() => setConfirmar(null)}>
          <h2>{confirmar === 'quitar' ? `¿Eliminar a ${nombreVisible(p)}?` : `¿Bloquear a ${nombreVisible(p)}?`}</h2>
          <p className="suave" style={{ margin: 0 }}>
            {confirmar === 'quitar' ? 'Dejarán de ver sus horarios. Pueden volver a agregarse cuando quieran.'
              : 'No podrá encontrarte, verte en sugerencias ni enviarte solicitudes. No se le avisa.'}
          </p>
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setConfirmar(null)}>Cancelar</button>
            <button className="boton" style={confirmar === 'bloquear' ? { background: 'var(--rojo)' } : undefined}
              onClick={() => { const q = confirmar; setConfirmar(null); if (q === 'quitar') onQuitar(); else onBloquear() }}>
              {confirmar === 'quitar' ? 'Eliminar' : 'Bloquear'}
            </button>
          </div>
        </Modal>
      )}
      {invitando && (
        <DialogoInvitar p={p} materias={[...new Set(comunes.map((c) => c.materia))]} huecos={libres}
          onCerrar={(enviada) => { setInvitando(false); if (enviada) onInvitado(nombreVisible(p)) }} />
      )}
    </>
  )
}

/** "mañana 3:00 pm", "vie 25 · 10:00 am"… */
function cuandoTexto(iso: string): string {
  const d = new Date(iso), hoy = new Date()
  const dias = Math.round((new Date(d.toDateString()).getTime() - new Date(hoy.toDateString()).getTime()) / 864e5)
  const h = hora12(`${d.getHours()}:${d.getMinutes()}`)
  if (dias === 0) return `hoy ${h}`
  if (dias === 1) return `mañana ${h}`
  return `${d.toLocaleDateString('es-DO', { weekday: 'short', day: 'numeric', month: 'short' })} · ${h}`
}

/** Próxima fecha (desde ahora) de un hueco libre en común, en formato de <input type="datetime-local">. */
function proximoHueco(huecos: { dia: number; desde: string }[]): string {
  const ahora = new Date()
  for (let k = 0; k < 8; k++) {
    const d = new Date(ahora); d.setDate(d.getDate() + k)
    const dia = ((d.getDay() + 6) % 7) + 1
    for (const h of huecos.filter((x) => x.dia === dia)) {
      const [hh, mm] = h.desde.split(':').map(Number)
      d.setHours(hh, mm, 0, 0)
      if (d > ahora) return aLocal(d)
    }
  }
  const d = new Date(ahora.getTime() + 2 * 3600e3); d.setMinutes(0, 0, 0)
  return aLocal(d)
}
const aLocal = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}T${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`

function DialogoInvitar({ p, materias, huecos, onCerrar }: {
  p: Persona; materias: string[]; huecos: { dia: number; desde: string }[]; onCerrar: (enviada: boolean) => void
}) {
  const [materia, setMateria] = useState(materias[0] ?? '')
  const [cuando, setCuando] = useState(() => proximoHueco(huecos))
  const [mensaje, setMensaje] = useState('')
  const [enviando, setEnviando] = useState(false)
  const [error, setError] = useState<string | null>(null)

  async function enviar() {
    setEnviando(true); setError(null)
    try {
      const fecha = cuando ? new Date(cuando) : null
      await repo.invitarAEstudiar(p.id, materia.trim() || null, fecha && !isNaN(fecha.getTime()) ? fecha.toISOString() : null, mensaje.trim() || null)
      onCerrar(true)
    } catch (e) {
      setError(String((e as Error).message ?? '').includes('límite') ? 'Ya enviaste muchas invitaciones hoy.' : 'No se pudo enviar. Intenta otra vez.')
      setEnviando(false)
    }
  }

  return (
    <Modal onCerrar={() => { if (!enviando) onCerrar(false) }}>
      <h2>Invitar a {nombreVisible(p)}</h2>
      <p className="suave" style={{ margin: 0, fontSize: 14 }}>Le llega una notificación y puede responder “¡Voy!” o “No puedo”.</p>
      <label className="suave" style={{ fontSize: 13 }}>¿Qué van a estudiar?</label>
      <input className="campo" list="materias-comunes" value={materia} maxLength={120} placeholder="Ej. Cálculo I" onChange={(e) => setMateria(e.target.value)} />
      <datalist id="materias-comunes">{materias.map((m) => <option key={m} value={m} />)}</datalist>
      <label className="suave" style={{ fontSize: 13 }}>¿Cuándo?{huecos.length > 0 && ' (ya elegimos una hora en que los dos están libres)'}</label>
      <input className="campo" type="datetime-local" value={cuando} onChange={(e) => setCuando(e.target.value)} />
      <textarea className="campo" rows={2} maxLength={200} value={mensaje} placeholder="Mensaje (opcional): ¿en la biblioteca?"
        onChange={(e) => setMensaje(e.target.value)} />
      {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => onCerrar(false)}>Cancelar</button>
        <button className="boton" disabled={enviando} onClick={enviar}>{enviando ? 'Enviando…' : 'Enviar invitación'}</button>
      </div>
    </Modal>
  )
}

function FilaClase({ c, juntos }: { c: Clase; juntos?: boolean }) {
  const detalle = [c.codigo, c.grupo ? `Grupo ${c.grupo}` : null, c.aula ? `Aula ${c.aula}` : null].filter(Boolean).join(' · ')
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, padding: '12px 16px' }}>
      <span style={{ width: 92, flex: 'none' }}>
        <strong style={{ display: 'block', fontSize: 13 }}>{DIAS[c.dia - 1]}</strong>
        <span className="suave" style={{ fontSize: 12 }}>{hora12(c.hora_inicio)}</span>
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: juntos ? 700 : 400 }}>{c.materia}</span>
        {detalle && <span className="suave" style={{ fontSize: 12 }}>{detalle}</span>}
      </span>
      {juntos && <Etiqueta texto="Juntos" color="var(--acento)" fondo="var(--acento-suave)" />}
    </div>
  )
}

/** Crear o cambiar el @usuario, revisando que esté libre mientras escribes. */
export function DialogoUsuario({ actual, onCerrar }: { actual: string | null; onCerrar: (nuevo: string | null) => void }) {
  const [texto, setTexto] = useState(actual ?? '')
  const [disponible, setDisponible] = useState<boolean | null>(null)
  const [guardando, setGuardando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const limpio = limpiarUsuario(texto)
  const formatoOk = FORMATO_USUARIO.test(limpio)

  useEffect(() => {
    setDisponible(null)
    if (!formatoOk || limpio === actual) return
    const t = setTimeout(() => repo.usuarioDisponible(limpio).then(setDisponible).catch(() => {}), 400)
    return () => clearTimeout(t)
  }, [limpio, formatoOk, actual])

  async function guardar() {
    setGuardando(true)
    try { await repo.guardarUsuario(limpio); onCerrar(limpio) } catch { setError('No se pudo guardar. Quizás alguien lo tomó justo ahora.') }
    setGuardando(false)
  }

  return (
    <Modal onCerrar={() => { if (!guardando) onCerrar(null) }}>
      <h2>{actual ? 'Cambiar @usuario' : 'Crea tu @usuario'}</h2>
      <p className="suave" style={{ margin: 0, fontSize: 14 }}>Así tus amigos te encuentran. Solo letras minúsculas, números, punto o guion bajo (3 a 20).</p>
      <div style={{ position: 'relative' }}>
        <span className="suave" style={{ position: 'absolute', left: 16, top: 14 }}>@</span>
        <input className="campo" style={{ paddingLeft: 32 }} value={texto} autoFocus autoCapitalize="none" autoCorrect="off"
          onChange={(e) => setTexto(e.target.value.toLowerCase().replace(/\s/g, ''))} />
      </div>
      {limpio && limpio !== actual && (
        !formatoOk ? <div style={{ color: 'var(--rojo)', fontSize: 13 }}>Entre 3 y 20 caracteres: a-z, 0-9, punto o guion bajo.</div>
          : disponible === true ? <div style={{ color: 'var(--verde)', fontSize: 13 }}>✓ @{limpio} está disponible</div>
          : disponible === false ? <div style={{ color: 'var(--rojo)', fontSize: 13 }}>@{limpio} ya lo tiene otra persona</div>
          : <div className="suave" style={{ fontSize: 13 }}>Revisando…</div>
      )}
      {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => onCerrar(null)}>Cancelar</button>
        <button className="boton" disabled={!formatoOk || disponible !== true || guardando} onClick={guardar}>Guardar</button>
      </div>
    </Modal>
  )
}

/** Elegir una opción de privacidad. */
export function DialogoOpciones({ titulo, opciones, actual, onElegir }: {
  titulo: string; opciones: readonly (readonly [string, string, string])[]; actual: string; onElegir: (v: string | null) => void
}) {
  const [sel, setSel] = useState(actual)
  return (
    <Modal onCerrar={() => onElegir(null)}>
      <h2>{titulo}</h2>
      {opciones.map(([valor, nombre, detalle]) => (
        <label key={valor} style={{ display: 'flex', gap: 12, alignItems: 'flex-start', cursor: 'pointer', padding: '6px 0' }}>
          <input type="radio" name="opcion" checked={sel === valor} onChange={() => setSel(valor)} style={{ marginTop: 4, accentColor: 'var(--acento)' }} />
          <span>
            <strong style={{ display: 'block', fontSize: 15 }}>{nombre}</strong>
            <span className="suave" style={{ fontSize: 13 }}>{detalle}</span>
          </span>
        </label>
      ))}
      <div className="acciones">
        <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => onElegir(null)}>Cancelar</button>
        <button className="boton" onClick={() => onElegir(sel)}>Guardar</button>
      </div>
    </Modal>
  )
}

export { OPCIONES_BUSCABLE, OPCIONES_HORARIO }
