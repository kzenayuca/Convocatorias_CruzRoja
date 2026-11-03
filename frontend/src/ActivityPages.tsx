import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, date, monthNow, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice } from './components';

export function ServicesPage() {
  const enrollments = useData<Row[]>('/enrollments', []); const attendance = useData<Row[]>('/attendance', []);
  const [month, setMonth] = useState(monthNow()); const hours = useData<Row>(`/attendance/hours?month=${month}`, {});
  return <><p className="eyebrow">Tu participación</p><h1>Mis servicios</h1><p className="lead">Inscripciones, asistencia y horas guardadas en tu cuenta.</p>
    <ErrorNotice message={enrollments.error || attendance.error || hours.error}/><label className="field">Mes<input type="month" value={month} onChange={e => setMonth(e.target.value)}/></label>
    <div className="metrics"><div className="metric-card"><b>{hours.data.hours ?? 0} h</b><span>Saldo del mes</span></div><div className="metric-card"><b>{hours.data.earned ?? 0} h</b><span>Horas reconocidas</span></div><div className="metric-card"><b>{hours.data.deductions ?? 0} h</b><span>Deducciones</span></div></div>
    <p>El saldo puede ser negativo. Las justificaciones aprobadas revierten el descuento sin otorgar horas no realizadas.</p><h2>Mis inscripciones</h2>
    {!enrollments.loading && !enrollments.data.length && <div className="empty">Todavía no tienes inscripciones. <Link className="link" to="/callouts">Buscar convocatoria →</Link></div>}
    {enrollments.data.map(e => <details className="card" key={e.id}><summary>{e.title} · {e.status}</summary><p>{date(e.start_at)} – {date(e.end_at)}</p><p>{e.comment}</p><p>Código para coordinar un reemplazo: {e.id}</p>{e.status === 'CONFIRMADA' && e.kind === 'SERVICIO' && <>
      <div className="toolbar"><Action path={`/enrollments/${e.id}/mark`} body={{ action: 'ENTRADA' }}>Marcar entrada</Action><Action path={`/enrollments/${e.id}/mark`} body={{ action: 'SALIDA' }}>Marcar salida</Action></div>
      <DataForm fields={[{ name: 'reason', label: 'Solicitud de revisión / registro incompleto', type: 'textarea' }]} label="Solicitar revisión" submit={v => api(`/enrollments/${e.id}/correction`, 'POST', v)}/>
    </>}</details>)}
    <h2>Asistencia y horas</h2>{attendance.data.map(a => <details className="card" key={a.id}><summary>{a.title} · {a.status} · {a.approved_hours ?? '—'} h</summary><p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p><p>{a.observations}</p><p>Justificación: {a.justification_status || 'Sin presentar'}</p>{a.status === 'AUSENTE' && !a.justification_status && <><p>Plazo ordinario: 48 h desde el final. Se requiere reemplazo aceptado antes del inicio. Las presentaciones tardías requieren revisión excepcional.</p><DataForm fields={[{ name: 'reason', label: 'Motivo de la justificación', type: 'textarea' }]} label="Presentar justificación" submit={v => api(`/attendance/${a.id}/justifications`, 'POST', v)}/></>}</details>)}
  </>;
}

export function JustificationsPage({ user }: { user: Session }) {
  const { data, error } = useData<Row[]>('/justifications', []);
  return <><p className="eyebrow">Asistencia</p><h1>Justificaciones</h1><ErrorNotice message={error}/>{!data.length && <div className="empty">No hay justificaciones.</div>}{data.map(j => <details className="card" key={j.id}><summary>{j.name} · {j.title} · {j.status} {j.late && '· Fuera de las 48 h'}</summary><p>{j.reason}</p><p>Presentada: {date(j.submitted_at)} · Reemplazos aceptados a tiempo: {j.replacements}</p><p>Resolución: {j.resolution_comment || 'Pendiente'}</p>{user.role === 'JEFATURA' && <DataForm fields={[{ name: 'decision', label: 'Resolución / revisión', options: ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo de la resolución', type: 'textarea' }]} submit={v => api(`/justifications/${j.id}/resolve`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/>}</details>)}</>;
}

export function NotificationsPage({ user }: { user: Session }) {
  const { data, error } = useData<Row[]>('/notifications', []); const [filter, setFilter] = useState('Todas');
  const filters = ['Todas', 'No leídas', 'COMUNICADO', 'INSCRIPCION', 'JUSTIFICACION'];
  return <><p className="eyebrow">Mantente al día</p><h1>Notificaciones</h1><p className="lead">Avisos importantes de tus convocatorias y servicios.</p><ErrorNotice message={error}/><div className="filters">{filters.map(f => <button className={`filter ${filter === f ? 'active' : ''}`} key={f} onClick={() => setFilter(f)}>{f}</button>)}</div>
    <div className="list">{data.filter(n => filter === 'Todas' || (filter === 'No leídas' ? !n.is_read : n.type === filter)).map(n => <article className={`notification ${n.is_read ? '' : 'unread'}`} key={n.id}><span className="notice-icon">🔔</span><div><h3>{n.type}</h3><p>{n.message}</p><Link className="link" to={n.link || '/notifications'} onClick={() => { void api(`/notifications/${n.id}/read`, 'POST').then(() => window.dispatchEvent(new Event('data-changed'))); }}>Ver detalle →</Link></div>{!n.is_read && <Action path={`/notifications/${n.id}/read`}>Marcar leída</Action>}</article>)}</div>
    {!data.length && <div className="empty">No tienes notificaciones.</div>}{user.role === 'JEFATURA' && <><h2>Publicar comunicado oficial</h2><DataForm fields={[{ name: 'title', label: 'Título', max: 150 }, { name: 'body', label: 'Mensaje', type: 'textarea', max: 5000 }, { name: 'audience', label: 'Audiencia', options: ['TODOS', 'VOLUNTARIO', 'RECLUTADOR', 'JEFATURA'] }]} label="Publicar comunicado" submit={v => api('/announcements', 'POST', v)}/></>}
  </>;
}

export function ReportsPage() {
  const [month, setMonth] = useState(monthNow()); const { data, error } = useData<Row[]>(`/reports?month=${month}`, []);
  return <><p className="eyebrow">Jefatura y reclutamiento</p><h1>Reporte de horas</h1><p className="lead">Actividad mensual según tus permisos. Incluye domingos y feriados.</p><label className="field">Mes<input type="month" value={month} onChange={e => setMonth(e.target.value)}/></label><ErrorNotice message={error}/>{data.map(r => <article className="list-item" key={r.id}><div><h2>{r.name}</h2><p>Reconocidas: {r.earned} h · Deducciones: {r.deductions} h · Pendientes: {r.pending}</p></div><span className="badge">Saldo: {r.hours} h</span></article>)}<button className="button" onClick={() => window.print()}>Imprimir / Guardar PDF</button></>;
}
