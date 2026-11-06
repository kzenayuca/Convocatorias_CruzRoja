import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { api, monthNow, Row, Session, useData } from './api';
import { ErrorNotice, SaveFeedback } from './components';
import { AdministrationPage, Login, ProfilePage, UsersPage } from './AccountPages';
import { SchedulePage } from './SchedulePage';
import { CalloutsPage, DetailPage } from './ServicePages';
import { JustificationsPage, NotificationsPage, ReportsPage, ServicesPage } from './ActivityPages';
import './styles.css';

function Home({ user }: { user: Session }) {
  return user.role === 'JEFATURA' ? <AdministrationHome/> : <VolunteerHome user={user}/>;
}
function AdministrationHome() {
  const reports = useData<Row[]>(`/reports?month=${monthNow()}`, []);
  const justifications = useData<Row[]>('/justifications', []);
  return <><div className="welcome"><div><p className="eyebrow">Jefatura · administración institucional</p><h1>Panel de gestión</h1><p className="lead">Organiza los servicios y revisa las incidencias de los voluntarios. Tu cuenta no tiene metas ni penalizaciones personales.</p></div></div><ErrorNotice message={reports.error || justifications.error}/>
    <div className="grid three"><Link className="card" to="/reports"><h2>Horas de voluntarios</h2><div className="stat">{reports.data.reduce((n, r) => n + Number(r.earned ?? 0), 0).toLocaleString('es-PE')} h</div><p>Reconocidas este mes · Ver reportes y ajustes →</p></Link><Link className="card" to="/justifications"><h2>Justificaciones pendientes</h2><div className="stat">{justifications.data.filter(j => j.status === 'PENDIENTE').length}</div><p>Revisar motivos y registrar decisiones →</p></Link><Link className="card" to="/schedule"><h2>Programación general</h2><p>Consultar servicios, equipos y descargar PDF →</p></Link></div>
    <h2>Administración</h2><div className="grid"><Link className="card" to="/callouts"><h3>Convocatorias y equipos</h3><p>Crear, completar plazas y gestionar responsables →</p></Link><Link className="card" to="/users"><h3>Usuarios y habilitaciones</h3><p>Revisar solicitudes y cuentas →</p></Link><Link className="card" to="/administration"><h3>Configuración institucional</h3><p>Metas para voluntarios e historial de códigos →</p></Link></div></>;
}
function VolunteerHome({ user }: { user: Session }) {
  const calls = useData<Row[]>('/enrollments', []); const hours = useData<Row>('/attendance/hours', {}); const notices = useData<Row[]>('/notifications', []);
  const next = calls.data.filter(c => c.status === 'CONFIRMADA' && new Date(c.start_at).getTime() > Date.now()).sort((a, b) => a.start_at.localeCompare(b.start_at))[0]; const n = notices.data.filter(x => !x.is_read).length;
  return <><div className="welcome"><div><p className="eyebrow">Tu espacio de voluntariado</p><h1>Hola, {user.name.split(' ')[0]} <span aria-hidden="true">👋</span></h1><p className="lead">Aquí tienes lo importante para hoy.</p></div><div className="welcome-art" aria-hidden="true">❤️</div></div><ErrorNotice message={calls.error || hours.error || notices.error}/><div className="grid three"><article className="card"><p className="eyebrow">Mis horas</p><div className="stat">{hours.data.hours ?? 0} h</div><p className="stat-label">Saldo de servicios de este mes</p><Link className="link" to="/services">Ver detalle →</Link></article><article className="card"><p className="eyebrow">Actividad próxima</p><h2>{next?.title ?? 'Aún no tienes actividades'}</h2><p>{next ? `${new Date(next.start_at).toLocaleDateString('es-PE', { timeZone: 'America/Lima' })} · ${next.location ?? 'Arequipa'}` : 'Explora las convocatorias disponibles.'}</p><Link className="link" to="/callouts">Explorar convocatorias →</Link></article><article className="card"><p className="eyebrow">Novedades</p><div className="stat">{n} <span className="stat-label">sin leer</span></div><p className="stat-label">Tienes avisos importantes por revisar.</p><Link className="link" to="/notifications">Ir a notificaciones →</Link></article></div><div className="page-heading" style={{ marginTop: 34 }}><div><p className="eyebrow">Para ti</p><h2>Accesos rápidos</h2></div></div><div className="grid"><Link className="card" to="/callouts"><h2>📍 Convocatorias</h2><p>Encuentra tu próxima actividad.</p></Link><Link className="card" to="/services"><h2>🕒 Mis servicios</h2><p>Revisa inscripciones, asistencia y horas.</p></Link></div></>;
}

function Workspace({ user, logout }: { user: Session; logout: () => Promise<void> }) {
  const { pathname } = useLocation();
  useEffect(() => { window.scrollTo(0, 0); }, [pathname]);
  const canReport = user.role === 'JEFATURA';
  const notices = useData<Row[]>('/notifications', []); const [error, setError] = useState('');
  const nav = [{ to: '/', label: 'Inicio', icon: '⌂' }, { to: '/callouts', label: 'Convocatorias', icon: '◷' }, { to: '/schedule', label: canReport ? 'Programación general' : 'Mi programación', icon: '▦' }, ...(!canReport ? [{ to: '/services', label: 'Mis servicios', icon: '✓' }] : []), { to: '/history', label: 'Historial', icon: '▤' }, { to: '/profile', label: 'Mi perfil', icon: '○' }, { to: '/justifications', label: 'Justificaciones', icon: '✓' }, { to: '/notifications', label: 'Notificaciones', icon: '♧' }, ...(canReport ? [{ to: '/reports', label: 'Reportes', icon: '▤' }, { to: '/users', label: 'Usuarios', icon: '♙' }, { to: '/administration', label: 'Administración', icon: '⚙' }] : [])];
  const count = notices.data.filter(n => !n.is_read).length;
  return <><a className="skip" href="#main">Saltar al contenido</a><header><div><strong>CRUZ ROJA AREQUIPA</strong><small>Filial Arequipa</small></div><div><NavLink className="icon-button" to="/notifications" aria-label={`${count} notificaciones sin leer`}>♧ {count}</NavLink><Link className="user-chip" to="/profile"><span className="avatar">{user.name.charAt(0)}</span><span>{user.name}<small className="role-label">{user.role}</small></span></Link><button className="header-button" onClick={() => { void logout().catch(e => setError(e.message)); }}>Salir</button></div></header><div className="layout"><aside><nav aria-label="Navegación principal">{nav.map(item => <NavLink key={item.to} to={item.to} end={item.to === '/'}><span aria-hidden="true">{item.icon}</span>{item.label}</NavLink>)}</nav></aside><main id="main"><ErrorNotice message={error}/><Routes>
    <Route path="/" element={<Home user={user}/>}/><Route path="/schedule" element={<SchedulePage user={user}/>}/><Route path="/administration" element={canReport ? <AdministrationPage/> : <Home user={user}/>}/><Route path="/callouts" element={<CalloutsPage user={user}/>}/><Route path="/callouts/:id" element={<DetailPage user={user}/>}/><Route path="/history" element={<CalloutsPage user={user} history/>}/><Route path="/services" element={canReport ? <ReportsPage/> : <ServicesPage/>}/><Route path="/profile" element={<ProfilePage/>}/><Route path="/justifications" element={<JustificationsPage user={user}/>}/><Route path="/notifications" element={<NotificationsPage user={user}/>}/><Route path="/reports" element={canReport ? <ReportsPage/> : <Home user={user}/>}/><Route path="/users" element={user.role === 'JEFATURA' ? <UsersPage/> : <Home user={user}/>}/><Route path="*" element={<Home user={user}/>}/>
  </Routes><SaveFeedback/></main></div></>;
}
function App() {
  const [user, setUser] = useState<Session | null>(null); const [loading, setLoading] = useState(true); const navigate = useNavigate();
  useEffect(() => {
    api<Session>('/auth/me').then(setUser).catch(() => {}).finally(() => setLoading(false));
    const expired = () => setUser(null); window.addEventListener('session-expired', expired);
    return () => window.removeEventListener('session-expired', expired);
  }, []);
  async function logout() { await api('/auth/logout', 'POST'); setUser(null); navigate('/'); }
  if (loading) return <main className="login-page"><div className="login-card">Cargando tu espacio…</div></main>;
  if (!user) return <Login onLogin={session => { setUser(session); navigate('/'); }}/>;
  return <Workspace key={user.id} user={user} logout={logout}/>;
}
createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><App/></BrowserRouter></React.StrictMode>);
