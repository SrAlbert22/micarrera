import { useEffect, useState } from 'react'
import * as repo from '../repo'
import type { CarreraDetalle, Estado, Materia, Perfil } from '../tipos'
import { Icono, LogoUniversidad, Modal, type NombreIcono } from '../comunes'
import { DialogoContrasena } from '../contrasena'
import { DialogoOpciones, DialogoUsuario, OPCIONES_BUSCABLE, OPCIONES_HORARIO } from './Amigos'
import { compartirInvitacion } from '../invitar'
import { OPCIONES_TEMA, guardarTema, leerTema, type Tema } from '../tema'
import { Cinco } from '../cinco'
import { textoAvisos, useAvisos } from '../avisos'
import { DialogoAvisos } from '../avisosWeb'

/** El evento que da Chrome/Edge/Android para ofrecer "Instalar app". */
export interface EventoInstalar extends Event { prompt: () => Promise<void> }

const esIphone = /iphone|ipad|ipod/i.test(navigator.userAgent)
const yaInstalada = () =>
  window.matchMedia('(display-mode: standalone)').matches || (navigator as { standalone?: boolean }).standalone === true

export default function Cuenta({ carreraId, onCambiarCarrera, instalar, onAmigos, onTest, onCinco }: {
  carreraId: string; onCambiarCarrera: () => void; instalar: EventoInstalar | null; onAmigos: () => void; onTest: () => void; onCinco: () => void
}) {
  const [perfil, setPerfil] = useState<Perfil | null>(null)
  const [carrera, setCarrera] = useState<CarreraDetalle | null>(null)
  const [materias, setMaterias] = useState<Materia[]>([])
  const [estados, setEstados] = useState<Record<string, Estado>>({})
  const [correo, setCorreo] = useState('')
  const [dialogo, setDialogo] = useState<'nombre' | 'privacidad' | 'salir' | 'iphone' | 'contrasena' | 'usuario' | 'buscable' | 'horario' | 'tema' | 'todos' | 'eliminar' | 'avisos' | null>(null)
  const [texto, setTexto] = useState('')
  const [trabajando, setTrabajando] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [copiado, setCopiado] = useState(false)
  const [tema, setTema] = useState<Tema>(leerTema)
  const avisos = useAvisos()

  useEffect(() => {
    repo.perfil().then(setPerfil).catch(() => {})
    repo.carreraDetalle(carreraId).then(setCarrera).catch(() => {})
    repo.materias(carreraId).then(setMaterias).catch(() => {})
    repo.progreso().then((l) => setEstados(Object.fromEntries(l.map((p) => [p.materia_id, p.estado])))).catch(() => {})
    repo.correo().then(setCorreo)
  }, [carreraId])

  const nombre = perfil?.nombre?.trim() || correo.split('@')[0] || 'Estudiante'
  const iniciales = nombre.split(/[\s._-]+/).filter(Boolean).slice(0, 2).map((p) => p[0].toUpperCase()).join('') || '🎓'
  const aprobadas = materias.filter((m) => estados[m.id] === 'aprobada')
  const credTotal = Math.max(materias.reduce((s, m) => s + m.creditos, 0), 1)
  const credAprob = aprobadas.reduce((s, m) => s + m.creditos, 0)
  const faltan = new Set(materias.filter((m) => estados[m.id] !== 'aprobada').map((m) => m.cuatrimestre)).size
  const periodos = [...new Set(materias.map((m) => m.cuatrimestre))].sort((a, b) => a - b)

  const mostrarInstalar = !yaInstalada() && (instalar || esIphone)

  return (
    <>
      <div style={{ background: 'linear-gradient(to bottom, color-mix(in srgb, #3d3a9e 55%, transparent), transparent)',
        padding: 'calc(16px + env(safe-area-inset-top)) 20px 16px', textAlign: 'center' }}>
        <div style={{ width: 96, height: 96, borderRadius: '50%', background: 'var(--acento)', border: '4px solid var(--fondo)',
          margin: '0 auto', display: 'grid', placeItems: 'center', color: '#fff', fontSize: 36, fontWeight: 700 }}>{iniciales}</div>
        <div style={{ marginTop: 12, display: 'flex', alignItems: 'baseline', justifyContent: 'center', gap: 8 }}>
          <strong style={{ fontSize: 24 }}>{nombre}</strong>
          <button className="boton-texto" style={{ padding: 0, fontSize: 14 }} onClick={() => { setTexto(perfil?.nombre ?? ''); setDialogo('nombre') }}>Cambiar</button>
        </div>
        {carrera && (
          <div className="suave" style={{ fontSize: 15, marginTop: 6, display: 'inline-flex', alignItems: 'center', gap: 8 }}>
            {carrera.universidades && <LogoUniversidad u={carrera.universidades} tam={26} />}
            {carrera.nombre}
          </div>
        )}
      </div>

      <div className="contenido" style={{ gap: 14 }}>
       <div className="cuenta-columnas">
        <div>
        <div className="tarjeta" style={{ display: 'flex', padding: '16px 0' }}>
          <Numero valor={perfil?.indice != null ? perfil.indice.toFixed(2) : '—'} etiqueta="Índice" />
          <span style={{ width: 1, background: 'var(--borde)' }} />
          <Numero valor={String(aprobadas.length)} etiqueta="Aprobadas" />
          <span style={{ width: 1, background: 'var(--borde)' }} />
          <Numero valor={String(credAprob)} etiqueta="Créditos" />
        </div>

        <div className="tarjeta" style={{ padding: 18 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <strong style={{ fontSize: 17 }}>Avance de carrera</strong>
            <span style={{ flex: 1 }} />
            <strong style={{ fontSize: 26, color: 'var(--acento)' }}>{Math.floor((credAprob * 100) / credTotal)}%</strong>
          </div>
          <div style={{ display: 'flex', gap: 4, margin: '12px 0 10px' }}>
            {periodos.map((p) => {
              const lista = materias.filter((m) => m.cuatrimestre === p)
              const total = Math.max(lista.reduce((s, m) => s + m.creditos, 0), 1)
              const hecho = lista.filter((m) => estados[m.id] === 'aprobada').reduce((s, m) => s + m.creditos, 0)
              return (
                <div key={p} style={{ flex: 1, height: 8, borderRadius: 99, background: 'var(--tarjeta-alta)', overflow: 'hidden' }}>
                  <div style={{ width: `${(hecho / total) * 100}%`, height: '100%', background: 'var(--acento)' }} />
                </div>
              )
            })}
          </div>
          <div style={{ display: 'flex', fontSize: 13 }} className="suave">
            <span className="mono">{credAprob} / {credTotal} cr.</span>
            <span style={{ flex: 1 }} />
            <span>{faltan === 0 ? '¡Carrera completa! 🎉' : faltan === 1 ? 'Te falta 1 período' : `Te faltan ${faltan} períodos`}</span>
          </div>
        </div>

        <div className="seccion">App</div>
        <div className="tarjeta">
          {mostrarInstalar && (
            <>
              <Fila icono="descargar" titulo="Instalar MiCarrera" subtitulo="Tenla en tu pantalla de inicio, como una app"
                onClick={() => (instalar ? instalar.prompt() : setDialogo('iphone'))} />
              <hr className="divisor" />
            </>
          )}
          <Fila icono="campana" titulo="Avisos de clase y entregas" subtitulo={textoAvisos(avisos.estado, avisos.minutos)}
            onClick={() => setDialogo('avisos')} />
          <hr className="divisor" />
          <Fila icono="android" titulo="App para Android" subtitulo="Con importar del portal y alertas de clase. Se actualiza sola."
            onClick={() => { window.location.href = '/descargas/MiCarrera.apk' }} />
        </div>

        </div>
        <div>
        <div className="seccion">Amigos y privacidad</div>
        <div className="tarjeta">
          <Fila icono="persona" titulo="Tu usuario" subtitulo={perfil?.usuario ? `@${perfil.usuario}` : 'Crea tu @usuario'} onClick={() => setDialogo('usuario')} />
          <hr className="divisor" />
          <Fila icono="persona" titulo="Amigos" subtitulo="Solicitudes, clases en común y horas libres" onClick={onAmigos} />
          <hr className="divisor" />
          <Fila icono="correo" titulo="Invitar amigos" subtitulo={copiado ? '¡Copiado! Pégalo en WhatsApp' : 'Comparte tu enlace de MiCarrera'}
            onClick={async () => { if ((await compartirInvitacion(perfil?.usuario ?? null)) === 'copiado') setCopiado(true) }} />
          <hr className="divisor" />
          <Fila icono="candado" titulo="Quién te puede encontrar"
            subtitulo={OPCIONES_BUSCABLE.find(([v]) => v === (perfil?.buscable ?? 'universidad'))?.[1] ?? ''} onClick={() => setDialogo('buscable')} />
          <hr className="divisor" />
          <Fila icono="horario" titulo="Quién ve tu horario"
            subtitulo={OPCIONES_HORARIO.find(([v]) => v === (perfil?.horario_visible ?? 'amigos'))?.[1] ?? ''} onClick={() => setDialogo('horario')} />
          <hr className="divisor" />
          <Fila icono="lista" titulo="Mostrar mis materias aprobadas"
            subtitulo={perfil?.logros_visibles === false ? 'No: tus amigos no ven cuando apruebas' : 'Sí: tus amigos pueden chocarte los 5 🙌'}
            onClick={() => {
              const v = perfil?.logros_visibles === false
              setPerfil((x) => (x ? { ...x, logros_visibles: v } : x))
              repo.guardarLogrosVisibles(v).catch(() => setPerfil((x) => (x ? { ...x, logros_visibles: !v } : x)))
            }} />
          <hr className="divisor" />
          <Fila icono="candado" titulo="Tus notas e índice" subtitulo="Nunca se comparten con nadie" />
        </div>

        <div className="seccion">Portal</div>
        <div className="tarjeta">
          <Fila icono="android" titulo="Importar del portal de tu universidad"
            subtitulo={perfil?.portal_actualizado ? `Actualizado el ${new Date(perfil.portal_actualizado).toLocaleDateString('es-DO', { day: 'numeric', month: 'long' })} desde Android` : 'Disponible en la app de Android'} />
          <hr className="divisor" />
          <Fila icono="candado" titulo="Qué pasa al conectar tu portal" subtitulo="Lo que leemos, lo que se guarda y lo que no." onClick={() => setDialogo('privacidad')} />
        </div>

        <div className="seccion">Carrera</div>
        <div className="tarjeta">
          <Fila icono="refrescar" titulo="Cambiar de carrera" subtitulo={carrera?.nombre ?? ''} onClick={onCambiarCarrera} />
          <hr className="divisor" />
          <Fila icono="brujula" titulo="¿Qué debo estudiar?" subtitulo="Test vocacional: carreras y universidades para ti" onClick={onTest} />
        </div>

        <div className="seccion">Apariencia</div>
        <div className="tarjeta">
          <Fila icono="tema" titulo="Tema" subtitulo={OPCIONES_TEMA.find(([v]) => v === tema)?.[1] ?? ''} onClick={() => setDialogo('tema')} />
        </div>

        <div className="seccion">Ayuda</div>
        <div className="tarjeta">
          <button onClick={onCinco} style={{ display: 'flex', alignItems: 'center', gap: 14, width: '100%', padding: '12px 16px', background: 'none', border: 0, textAlign: 'left' }}>
            <Cinco tam={40} />
            <span style={{ flex: 1 }}>
              <span style={{ display: 'block', fontWeight: 600, fontSize: 15 }}>5inco, tu guía</span>
              <span className="suave" style={{ fontSize: 13 }}>Ayuda, comentarios y el tutorial de la app</span>
            </span>
            <Icono n="derecha" color="var(--gris)" />
          </button>
        </div>

        <div className="seccion">Legal</div>
        <div className="tarjeta">
          <Fila icono="candado" titulo="Política de privacidad" subtitulo="Qué datos usamos y quién los ve" onClick={() => window.open('/privacidad.html', '_blank')} />
          <hr className="divisor" />
          <Fila icono="lista" titulo="Términos y condiciones" subtitulo="Las reglas para usar MiCarrera" onClick={() => window.open('/terminos.html', '_blank')} />
        </div>

        <div className="seccion">Sesión</div>
        <div className="tarjeta">
          <Fila icono="candado" titulo="Contraseña" subtitulo="Crear o cambiar tu contraseña" onClick={() => setDialogo('contrasena')} />
          <hr className="divisor" />
          <Fila icono="salir" titulo="Cerrar sesión" subtitulo={correo} onClick={() => setDialogo('salir')} />
          <hr className="divisor" />
          <Fila icono="candado" titulo="Cerrar sesión en todos los dispositivos"
            subtitulo="Si perdiste tu teléfono o entraste en otra computadora" onClick={() => setDialogo('todos')} />
          <hr className="divisor" />
          <Fila icono="cerrar" titulo="Eliminar mi cuenta" subtitulo="Borra tu cuenta y todos tus datos para siempre"
            onClick={() => { setTexto(''); setError(null); setDialogo('eliminar') }} />
        </div>
        </div>
       </div>

        <p className="suave" style={{ textAlign: 'center', fontSize: 12, padding: '20px 0' }}>MiCarrera web · Creado por Albert Silvestre</p>
      </div>

      {dialogo === 'avisos' && <DialogoAvisos avisos={avisos} onCerrar={() => setDialogo(null)} />}
      {dialogo === 'contrasena' && <DialogoContrasena primeraVez={false} onCerrar={() => setDialogo(null)} />}
      {dialogo === 'usuario' && (
        <DialogoUsuario actual={perfil?.usuario ?? null} onCerrar={(nuevo) => {
          setDialogo(null)
          if (nuevo) setPerfil((p) => (p ? { ...p, usuario: nuevo } : p))
        }} />
      )}
      {dialogo === 'tema' && (
        <DialogoOpciones titulo="Tema" opciones={OPCIONES_TEMA} actual={tema} onElegir={(valor) => {
          setDialogo(null)
          if (valor) { setTema(valor as Tema); guardarTema(valor as Tema) }
        }} />
      )}
      {(dialogo === 'buscable' || dialogo === 'horario') && (
        <DialogoOpciones
          titulo={dialogo === 'buscable' ? '¿Quién te puede encontrar?' : '¿Quién ve tu horario?'}
          opciones={dialogo === 'buscable' ? OPCIONES_BUSCABLE : OPCIONES_HORARIO}
          actual={dialogo === 'buscable' ? perfil?.buscable ?? 'universidad' : perfil?.horario_visible ?? 'amigos'}
          onElegir={(valor) => {
            const que = dialogo
            setDialogo(null)
            if (!valor) return
            const cambio = que === 'buscable' ? { buscable: valor } : { horario_visible: valor }
            setPerfil((p) => (p ? { ...p, ...cambio } as typeof p : p))
            repo.guardarPrivacidad(cambio).catch(() => {})
          }} />
      )}

      {dialogo === 'nombre' && (
        <Modal onCerrar={() => setDialogo(null)}>
          <h2>¿Cómo te llamas?</h2>
          <input className="campo" placeholder="Tu nombre" value={texto} maxLength={40} onChange={(e) => setTexto(e.target.value)} autoFocus />
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setDialogo(null)}>Cancelar</button>
            <button className="boton" disabled={!texto.trim()} onClick={() => {
              setPerfil((p) => (p ? { ...p, nombre: texto.trim() } : p)); setDialogo(null)
              repo.guardarNombre(texto).catch(() => {})
            }}>Guardar</button>
          </div>
        </Modal>
      )}

      {dialogo === 'privacidad' && (
        <Modal onCerrar={() => setDialogo(null)}>
          <h2>Tu portal y tu privacidad 🔒</h2>
          <Punto e="🔑" t="Tu contraseña" d="La escribes solo en la página de tu universidad. MiCarrera no la ve ni la guarda." />
          <Punto e="📱" t="Lo que leemos" d="Solo el texto de la página que tú abres (récord u horario), y se lee en tu teléfono." />
          <Punto e="☁️" t="Lo que se guarda" d="Tus materias, notas, índice y horario, en tu cuenta. Solo tú los ves." />
          <Punto e="🧹" t="Al salir" d="Borramos las cookies y la sesión del portal del teléfono." />
          <div className="acciones"><button className="boton-texto" onClick={() => setDialogo(null)}>Entendido</button></div>
        </Modal>
      )}

      {dialogo === 'iphone' && (
        <Modal onCerrar={() => setDialogo(null)}>
          <h2>Instálala en tu iPhone 📲</h2>
          <Punto e="1️⃣" t="Abre esta página en Safari" d="En otros navegadores de iPhone no aparece la opción." />
          <Punto e="2️⃣" t="Toca el botón Compartir" d="El cuadrito con la flecha hacia arriba, abajo en la pantalla." />
          <Punto e="3️⃣" t='Elige "Agregar a inicio"' d="Baja un poco en el menú si no lo ves. Luego toca Agregar." />
          <div className="acciones"><button className="boton-texto" onClick={() => setDialogo(null)}>Listo</button></div>
        </Modal>
      )}

      {dialogo === 'salir' && (
        <Modal onCerrar={() => setDialogo(null)}>
          <h2>¿Cerrar sesión?</h2>
          <p className="suave" style={{ margin: 0 }}>Tus datos quedan guardados. Vuelves a entrar con tu correo.</p>
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setDialogo(null)}>Cancelar</button>
            <button className="boton" onClick={() => { setDialogo(null); repo.salir() }}>Cerrar sesión</button>
          </div>
        </Modal>
      )}

      {dialogo === 'todos' && (
        <Modal onCerrar={() => setDialogo(null)}>
          <h2>¿Salir de todos lados?</h2>
          <p className="suave" style={{ margin: 0 }}>Se cierra tu sesión en todos los teléfonos y computadoras, incluida esta. Tus datos quedan guardados.</p>
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} onClick={() => setDialogo(null)}>Cancelar</button>
            <button className="boton" onClick={() => { setDialogo(null); repo.salirDeTodo() }}>Cerrar en todos</button>
          </div>
        </Modal>
      )}

      {dialogo === 'eliminar' && (
        <Modal onCerrar={() => { if (!trabajando) setDialogo(null) }}>
          <h2>¿Eliminar tu cuenta?</h2>
          <p className="suave" style={{ margin: 0 }}>
            Se borran para siempre tus notas, horario, tareas, amigos y todo lo demás. No se puede deshacer.
            Escribe <strong>ELIMINAR</strong> para confirmar.
          </p>
          <input className="campo" value={texto} placeholder="ELIMINAR" onChange={(e) => setTexto(e.target.value.toUpperCase())} />
          {error && <div style={{ color: 'var(--rojo)', fontSize: 13 }}>{error}</div>}
          <div className="acciones">
            <button className="boton-texto" style={{ color: 'var(--texto-suave)' }} disabled={trabajando} onClick={() => setDialogo(null)}>Cancelar</button>
            <button className="boton" style={{ background: 'var(--rojo)' }} disabled={texto !== 'ELIMINAR' || trabajando} onClick={async () => {
              setTrabajando(true); setError(null)
              try { await repo.eliminarMiCuenta() } catch { setError('No se pudo eliminar. Intenta otra vez.'); setTrabajando(false) }
            }}>{trabajando ? 'Eliminando…' : 'Eliminar para siempre'}</button>
          </div>
        </Modal>
      )}
    </>
  )
}

function Numero({ valor, etiqueta }: { valor: string; etiqueta: string }) {
  return (
    <div style={{ flex: 1, textAlign: 'center' }}>
      <strong style={{ fontSize: 24, display: 'block' }}>{valor}</strong>
      <span className="suave" style={{ fontSize: 13 }}>{etiqueta}</span>
    </div>
  )
}

function Fila({ icono, titulo, subtitulo, onClick }: { icono: NombreIcono; titulo: string; subtitulo: string; onClick?: () => void }) {
  return (
    <button onClick={onClick} disabled={!onClick} style={{ display: 'flex', alignItems: 'center', gap: 14, padding: 16, width: '100%',
      background: 'none', border: 0, textAlign: 'left', cursor: onClick ? 'pointer' : 'default' }}>
      <span style={{ width: 40, height: 40, borderRadius: 12, background: 'var(--tarjeta-alta)', display: 'grid', placeItems: 'center', flex: 'none' }}>
        <Icono n={icono} color="var(--acento)" />
      </span>
      <span style={{ flex: 1, minWidth: 0 }}>
        <span style={{ display: 'block', fontWeight: 600, fontSize: 16 }}>{titulo}</span>
        {subtitulo && <span className="suave" style={{ fontSize: 13 }}>{subtitulo}</span>}
      </span>
      {onClick && <Icono n="derecha" color="var(--texto-suave)" />}
    </button>
  )
}

function Punto({ e, t, d }: { e: string; t: string; d: string }) {
  return (
    <div style={{ display: 'flex', gap: 10 }}>
      <span style={{ fontSize: 18 }}>{e}</span>
      <span><strong style={{ fontSize: 14, display: 'block' }}>{t}</strong><span className="suave" style={{ fontSize: 13 }}>{d}</span></span>
    </div>
  )
}
