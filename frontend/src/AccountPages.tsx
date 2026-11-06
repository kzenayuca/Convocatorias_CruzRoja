import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, date, Session, Row, useData } from './api';
import { DataForm, ErrorNotice, Field } from './components';
import { HoursProgress } from './HoursProgress';

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
    {register ? submitted ? <p role="status">Solicitud registrada. Jefatura revisará y activará tu cuenta.</p> : <><p>Jefatura revisará tu solicitud y habilitaciones antes de activar el acceso.</p><DataForm fields={[...profileFields, { name: 'phone', label: 'Teléfono', type: 'tel', max: 30 }, { name: 'password', label: 'Contraseña (8 a 72 caracteres)', type: 'password', max: 72 }]} label="Solicitar cuenta" submit={async v => {
      const { phone, password, ...profile } = v; await api('/auth/register', 'POST', { profile, phone, password }); setSubmitted(true);
    }}/></> : <form onSubmit={login}><p>Ingresa para continuar con tus actividades de voluntariado.</p><label className="field">Correo electrónico<input type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required/></label><label className="field">Contraseña<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required/></label><ErrorNotice message={error}/><button className="button login-button" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button><details><summary>Cuentas de demostración</summary><p>voluntario@demo.pe · reclutador@demo.pe · jefatura@demo.pe<br/>Contraseña: convocatorias</p><p>Más perfiles: maria@demo.pe, diego@demo.pe y lucia@demo.pe.</p></details></form>}
    <button className="icon-button" onClick={() => setRegister(!register)}>{register ? 'Volver al inicio de sesión' : 'Solicitar una cuenta'}</button>
  </div></main>;
}
export function ProfilePage() {
  const { data: p, error } = useData<Row>('/profile', {});
  const [editing, setEditing] = useState(false); const photos = useData<Row[]>('/profile/photos', []);
  return <><div className="page-heading"><div><p className="eyebrow">Tu espacio personal</p><h1>Mi perfil</h1><p className="lead">Tus datos, formación y recuerdos del voluntariado.</p></div><button className="button secondary" onClick={() => setEditing(!editing)}>{editing ? 'Cerrar edición' : 'Modificar'}</button></div><ErrorNotice message={error || photos.error}/>{p.id && <>
    <section className="profile-hero"><span className="avatar large-avatar">{p.name.charAt(0)}</span><div><h2>{p.name} {p.surnames}</h2><p>{p.occupation || 'Voluntariado Cruz Roja'}</p><span className="badge">{p.role}</span> <span className="badge">{p.status}</span></div></section>
    <section className="section-block"><h2>Información personal</h2><dl className="info-grid"><div><dt>Correo electrónico</dt><dd>{p.email}</dd></div><div><dt>DNI</dt><dd>{p.document}</dd></div><div><dt>Teléfono</dt><dd>{p.phone || 'No registrado'}</dd></div><div><dt>Profesión u ocupación</dt><dd>{p.occupation || 'No registrada'}</dd></div></dl></section>
    {p.role === 'JEFATURA' ? <section className="section-block"><h2>Administración institucional</h2><p>Tu cuenta administra convocatorias, asistencia y horas de todos los voluntarios. No tiene meta personal, penalizaciones ni obligación de justificarse.</p><Link className="link" to="/reports">Consultar horas de los voluntarios →</Link></section> : <section className="section-block"><h2>Condición y habilitaciones</h2><dl className="info-grid"><div><dt>Condición</dt><dd>{p.graduated ? 'Graduado' : 'En formación'}</dd></div><div><dt>Radioperador</dt><dd>{p.radio_qualified ? 'Habilitado por jefatura' : 'Sin habilitación'}</dd></div><div><dt>Piloto</dt><dd>{p.pilot_qualified ? 'Habilitado por jefatura' : 'Sin habilitación'}</dd></div></dl><p>Ser graduado no otorga gestión. Los permisos de responsable solo corresponden al servicio al que jefatura te asigna.</p><Link className="link" to="/services">Ver mis servicios y horas →</Link></section>}
    {p.role !== 'JEFATURA' && <HoursProgress/>}
    {editing && <section className="section-block"><h2>Modificar información</h2><h3>Teléfono · cambio directo</h3><DataForm initial={p} fields={[{ name: 'phone', label: 'Teléfono', type: 'tel', max: 30 }]} label="Guardar teléfono" submit={v => api('/profile/phone', 'PUT', v)}/>
      <h3>Otros datos · requieren aprobación</h3><p>Mientras jefatura revisa la solicitud, se conservan tus datos actuales.</p><DataForm key={`${p.name}-${p.email}`} initial={p} fields={profileFields} label="Solicitar cambio" submit={v => api('/profile/changes', 'POST', v)}/>
    </section>}
    {!!photos.data.length && <section className="section-block"><h2>Mis fotos de equipo</h2><p className="muted">Servicios en los que participaste. Solo el responsable asignado y jefatura suben fotos.</p><div className="callout-grid">{photos.data.map(c => <article className="card" key={c.id}><h3>{c.title}</h3><p>{date(c.start_at)}</p><details className="photo-view"><summary>Ver foto</summary><img loading="lazy" src={`/api/v1/callouts/${c.id}/photo`} alt={`Equipo de ${c.title}`}/></details><Link className="link" to={`/callouts/${c.id}`}>Ver convocatoria →</Link></article>)}</div></section>}
  </>}</>;
}
export function AdministrationPage() {
  const settings = useData<Row>('/administration', {}); const codes = useData<Row[]>('/service-codes', []); const [type, setType] = useState('');
  const types = [...new Set(codes.data.map(c => c.service_type))];
  return <><p className="eyebrow">Jefatura</p><h1>Administración</h1><p className="lead">Requisitos institucionales e historial de códigos.</p><ErrorNotice message={settings.error || codes.error}/>
    <section className="section-block"><h2>Metas de horas por condición</h2><p>Dos metas generales de saldo acumulado: una para formación y otra para graduados. No son mensuales ni individuales. Cambiarlas no modifica asistencias, saldos o cierres anteriores, ni gradúa automáticamente.</p><dl className="info-grid"><div><dt>En formación</dt><dd>{settings.data.training_hours ? `${settings.data.training_hours} h` : 'Sin configurar'}</dd></div><div><dt>Graduados</dt><dd>{settings.data.graduate_hours ? `${settings.data.graduate_hours} h` : 'Sin configurar'}</dd></div></dl><DataForm key={`${settings.data.training_hours}-${settings.data.graduate_hours}`} fields={[{ name: 'trainingHours', label: 'Meta para voluntarios en formación (horas acumuladas)', type: 'number', min: 0.01, max: 100000, step: '0.01' }, { name: 'graduateHours', label: 'Meta para voluntarios graduados (horas acumuladas)', type: 'number', min: 0.01, max: 100000, step: '0.01' }]} initial={{ trainingHours: settings.data.training_hours, graduateHours: settings.data.graduate_hours }} label="Guardar metas generales" submit={v => api('/administration', 'PUT', v)}/></section>
    <section className="section-block"><h2>Códigos recientes por tipo</h2><p>Se asignan manualmente en cada convocatoria. Un código usado queda reservado y no se reutiliza en otro servicio. Los prefijos automáticos quedan como trabajo futuro.</p><label className="field">Tipo de servicio<select value={type} onChange={e => setType(e.target.value)}><option value="">Todos los tipos</option>{types.map(t => <option key={t}>{t}</option>)}</select></label>
      {codes.data.filter(c => !type || c.service_type === type).map(c => <article className="preview-row" key={c.id}><Link className="link" to={`/callouts/${c.callout_id}`}>{c.service_code}</Link><p>{c.service_type} · {date(c.assigned_at)}</p><small>{c.assigned_name || 'Registro migrado'}</small></article>)}
    </section>
  </>;
}
export function UsersPage() {
  const { data, error } = useData<Row[]>('/users', []); const changes = useData<Row[]>('/users/changes', []); const [query, setQuery] = useState('');
  return <><p className="eyebrow">Jefatura</p><h1>Usuarios y solicitudes</h1><ErrorNotice message={error || changes.error}/>
    <input aria-label="Buscar usuarios" placeholder="Nombre, DNI, rol o estado…" value={query} onChange={e => setQuery(e.target.value)}/>
    {changes.data.map(p => <section className="panel" key={p.user_id}><h2>Cambio de perfil</h2><p>{p.name} {p.surnames} · {p.email} · DNI {p.document} · {p.occupation}</p><DataForm fields={[{ name: 'decision', label: 'Decisión', options: ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo' }]} submit={v => api(`/users/${p.user_id}/changes`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/></section>)}
    <div className="list">{data.filter(p => `${p.name} ${p.surnames} ${p.email} ${p.document} ${p.role} ${p.status}`.toLowerCase().includes(query.toLowerCase())).map(p => <details className="card" key={`${p.id}-${p.status}-${p.role}`}><summary>{p.name} {p.surnames} · {p.status} · {p.role}</summary><p>{p.email} · DNI {p.document} · {p.phone} · {p.occupation}</p>
      <DataForm initial={{ role: p.role, graduated: p.graduated ? 'SI' : 'NO', radioQualified: p.radio_qualified ? 'SI' : 'NO', pilotQualified: p.pilot_qualified ? 'SI' : 'NO', status: p.status, comment: p.review_comment }} fields={[
        { name: 'role', label: 'Rol de acceso (responsable se asigna por servicio)', options: ['VOLUNTARIO', 'JEFATURA'] },
        { name: 'graduated', label: 'Graduado', options: ['NO', 'SI'] }, { name: 'status', label: 'Estado', options: ['PENDIENTE', 'ACTIVO', 'RECHAZADO', 'INACTIVO', 'SUSPENDIDO'] }, { name: 'comment', label: 'Motivo de la decisión' },
        { name: 'radioQualified', label: 'Habilitación de radioperador verificada', options: ['NO', 'SI'] }, { name: 'pilotQualified', label: 'Habilitación de piloto verificada', options: ['NO', 'SI'] },
      ]} submit={v => api(`/users/${p.id}`, 'PUT', { ...v, graduated: v.graduated === 'SI', radioQualified: v.radioQualified === 'SI', pilotQualified: v.pilotQualified === 'SI' })}/>
    </details>)}</div></>;
}
