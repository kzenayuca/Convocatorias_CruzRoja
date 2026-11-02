import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, NavLink, Route, Routes } from 'react-router-dom';
import type { Feature, Role } from './types';
import './shared/ui/tokens.css';
import './shared/ui/ui.css';

type Session = { id: string; name: string; email: string; role: Role; graduated: boolean };
const modules = import.meta.glob('./features/*/index.tsx', { eager: true }) as Record<string, { default: Feature }>;
const features = Object.values(modules).map(module => module.default);

function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [email, setEmail] = useState('voluntario@demo.pe');
  const [password, setPassword] = useState('password');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(event: React.FormEvent) {
    event.preventDefault(); setLoading(true); setError('');
    const response = await fetch('/api/v1/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email, password }) });
    if (response.ok) onLogin(await response.json()); else setError('No pudimos iniciar sesión. Revise el correo y la contraseña.');
    setLoading(false);
  }

  return <main className="login-page" id="main"><form className="login-card" onSubmit={submit}>
    <div className="brand-mark" aria-hidden="true">+</div><p className="eyebrow">Cruz Roja Peruana</p><h1>Bienvenido al sistema</h1><p>Ingrese para gestionar su voluntariado en la Filial Arequipa.</p>
    <label>Correo electrónico<input type="email" value={email} onChange={event => setEmail(event.target.value)} autoComplete="username" required /></label>
    <label>Contraseña<input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" required /></label>
    {error && <p className="form-error" role="alert">! {error}</p>}
    <button className="button login-button" disabled={loading}>{loading ? 'Ingresando…' : 'Iniciar sesión'}</button>
    <details><summary>Cuentas de demostración</summary><p>voluntario@demo.pe<br />reclutador@demo.pe<br />jefatura@demo.pe<br />Contraseña: password</p></details>
  </form></main>;
}

function Home({ session }: { session: Session }) {
  return <section><h1>Hola, {session.name}</h1><p className="lead">Estas son sus tareas principales como {session.role.toLowerCase()}.</p><div className="grid"><article className="card"><span className="badge">✓ Sesión activa</span><h2>Próxima convocatoria</h2><p>Campaña de donación de sangre · 32/40 cupos.</p><div className="progress" aria-label="32 de 40 cupos"><span style={{ width: '80%' }} /></div><Link className="button" to="/callouts">Ver convocatorias</Link></article><article className="panel"><h2>Resumen</h2><strong className="metric">24,5 h</strong><p>Horas firmadas acumuladas.</p><Link to="/attendance">Ver mis horas</Link></article></div></section>;
}

function App() {
  const [session, setSession] = useState<Session | null>(null);
  const [checking, setChecking] = useState(true);
  useEffect(() => { fetch('/api/v1/auth/me').then(async response => { if (response.ok) setSession(await response.json()); }).finally(() => setChecking(false)); }, []);

  async function logout() { await fetch('/api/v1/auth/logout', { method: 'POST' }); setSession(null); }
  if (checking) return <main className="login-page"><div className="login-card"><p aria-live="polite">Cargando sesión…</p></div></main>;
  if (!session) return <Login onLogin={setSession} />;
  const allowedFeatures = features.filter(feature => feature.nav.some(item => item.roles.includes(session.role)));
  const navigation = allowedFeatures.flatMap(feature => feature.nav).filter(item => item.roles.includes(session.role));

  return <><a className="skip" href="#main">Saltar al contenido</a><header><div><strong>Cruz Roja Peruana</strong><small>Filial Arequipa</small></div><div><span>{session.name}</span><span className="role-badge">{session.role}</span><button className="header-button" onClick={logout}>Cerrar sesión</button></div></header><div className="layout"><aside><nav aria-label="Principal"><NavLink to="/">Inicio</NavLink>{navigation.map(item => <NavLink key={item.path} to={item.path}>{item.label}</NavLink>)}<NavLink to="/ayuda">Ayuda</NavLink></nav></aside><main id="main"><Routes><Route path="/" element={<Home session={session} />}/>{allowedFeatures.flatMap(feature => feature.routes).map(route => <Route key={route.path} path={route.path} element={route.element} />)}<Route path="/ayuda" element={<section><h1>Ayuda</h1><div className="panel"><h2>¿Cómo usar el sistema?</h2><p>Seleccione un módulo, use la búsqueda para encontrar información y la acción principal para registrar datos.</p><p><kbd>Tab</kbd> recorre controles y enlaces.</p></div></section>}/><Route path="*" element={<section><h1>Página no encontrada</h1><Link className="button" to="/">Volver al inicio</Link></section>}/></Routes></main></div></>;
}

createRoot(document.getElementById('root')!).render(<React.StrictMode><BrowserRouter><App /></BrowserRouter></React.StrictMode>);
