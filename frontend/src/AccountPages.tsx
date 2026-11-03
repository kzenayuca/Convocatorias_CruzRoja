import React, { useState } from 'react';
import { api, Session, Row, useData } from './api';
import { DataForm, ErrorNotice, Field } from './components';

const profileFields: Field[] = [
  { name: 'name', label: 'Nombres', max: 120 }, { name: 'surnames', label: 'Apellidos', max: 160 },
  { name: 'email', label: 'Correo electrónico', type: 'email', max: 180 }, { name: 'document', label: 'DNI (8 dígitos)', max: 8 },
  { name: 'occupation', label: 'Profesión u ocupación', max: 160 },
];
export function Login({ onLogin }: { onLogin: (session: Session) => void }) {
  const [register, setRegister] = useState(false); const [submitted, setSubmitted] = useState(false);
  const [email, setEmail] = useState('voluntario@demo.pe'); const [password, setPassword] = useState('convocatorias');
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  async function login(event: React.FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    try { onLogin(await api('/auth/login', 'POST', { email, password })); } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  return <main className="login-page"><div className="login-card"><div className="brand-mark">+</div><p className="eyebrow">Cruz Roja · Arequipa</p><h1>{register ? 'Solicita tu cuenta' : 'Qué bueno tenerte aquí'}</h1>
    {register ? submitted ? <p role="status">Solicitud registrada. Jefatura revisará y activará tu cuenta.</p> : <><p>Jefatura asignará tu rol y nivel antes de habilitar el acceso.</p><DataForm fields={[...profileFields, { name: 'phone', label: 'Teléfono', type: 'tel', max: 30 }, { name: 'password', label: 'Contraseña (8 a 72 caracteres)', type: 'password', max: 72 }]} label="Solicitar cuenta" submit={async v => {
      const { phone, password, ...profile } = v; await api('/auth/register', 'POST', { profile, phone, password }); setSubmitted(true);
    }}/></> : <form onSubmit={login}><p>Ingresa para continuar con tus actividades de voluntariado.</p><label className="field">Correo electrónico<input type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required/></label><label className="field">Contraseña<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required/></label><ErrorNotice message={error}/><button className="button login-button" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button><details><summary>Cuentas de demostración</summary><p>voluntario@demo.pe · reclutador@demo.pe · jefatura@demo.pe<br/>Contraseña: convocatorias</p><p>Más perfiles: maria@demo.pe, diego@demo.pe y lucia@demo.pe.</p></details></form>}
    <button className="icon-button" onClick={() => setRegister(!register)}>{register ? 'Volver al inicio de sesión' : 'Solicitar una cuenta'}</button>
  </div></main>;
}
export function ProfilePage() {
  const { data: p, error } = useData<Row>('/profile', {});
  return <><p className="eyebrow">Tus datos</p><h1>Mi perfil</h1><ErrorNotice message={error}/>{p.id && <>
    <div className="panel"><p>{p.name} {p.surnames} · {p.role} · {p.training_level} · {p.status}</p></div>
    <h2>Teléfono · cambio directo</h2><DataForm initial={p} fields={[{ name: 'phone', label: 'Teléfono', type: 'tel', max: 30 }]} submit={v => api('/profile/phone', 'PUT', v)}/>
    <h2>Otros datos · requieren aprobación</h2><p>Mientras jefatura revisa la solicitud, se conservan tus datos actuales.</p><DataForm key={`${p.name}-${p.email}`} initial={p} fields={profileFields} label="Solicitar cambio" submit={v => api('/profile/changes', 'POST', v)}/>
  </>}</>;
}
export function UsersPage() {
  const { data, error } = useData<Row[]>('/users', []); const changes = useData<Row[]>('/users/changes', []); const [query, setQuery] = useState('');
  return <><p className="eyebrow">Jefatura</p><h1>Usuarios y solicitudes</h1><ErrorNotice message={error || changes.error}/>
    <input aria-label="Buscar usuarios" placeholder="Nombre, DNI, rol o estado…" value={query} onChange={e => setQuery(e.target.value)}/>
    {changes.data.map(p => <section className="panel" key={p.user_id}><h2>Cambio de perfil</h2><p>{p.name} {p.surnames} · {p.email} · DNI {p.document} · {p.occupation}</p><DataForm fields={[{ name: 'decision', label: 'Decisión', options: ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo' }]} submit={v => api(`/users/${p.user_id}/changes`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/></section>)}
    <div className="list">{data.filter(p => `${p.name} ${p.email} ${p.document} ${p.role} ${p.status}`.toLowerCase().includes(query.toLowerCase())).map(p => <details className="card" key={`${p.id}-${p.status}-${p.role}-${p.training_level}`}><summary>{p.name} {p.surnames} · {p.status} · {p.role}</summary><p>{p.email} · DNI {p.document} · {p.phone} · {p.occupation}</p>
      <DataForm initial={{ role: p.role, level: p.training_level, graduated: p.graduated ? 'SI' : 'NO', status: p.status, comment: p.review_comment }} fields={[
        { name: 'role', label: 'Rol', options: ['VOLUNTARIO', 'RECLUTADOR', 'JEFATURA'] }, { name: 'level', label: 'Formación', options: ['BASICO', 'INTERMEDIO', 'AVANZADO'] },
        { name: 'graduated', label: 'Graduado', options: ['NO', 'SI'] }, { name: 'status', label: 'Estado', options: ['PENDIENTE', 'ACTIVO', 'RECHAZADO', 'INACTIVO', 'SUSPENDIDO'] }, { name: 'comment', label: 'Motivo de la decisión' },
      ]} submit={v => api(`/users/${p.id}`, 'PUT', { ...v, graduated: v.graduated === 'SI' })}/>
    </details>)}</div></>;
}
