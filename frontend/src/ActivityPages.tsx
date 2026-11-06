import React, { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, date, delay, monthNow, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice, PdfDownload, useRequestFocus } from './components';
import { positionNames } from './ServicePages';
import { HoursProgress } from './HoursProgress';
import { JustificationForm, ReplacementResolution } from './JustificationForms';
import { AnnualReport } from './AnnualPages';

export function ServicesPage() {
  const enrollments = useData<Row[]>('/enrollments', []); const attendance = useData<Row[]>('/attendance', []);
  const [params] = useSearchParams(); useRequestFocus(attendance.data); useRequestFocus(enrollments.data);
  const [month, setMonth] = useState(monthNow()); const hours = useData<Row>(`/attendance/hours?month=${month}`, {});
  useEffect(() => { const value = params.get('month'); if (value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)) setMonth(value); }, [params]);
  return <><p className="eyebrow">Tu participación</p><h1>Mis servicios</h1><p className="lead">Inscripciones, asistencia y horas guardadas en tu cuenta.</p>
    <ErrorNotice message={enrollments.error || attendance.error || hours.error}/>{params.get('review') && <FocusedAttendanceReview id={params.get('review')!}/>}<label className="field">Mes<input type="month" value={month} onChange={e => setMonth(e.target.value)}/></label>
    <div className="metrics"><div className="metric-card"><b>{hours.data.hours ?? 0} h</b><span>Saldo del mes</span></div><div className="metric-card"><b>{hours.data.earned ?? 0} h</b><span>Horas reconocidas</span></div><div className="metric-card"><b>{hours.data.deductions ?? 0} h</b><span>Deducciones</span></div></div>
    {hours.data.closed && <p className="participation-note">Mes aprobado. Saldo original: {hours.data.original_hours} h · Ajustes posteriores: {hours.data.adjustment} h. Se conserva el cierre original.</p>}
    <div className="toolbar"><PdfDownload path={`/attendance/hours/pdf?month=${month}`} filename={`mis-horas-${month}.pdf`}/></div><AnnualReport/><HoursProgress/>
    <p>El saldo puede ser negativo. Las justificaciones aprobadas revierten el descuento sin otorgar horas no realizadas.</p><h2>Mis inscripciones</h2>
    {!enrollments.loading && !enrollments.data.length && <div className="empty">Todavía no tienes inscripciones. <Link className="link" to="/callouts">Buscar convocatoria →</Link></div>}
    {enrollments.data.map(e => <details className="card" data-focus={`enrollment:${e.id}`} key={e.id}><summary>{e.title} · {positionNames[e.position]} · {e.status === 'CONFIRMADA' ? 'ACEPTADA' : e.status}</summary><p>{e.service_code} · {date(e.start_at)} – {date(e.end_at)}</p><p>Participante: {e.full_name}</p><p>{e.comment}</p>
      {e.replacement_request_id && <p><Link className="link" to={`/justifications?request=${e.replacement_request_id}`}>Consultar solicitud de reemplazo →</Link></p>}
      {e.status === 'CONFIRMADA' && e.kind === 'SERVICIO' && new Date(e.start_at).getTime() > Date.now() && <><p>Solicita el reemplazo con al menos 48 horas de anticipación. Solo Jefatura lo asigna y resuelve; mientras tanto sigues inscrito.</p>{e.cancellation_requested_at ? <p role="status">Solicitud pendiente de gestión: {e.cancellation_reason}</p> : new Date(e.start_at).getTime() - Date.now() >= 48 * 3600000 ? <details><summary>Solicitar cancelación con reemplazo</summary><JustificationForm replacement path={`/enrollments/${e.id}/cancel-request`}/></details> : <p>El plazo para solicitar un reemplazo terminó.</p>}</>}{e.status === 'CONFIRMADA' && e.kind === 'SERVICIO' && <>
      <div className="toolbar"><Link className="link" to={`/callouts/${e.callout_id}`}>Ver servicio →</Link>{!e.month_closed && e.opened_at && !e.closed_at && Date.now() >= new Date(e.start_at).getTime() - 3600000 && Date.now() <= new Date(e.end_at).getTime() + 3600000 && <><Action path={`/enrollments/${e.id}/mark`} body={{ action: 'ENTRADA' }}>Marcar entrada</Action><Action path={`/enrollments/${e.id}/mark`} body={{ action: 'SALIDA' }}>Marcar salida</Action></>}</div>
      {e.month_closed ? <p>Este mes ya fue aprobado y no admite cambios.</p> : new Date(e.start_at).getTime() <= Date.now() && <details><summary>Solicitar revisión de asistencia</summary><DataForm fields={[{ name: 'reason', label: 'Solicitud de revisión / registro incompleto', type: 'textarea' }]} label="Solicitar revisión" submit={v => api(`/enrollments/${e.id}/correction`, 'POST', v)}/></details>}
    </>}</details>)}
    <h2>Asistencia y horas</h2><p>Tolerancia de 10 minutos; más de 10:00 min genera descuento de dos veces el retraso. La justificación aprobada elimina ese descuento, no acredita tiempo no trabajado.</p>
    {attendance.data.map(a => <details className="card" data-focus={`attendance:${a.id}`} key={a.id}>
      <summary>{a.title} · {a.status} · {a.approved_hours ?? '—'} h</summary>
      <p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p>
      <p>Retraso: {delay(a.late_minutes)} · Deducción vigente: {a.deduction} h</p>
      {a.policy_version < 2 && <p>Registro histórico: conserva el cálculo anterior hasta revisión administrativa.</p>}
      <p>{a.observations}</p><p>Justificación: {a.justification_status || 'Sin presentar'}</p>
      {!!a.review_requests?.length && <><h3>Mis solicitudes de revisión</h3>{a.review_requests.map((r: Row) => <details key={r.id} data-focus={`review:${r.id}`}><summary>{r.status} · {date(r.submitted_at)}</summary><p>{r.reason}</p><p>{r.resolution_comment || 'Pendiente de validación'} · {r.resolved_name} {r.resolved_at && date(r.resolved_at)}</p></details>)}</>}
      {a.month_closed && <p>Mes aprobado: la asistencia queda congelada. Una justificación resuelta genera un ajuste separado.</p>}
      {(a.status === 'AUSENTE' || a.policy_version >= 2 && a.late_minutes > 10) && !a.justification_status && <>
        <p>Ausencia injustificada: descuento del doble de las horas programadas. Ausencias y tardanzas pueden justificarse hasta 72 horas después del final del servicio. Solo Jefatura resuelve.</p>
        {Date.now() >= new Date(a.end_at).getTime() && Date.now() <= new Date(a.end_at).getTime() + 72 * 3600000 ? <JustificationForm path={`/attendance/${a.id}/justifications`}/> : <p>Fuera del plazo para presentar la justificación.</p>}
      </>}
    </details>)}
  </>;
}
function FocusedAttendanceReview({ id }: { id: string }) {
  const { data: r, error } = useData<Row>(`/attendance-reviews/${id}`, {}); useRequestFocus(r);
  return <section><ErrorNotice message={error}/>{r.id && <details className="card" data-focus={`review:${r.id}`}><summary>Revisión de asistencia · {r.title} · {r.status}</summary><p>{r.reason}</p><p>Resolución: {r.resolution_comment || 'Pendiente de validación'}</p>{r.resolved_at && <p>{r.resolved_name} · {date(r.resolved_at)}</p>}</details>}</section>;
}

export function JustificationsPage({ user }: { user: Session }) {
  const [params] = useSearchParams(); const selected = params.get('request');
  return selected ? <JustificationDetail key={selected} id={selected} user={user}/> : <JustificationsList user={user}/>;
}
function JustificationDetail({ id, user }: { id: string; user: Session }) {
  const { data, error, loading } = useData<Row>(`/justifications/${id}`, {});
  useRequestFocus(data);
  return <><Link className="link" to="/justifications">← Volver a mis justificaciones{user.role === 'JEFATURA' && ' y gestión general'}</Link><h1>Solicitud de justificación</h1><ErrorNotice message={error}/>{loading && !data.id ? <p>Cargando solicitud…</p> : data.id && <JustificationCard j={data} user={user}/>}</>;
}
function JustificationsList({ user }: { user: Session }) {
  const { data, error } = useData<Row[]>('/justifications', []);
  const chief = user.role === 'JEFATURA';
  const [status, setStatus] = useState(chief ? 'PENDIENTE' : 'TODAS'); const [query, setQuery] = useState(''); const [month, setMonth] = useState('');
  const filtered = data.filter(j => (status === 'TODAS' || j.status === status) && (!month || String(j.start_at).startsWith(month)) && `${j.name} ${j.surnames} ${j.title}`.toLocaleLowerCase().includes(query.toLocaleLowerCase()));
  return <><p className="eyebrow">{user.role === 'JEFATURA' ? 'Gestión de incidencias de todos los voluntarios' : 'Asistencia'}</p><h1>Justificaciones</h1>
    <p className="lead">{user.role === 'JEFATURA' ? 'Revisa pendientes, consulta decisiones y acepta o rechaza con un motivo. Jefatura no necesita justificarse ni recibe penalizaciones.' : 'Consulta el estado de tus solicitudes y sus resoluciones.'}</p><ErrorNotice message={error}/>
    <div className="filters">{['PENDIENTE', 'APROBADA', 'RECHAZADA'].map(s => <button className={`filter ${status === s ? 'active' : ''}`} key={s} onClick={() => setStatus(s)}>{s} · {data.filter(j => j.status === s).length}</button>)}</div>
    <div className="toolbar"><label className="field">Estado<select aria-label="Estado de justificación" value={status} onChange={e => setStatus(e.target.value)}>{['TODAS', 'PENDIENTE', 'APROBADA', 'RECHAZADA'].map(s => <option key={s}>{s}</option>)}</select></label>{chief && <><label className="field">Persona o servicio<input aria-label="Buscar justificaciones" value={query} onChange={e => setQuery(e.target.value)}/></label><label className="field">Mes del servicio<input type="month" value={month} onChange={e => setMonth(e.target.value)}/></label></>}<button className="button secondary" onClick={() => { setStatus('TODAS'); setQuery(''); setMonth(''); }}>Limpiar filtros</button></div>
    <p>{filtered.length} justificaciones</p>{!filtered.length && <div className="empty">No hay justificaciones con esos filtros.</div>}{filtered.map(j => <JustificationCard key={j.id} j={j} user={user}/>)}</>;
}
function JustificationCard({ j, user }: { j: Row; user: Session }) {
  return <details className="card" data-focus={`request:${j.id}`}><summary>{user.role === 'JEFATURA' || j.operational_only ? `${j.name} ${j.surnames} · ` : ''}{j.kind} · {j.title} · {j.status}</summary>
    {j.operational_only ? <p>Resumen operativo para el responsable. Solo Jefatura puede consultar el sustento y asignar o resolver el reemplazo.</p> : <><p className="multiline">{j.reason}</p>{j.has_evidence && <a className="link" href={`/api/v1/justifications/${j.id}/evidence`} target="_blank" rel="noreferrer">Ver evidencia privada →</a>}</>}
    <p>Servicio: {date(j.start_at)} – {date(j.end_at)} · Presentada: {date(j.submitted_at)}</p>
    {j.kind === 'REEMPLAZO' && <><p>Reemplazo propuesto: <b>{j.proposed_replacement_name || 'Solicita búsqueda y asignación de Jefatura'}</b></p><p>Reemplazo asignado por Jefatura: <b>{j.assigned_replacement_name || 'Sin asignar'}</b></p></>}
    {!j.operational_only && <><p>Resolución: {j.resolution_comment || 'Pendiente de Jefatura'}</p>{j.resolved_at && <p>Decidió: {j.resolved_name} · {date(j.resolved_at)}</p>}{j.month_closed && j.kind !== 'REEMPLAZO' && <p>Mes cerrado: cualquier cambio de penalización se registra como ajuste, sin borrar el cierre.</p>}
    {!!j.history?.length && <details><summary>Historial de decisiones</summary>{j.history.map((h: Row) => <p key={h.id}>{date(h.resolved_at)} · {h.resolved_name} · {h.previous_status} → {h.status} · {h.comment}{j.kind !== 'REEMPLAZO' && ` · Deducción: ${h.deduction_before} → ${h.deduction_after} h`}</p>)}</details>}</>}
    {user.role === 'JEFATURA' && (j.kind === 'REEMPLAZO' ? j.status === 'PENDIENTE' && <ReplacementResolution request={j}/> : <details data-primary-form={j.status === 'PENDIENTE' || undefined}><summary>{j.status === 'PENDIENTE' ? 'Resolver justificación' : 'Revisar decisión (queda auditado)'}</summary><DataForm fields={[{ name: 'decision', label: 'Decisión', options: ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo obligatorio de la resolución', type: 'textarea' }]} label="Registrar decisión" submit={v => api(`/justifications/${j.id}/resolve`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/></details>)}
  </details>;
}

export function NotificationsPage({ user }: { user: Session }) {
  const { data, error } = useData<Row[]>('/notifications', []); const [filter, setFilter] = useState('Todas');
  const filters = ['Todas', 'No leídas', 'CUENTA', 'PERFIL', 'GRADUACION', 'REVISION', 'ASISTENCIA', 'INSCRIPCION', 'JUSTIFICACION', 'REEMPLAZO', 'REPORTE', 'CICLO_ANUAL', 'COMUNICADO'];
  return <><p className="eyebrow">Mantente al día</p><h1>Notificaciones</h1><p className="lead">Avisos importantes de tus convocatorias y servicios.</p><ErrorNotice message={error}/><div className="filters">{filters.map(f => <button className={`filter ${filter === f ? 'active' : ''}`} key={f} onClick={() => setFilter(f)}>{f}</button>)}</div>
    <div className="list">{data.filter(n => filter === 'Todas' || (filter === 'No leídas' ? !n.is_read : n.type === filter)).map(n => <article className={`notification ${n.is_read ? '' : 'unread'}`} key={n.id}><span className="notice-icon">🔔</span><div><h3>{n.type}</h3><p>{n.message}</p><Link className="link" to={n.link || '/notifications'} onClick={() => { void api(`/notifications/${n.id}/read`, 'POST').then(() => window.dispatchEvent(new Event('data-changed'))); }}>Ver detalle →</Link></div>{!n.is_read && <Action path={`/notifications/${n.id}/read`}>Marcar leída</Action>}</article>)}</div>
    {!data.length && <div className="empty">No tienes notificaciones.</div>}{user.role === 'JEFATURA' && <details className="section-block"><summary>Publicar comunicado oficial</summary><DataForm fields={[{ name: 'title', label: 'Título', max: 150 }, { name: 'body', label: 'Mensaje', type: 'textarea', max: 5000 }, { name: 'audience', label: 'Audiencia', options: ['TODOS', 'VOLUNTARIO', 'JEFATURA'] }]} label="Publicar comunicado" submit={v => api('/announcements', 'POST', v)}/></details>}
  </>;
}

export function ReportsPage() {
  const [params] = useSearchParams();
  const [mode, setMode] = useState('MENSUAL');
  const [month, setMonth] = useState(() => { const now = new Date(); now.setUTCDate(1); now.setUTCMonth(now.getUTCMonth() - 1); return now.toISOString().slice(0, 7); });
  const { data, error } = useData<Row[]>(`/reports?month=${month}`, []); const status = useData<Row>(`/reports/status?month=${month}`, {});
  const [confirm, setConfirm] = useState(false);
  useEffect(() => { const value = params.get('month'); if (value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value)) { setMonth(value); setMode('MENSUAL'); setConfirm(false); } }, [params]);
  return <><p className="eyebrow">Jefatura</p><h1>Reporte de horas</h1><p className="lead">Revisa el mes y publícalo entre los días 1 y 3 del siguiente. Se conserva el cierre original; las justificaciones posteriores se reflejan como ajustes auditados.</p>
    <div className="filters"><button className={`filter ${mode === 'MENSUAL' ? 'active' : ''}`} onClick={() => setMode('MENSUAL')}>Reporte mensual</button><button className={`filter ${mode === 'ANUAL' ? 'active' : ''}`} onClick={() => setMode('ANUAL')}>Reporte anual</button></div>{mode === 'ANUAL' ? <AnnualReport general/> : <><label className="field">Mes<input type="month" value={month} onChange={e => { if (e.target.value) { setMonth(e.target.value); setConfirm(false); } }}/></label><ErrorNotice message={error || status.error}/>
    <section className="soft-panel"><p><b>{status.data.status}</b> · Fecha prevista de publicación: {status.data.due}</p>{status.data.overdue && <p role="status">Publicación pendiente fuera del plazo previsto. Jefatura aún puede aprobarla; quedará registrada la fecha real.</p>}
      {status.data.status !== 'APROBADO' && month < monthNow() && <><p>Los registros incompletos se publican como pendientes. El cierre congela las asistencias; las decisiones posteriores sobre justificaciones quedan como ajustes separados.</p><label className="requirement"><input type="checkbox" checked={confirm} onChange={e => setConfirm(e.target.checked)}/>Revisé los resultados y entiendo que se conservará este cierre.</label>{confirm && <Action path={`/reports/approve?month=${month}`}>Aprobar y publicar mes</Action>}</>}
      {status.data.approved_at && <p>Aprobado: {date(status.data.approved_at)}</p>}
    </section><div className="document-preview"><div className="page-heading"><h2>Vista previa · {month}</h2><PdfDownload path={`/reports/pdf?month=${month}`} filename={`reporte-${month}.pdf`}/></div>
      {data.map(r => <article className="list-item" key={r.id}><div><h3>{r.name}</h3><p>Reconocidas: {r.earned} h · Deducciones: {r.deductions} h · Pendientes: {r.pending}</p>{r.closed && <p>Cierre original: {r.original_hours} h · Ajustes: {r.adjustment} h</p>}</div><span className="badge">Saldo: {r.hours} h</span></article>)}
    </div></>}</>;
}
