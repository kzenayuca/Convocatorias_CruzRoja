import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, NavLink, Route, Routes, useNavigate } from 'react-router-dom';
import './styles.css';

type Session = { id: string; name: string; email: string; role: 'VOLUNTARIO' | 'RECLUTADOR' | 'JEFATURA'; graduated: boolean };
type Callout = { id: string; title: string; description?: string; status: string; date: string; endDate?: string; location?: string; capacity: number; enrolled: number; minimumLevel?: string };
const calloutStatuses: Record<string, string> = { PUBLICADA: 'Publicada', CONFIRMADA: 'Confirmada', CANCELADA: 'Cancelada', FINALIZADA: 'Finalizada' };
function canEnroll(call: Callout) {
  return ['PUBLICADA', 'CONFIRMADA'].includes(call.status) && call.enrolled < call.capacity && new Date(call.date).getTime() > Date.now();
}
type Notice = { id: number; title: string; body: string; kind: string; time: string; to: string; urgent?: boolean; unread?: boolean };
const demoNotices: Notice[] = [
  { id: 1, title: 'Nueva convocatoria urgente', body: 'Se necesitan voluntarios para una actividad de apoyo.', kind: 'Convocatorias', time: 'Hace 5 min', to: '/callouts', urgent: true, unread: true },
  { id: 2, title: 'Inscripción aceptada', body: 'Tu inscripción para la campaña de salud fue registrada.', kind: 'Mis avisos', time: 'Hace 1 hora', to: '/services', unread: true },
  { id: 3, title: 'Justificación en revisión', body: 'Jefatura está revisando tu solicitud.', kind: 'Mis avisos', time: 'Ayer', to: '/services' },
  { id: 4, title: 'Asistencia registrada', body: 'Se registraron tus horas de servicio.', kind: 'Mis avisos', time: 'Hace 2 días', to: '/services' },
  { id: 5, title: 'Nuevo comunicado', body: 'Jefatura publicó un aviso para los voluntarios.', kind: 'Comunicados', time: 'Hace 3 días', to: '/notifications' },
];

function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [email, setEmail] = useState('voluntario@demo.pe'); const [password, setPassword] = useState('convocatorias'); const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function submit(event: React.FormEvent) { event.preventDefault(); setBusy(true); setError(''); try { const response = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) }); if (!response.ok) throw new Error(); onLogin(await response.json()); } catch { setError('No se pudo iniciar sesión. Verifica tus datos e inténtalo de nuevo.'); } finally { setBusy(false); } }
  return <main className="login-page"><form className="login-card" onSubmit={submit}><div className="brand-mark">+</div><p className="eyebrow">Cruz Roja · Arequipa</p><h1>Qué bueno tenerte aquí</h1><p>Ingresa para continuar con tus actividades de voluntariado.</p><label className="field">Correo electrónico<input type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required /></label><label className="field">Contraseña<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required /></label>{error && <p className="form-error" role="alert">{error}</p>}<button className="button login-button" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button><details><summary>Cuentas de demostración</summary><p>voluntario@demo.pe · reclutador@demo.pe · jefatura@demo.pe<br/>Contraseña: convocatorias</p><p>Más perfiles: maria@demo.pe, diego@demo.pe, lucia@demo.pe, reclutador.formacion@demo.pe, reclutador2@demo.pe y jefatura2@demo.pe.</p><p>inactivo@demo.pe permite comprobar el acceso denegado.</p></details></form></main>;
}

function Home({ user, calls }: { user: Session; calls: Callout[] }) {
  const next = calls.find(call => ['PUBLICADA', 'CONFIRMADA'].includes(call.status) && new Date(call.date).getTime() > Date.now()); const n = demoNotices.filter(x => x.unread).length;
  return <><div className="welcome"><div><p className="eyebrow">Tu espacio de voluntariado</p><h1>Hola, {user.name.split(' ')[0]} <span aria-hidden="true">👋</span></h1><p className="lead">Aquí tienes lo importante para hoy.</p></div><div className="welcome-art" aria-hidden="true">❤️</div></div><div className="grid three"><article className="card"><p className="eyebrow">Mis horas</p><div className="stat">24.5 h</div><p className="stat-label">En servicios registrados este mes</p><Link className="link" to="/services">Ver detalle →</Link></article><article className="card"><p className="eyebrow">Actividad próxima</p><h2>{next?.title ?? 'Aún no tienes actividades'}</h2><p>{next ? `${new Date(next.date).toLocaleDateString('es-PE')} · ${next.location ?? 'Arequipa'}` : 'Explora las convocatorias disponibles.'}</p><Link className="link" to="/callouts">Explorar convocatorias →</Link></article><article className="card"><p className="eyebrow">Novedades</p><div className="stat">{n} <span className="stat-label">sin leer</span></div><p className="stat-label">Tienes avisos importantes por revisar.</p><Link className="link" to="/notifications">Ir a notificaciones →</Link></article></div><div className="page-heading" style={{ marginTop: 34 }}><div><p className="eyebrow">Para ti</p><h2>Accesos rápidos</h2></div></div><div className="grid"><Link className="card" to="/callouts"><h2>📍 Convocatorias</h2><p>Encuentra tu próxima actividad.</p></Link><Link className="card" to="/services"><h2>🕒 Mis servicios</h2><p>Revisa inscripciones, asistencia y horas.</p></Link></div></>;
}

function CalloutsPage({ calls, user }: { calls: Callout[]; user: Session }) {
  const [query, setQuery] = useState(''); const [selected, setSelected] = useState(''); const [message, setMessage] = useState('');
  const filtered = useMemo(() => calls.filter(c => `${c.title} ${c.description ?? ''} ${c.location ?? ''} ${c.status} ${c.minimumLevel ?? ''}`.toLowerCase().includes(query.toLowerCase())), [calls, query]);
  const suggestions = query.trim() && !selected ? filtered.slice(0, 4) : [];
  function enroll(call: Callout) {
    if (!canEnroll(call)) { setMessage('Esta convocatoria no está disponible para inscripción.'); return; }
    const key = `enrollments-${user.id}`;
    const values = JSON.parse(localStorage.getItem(key) ?? '[]') as string[];
    if (values.includes(call.id)) { setMessage('Ya estás inscrito en esta convocatoria.'); return; }
    values.push(call.id);
    localStorage.setItem(key, JSON.stringify(values));
    setMessage(`Tu inscripción a “${call.title}” quedó registrada en este navegador (demo).`);
  }
  return <>
    <div className="page-heading"><div><p className="eyebrow">Encuentra tu próxima actividad</p><h1>Convocatorias</h1><p className="lead">Explora los servicios y sus estados. Las nuevas inscripciones son locales de demostración.</p></div></div>
    <div className="toolbar">
      <div style={{ position: 'relative', flex: 1 }}>
        <input aria-label="Buscar convocatorias" value={query} onChange={e => { setQuery(e.target.value); setSelected(''); }} placeholder="Busca por actividad, lugar o estado…" autoComplete="off"/>
        {suggestions.length > 0 && <div className="suggestions" role="listbox">{suggestions.map(c => <button key={c.id} role="option" aria-selected={selected === c.id} onClick={() => { setQuery(c.title); setSelected(c.id); }}>{c.title} <span className="role-label"> · {c.location}</span></button>)}</div>}
      </div>
      <button className="button secondary" onClick={() => { setQuery(''); setSelected(''); }}>Limpiar</button>
    </div>
    {message && <div className="panel" role="status">{message}<button className="icon-button" onClick={() => setMessage('')}>Cerrar</button></div>}
    <div className="list">
      {filtered.map(c => <article className="list-item" key={c.id}>
        <div>
          <span className={`badge ${c.status === 'CANCELADA' ? 'red' : c.enrolled >= c.capacity ? 'warning' : ''}`}>{calloutStatuses[c.status] ?? c.status}</span>
          <h2 style={{ marginTop: 10 }}>{c.title}</h2>
          <p>{c.description || 'Actividad de voluntariado'} · 📍 {c.location || 'Arequipa'}</p>
          <p>🗓 {new Date(c.date).toLocaleString('es-PE')}{c.endDate && ` – ${new Date(c.endDate).toLocaleTimeString('es-PE', { hour: '2-digit', minute: '2-digit' })}`} · {c.enrolled}/{c.capacity} participantes</p>
          {c.minimumLevel && <small className="role-label">Nivel requerido: {c.minimumLevel.toLowerCase()}</small>}
        </div>
        <button className="button" disabled={!canEnroll(c)} onClick={() => enroll(c)}>{c.enrolled >= c.capacity ? 'Sin cupos' : canEnroll(c) ? 'Inscribirme' : 'No disponible'}</button>
      </article>)}
      {!filtered.length && <div className="empty">No encontramos convocatorias con esa búsqueda.</div>}
    </div>
  </>;
}

function ServicesPage({ user }: { user: Session }) {
  const [enrolled] = useState<string[]>(() => JSON.parse(localStorage.getItem(`enrollments-${user.id}`) ?? '[]'));
  return <><p className="eyebrow">Tu participación</p><h1>Mis servicios</h1><p className="lead">Inscripciones, asistencias y horas en un solo lugar.</p><div className="metrics"><div className="metric-card"><b>24.5 h</b><span>Horas del mes</span></div><div className="metric-card"><b>{enrolled.length}</b><span>Inscripciones activas</span></div><div className="metric-card"><b>0 h</b><span>Pendientes de revisión</span></div></div><div className="page-heading" style={{ marginTop: 28 }}><h2>Mis inscripciones</h2><Link className="button soft" to="/callouts">Buscar convocatoria</Link></div>{enrolled.length ? enrolled.map(id => <article className="list-item" key={id}><div><h2>Convocatoria inscrita</h2><p>Tu inscripción está activa.</p></div><span className="badge">Aceptada</span></article>) : <div className="empty">Todavía no tienes inscripciones. <br/><Link className="link" to="/callouts">Explora las convocatorias →</Link></div>}<div className="page-heading" style={{ marginTop: 30 }}><h2>Asistencia y horas</h2></div><div className="card"><h2>Registro de actividad</h2><p>Las horas se calculan con tu hora de entrada y salida. Si falta un registro, comunícate con jefatura para presentar evidencia.</p><span className="badge warning">Consulta pendiente: 0</span></div></>;
}

function NotificationsPage() {
  const [filter, setFilter] = useState('Todas'); const [notices, setNotices] = useState(demoNotices);
  const filters = ['Todas', 'No leídas', 'Convocatorias', 'Mis avisos'];
  const shown = notices.filter(n => filter === 'Todas' || (filter === 'No leídas' ? n.unread : n.kind === filter));
  function openNotice(notice: Notice) { setNotices(old => old.map(n => n.id === notice.id ? { ...n, unread: false } : n)); }
  return <><p className="eyebrow">Mantente al día</p><h1>Notificaciones</h1><p className="lead">Avisos importantes de tus convocatorias y servicios.</p><div className="filters">{filters.map(f => <button key={f} className={`filter ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>{f}{f === 'No leídas' ? ` (${notices.filter(n => n.unread).length})` : ''}</button>)}</div><div className="list">{shown.map(n => <article key={n.id} className={`notification ${n.unread ? 'unread' : ''}`}><span className="notice-icon">{n.urgent ? '📣' : n.kind === 'Mis avisos' ? '🟢' : '🔔'}</span><div><h3>{n.title} {n.urgent && <span className="badge red">Urgente</span>}</h3><p>{n.body}</p><Link className="link" to={n.to} onClick={() => openNotice(n)}>Ver detalle →</Link></div><span className="notice-time">{n.time}</span></article>)}{!shown.length && <div className="empty">No hay avisos en esta categoría.</div>}</div></>;
}

function ReportsPage() { const [month, setMonth] = useState(new Date().toISOString().slice(0, 7)); return <><p className="eyebrow">Jefatura y reclutamiento</p><h1>Reporte de horas</h1><p className="lead">Consulta la actividad mensual del equipo.</p><label className="field" style={{ maxWidth: 260 }}>Mes<input type="month" value={month} onChange={e => setMonth(e.target.value)}/></label><div className="card"><div className="metrics"><div className="metric-card"><b>24.5 h</b><span>Horas realizadas</span></div><div className="metric-card"><b>0 h</b><span>Horas pendientes</span></div><div className="metric-card"><b>0 h</b><span>Penalizaciones</span></div></div><div className="divider"/><p>El reporte detallado por voluntario y convocatoria aparecerá aquí.</p><button className="button" onClick={() => window.print()}>Imprimir / Guardar PDF</button></div></>; }

function App() {
  const [user, setUser] = useState<Session | null>(null); const [loading, setLoading] = useState(true); const [calls, setCalls] = useState<Callout[]>([]); const navigate = useNavigate();
  useEffect(() => { fetch('/api/v1/auth/me').then(async r => { if (r.ok) setUser(await r.json()); }).catch(() => {}).finally(() => setLoading(false)); }, []);
  useEffect(() => {
    if (!user) return;
    fetch('/api/v1/callouts').then(r => r.ok ? r.json() : []).then((data: Callout[]) => setCalls(data)).catch(() => setCalls([]));
  }, [user]);
  async function logout() { await fetch('/api/v1/auth/logout', { method: 'POST' }); setUser(null); navigate('/'); }
  if (loading) return <main className="login-page"><div className="login-card">Cargando tu espacio…</div></main>;
  if (!user) return <Login onLogin={setUser}/>;
  const canManage = user.role === 'JEFATURA' || (user.role === 'RECLUTADOR' && user.graduated);
  const nav = [{ to: '/', label: 'Inicio', icon: '⌂' }, { to: '/callouts', label: 'Convocatorias', icon: '◷' }, { to: '/services', label: 'Mis servicios', icon: '✓' }, { to: '/notifications', label: 'Notificaciones', icon: '♧' }, ...(canManage ? [{ to: '/reports', label: 'Reportes', icon: '▤' }] : []), ...(user.role !== 'VOLUNTARIO' ? [{ to: '/inventory', label: 'Materiales', icon: '▧' }] : [])];
  const count = demoNotices.filter(n => n.unread).length;
  return <><a className="skip" href="#main">Saltar al contenido</a><header><div><strong>CRUZ ROJA AREQUIPA</strong><small>Filial Arequipa</small></div><div><NavLink className="icon-button" to="/notifications" aria-label={`${count} notificaciones sin leer`}>♧ {count}</NavLink><span className="user-chip"><span className="avatar">{user.name.charAt(0)}</span><span>{user.name}<small className="role-label">{user.role}</small></span></span><button className="header-button" onClick={logout}>Salir</button></div></header><div className="layout"><aside><nav aria-label="Navegación principal">{nav.map(item => <NavLink key={item.to} to={item.to} end={item.to === '/'}><span aria-hidden="true">{item.icon}</span>{item.label}</NavLink>)}</nav></aside><main id="main"><Routes><Route path="/" element={<Home user={user} calls={calls}/>}/><Route path="/callouts" element={<CalloutsPage calls={calls} user={user}/>}/><Route path="/services" element={<ServicesPage user={user}/>}/><Route path="/notifications" element={<NotificationsPage/>}/><Route path="/reports" element={canManage ? <ReportsPage/> : <Home user={user} calls={calls}/>}/><Route path="/inventory" element={<section><p className="eyebrow">Consulta de materiales</p><h1>Materiales para servicios</h1><p className="lead">Consulta el material disponible y su asignación a convocatorias.</p><div className="empty">La lista de materiales se conectará con el inventario de la filial.</div></section>}/><Route path="*" element={<Home user={user} calls={calls}/>}/></Routes></main></div></>;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><App/></BrowserRouter></React.StrictMode>);
