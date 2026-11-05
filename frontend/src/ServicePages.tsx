import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, changed, date, delay, localInput, manager, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice, Field } from './components';

export const positionNames: Record<string, string> = { RESPONSABLE: 'Responsable', SOCORRISTA: 'Socorrista', RADIOPERADOR: 'Radioperador', PILOTO: 'Piloto' };
const statuses: Record<string, string> = { BORRADOR: 'Borrador', ABIERTA: 'Abierta', CERRADA: 'Completa · confirmada', CANCELADA: 'Cancelada', FINALIZADA: 'Finalizada', PUBLICADA: 'Publicada (histórico)', CONFIRMADA: 'Confirmada (histórico)' };
const callFields: Field[] = [
  { name: 'code', label: 'Código único del servicio (incluye el año)', max: 40 }, { name: 'title', label: 'Título', max: 150 },
  { name: 'description', label: 'Descripción', type: 'textarea', max: 5000 },
  { name: 'kind', label: 'Convocatoria', options: ['SERVICIO', 'INGRESO'] },
  { name: 'serviceType', label: 'Tipo de servicio', max: 100 }, { name: 'area', label: 'Área', max: 120 },
  { name: 'start', label: 'Inicio (hora de Perú)', type: 'datetime-local' }, { name: 'end', label: 'Fin (hora de Perú)', type: 'datetime-local' },
  { name: 'deadline', label: 'Completar puestos antes de (hora de Perú)', type: 'datetime-local' },
  { name: 'location', label: 'Lugar' }, { name: 'contact', label: 'Contacto de emergencia' },
  { name: 'requirements', label: 'Requisitos (uno por línea)', type: 'textarea', max: 5000 },
  { name: 'level', label: 'Nivel mínimo', options: ['BASICO', 'INTERMEDIO', 'AVANZADO'] },
  { name: 'socorristaSlots', label: 'Puestos de socorrista / vacantes de ingreso', type: 'number', min: 1, max: 10000 },
  { name: 'radioSlots', label: 'Radioperador (0 = no aplica, 1 = obligatorio)', type: 'number', min: 0, max: 1 },
  { name: 'pilotSlots', label: 'Piloto (0 = no aplica, 1 = obligatorio)', type: 'number', min: 0, max: 1 },
];
function Positions({ positions }: { positions: Row[] }) {
  return <div className="grid">{positions.map(p => <article className="card" key={p.position}><p className="eyebrow">{positionNames[p.position]}</p><div className="stat">{p.required ? `${p.filled}/${p.required}` : '—'}</div><p>{p.required ? Math.max(0, p.required - p.filled) ? `Faltan ${Math.max(0, p.required - p.filled)}` : 'Puesto cubierto' : 'No aplica'}</p></article>)}</div>;
}
export function CalloutsPage({ user, history = false }: { user: Session; history?: boolean }) {
  const { data, error, loading } = useData<Row[]>(`/callouts?history=${history}`, []);
  const [query, setQuery] = useState(''); const [create, setCreate] = useState(false); const navigate = useNavigate();
  const filtered = data.filter(c => `${c.service_code} ${c.title} ${c.description} ${c.location} ${statuses[c.status]} ${c.minimum_level}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="page-heading"><div><p className="eyebrow">{history ? 'Servicios anteriores' : 'Encuentra tu próxima actividad'}</p><h1>{history ? 'Historial' : 'Convocatorias'}</h1><p className="lead">{history ? 'Participaciones y servicios según tus permisos.' : 'Puestos disponibles según tu formación y sin cruces de horario.'}</p></div>{manager(user) && !history && <button className="button" onClick={() => setCreate(!create)}>Nueva convocatoria</button>}</div>
    <ErrorNotice message={error}/>{create && <><p>Un servicio requiere un responsable graduado y todos los puestos indicados. Las horas programadas se calculan a partir del horario. En ingreso no se solicita responsable, piloto ni radioperador.</p><DataForm fields={callFields} initial={{ socorristaSlots: 3, radioSlots: 0, pilotSlots: 0, area: 'Voluntariado', serviceType: 'Apoyo Salud' }} label="Crear borrador" submit={async v => { const result = await api('/callouts', 'POST', v); navigate(`/callouts/${result.id}`); }}/></>}
    <div className="toolbar"><input aria-label="Buscar convocatorias" placeholder="Busca por código, actividad, lugar o estado…" value={query} onChange={e => setQuery(e.target.value)} list="callout-suggestions"/><datalist id="callout-suggestions">{filtered.slice(0, 4).map(c => <option key={c.id} value={c.title}/>)}</datalist><button className="button secondary" onClick={() => setQuery('')}>Limpiar</button></div>
    <div className="list">{filtered.map(c => <article className="list-item" key={c.id}><div><span className={`badge ${c.status === 'CANCELADA' ? 'red' : ''}`}>{statuses[c.status] || c.status}</span><h2><Link to={`/callouts/${c.id}`}>{c.title}</Link></h2><p>{c.service_code} · {c.service_type} · 📍 {c.location}</p><p>🗓 {date(c.start_at)} · {Number(c.planned_hours).toLocaleString('es-PE')} h</p><small>{c.positions.filter((p: Row) => p.required > 0).map((p: Row) => `${positionNames[p.position]} ${p.filled}/${p.required}`).join(' · ')}</small></div><Link className="button" to={`/callouts/${c.id}`}>Ver detalle</Link></article>)}</div>
    {!loading && !filtered.length && <div className="empty">No hay convocatorias para mostrar.</div>}
  </>;
}
export function DetailPage({ user }: { user: Session }) {
  const { id } = useParams(); const { data: c, error, loading } = useData<Row>(`/callouts/${id}`, {});
  const [checked, setChecked] = useState<string[]>([]); const [edit, setEdit] = useState(false);
  useEffect(() => { setChecked([]); setEdit(false); }, [id, c.requirements]);
  if (loading && !c.id) return <p>Cargando convocatoria…</p>;
  if (error) return <ErrorNotice message={error}/>;
  const requirements = String(c.requirements ?? '').split('\n').filter(Boolean);
  const future = new Date(c.start_at).getTime() > Date.now();
  const available = ['ABIERTA', 'CERRADA'].includes(c.status) && future;
  const editable = c.can_edit && future && ['BORRADOR', 'ABIERTA', 'CERRADA'].includes(c.status);
  const positionOptions = (c.eligible_positions ?? []).map((p: string) => ({ value: p, label: `${positionNames[p]} · ${Math.max(0, (c.positions.find((s: Row) => s.position === p)?.required ?? 0) - (c.positions.find((s: Row) => s.position === p)?.filled ?? 0))} disponibles` }));
  return <><Link className="link" to="/callouts">← Volver a la lista</Link><div className="page-heading"><div><p className="eyebrow">{c.service_code} · {c.kind} · {c.area}</p><h1>{c.title}</h1><p className="lead">{c.description}</p></div><span className="badge">{statuses[c.status] || c.status}</span></div>
    <h2>Vacantes disponibles</h2><Positions positions={c.positions ?? []}/>
    <section className="panel"><p>{c.service_type} · 📍 {c.location}</p><p>Inicio: {date(c.start_at)} · Fin: {date(c.end_at)}</p><p>Horas programadas: <b>{Number(c.planned_hours).toLocaleString('es-PE')} h</b> · Nivel: {c.minimum_level}</p><p>Completar puestos antes de: {date(c.deadline)}</p><p>Responsable: {c.responsible_name || 'Puesto pendiente'}</p><p>Contacto: {c.contact || 'Consultar al coordinador'}</p><p>Observaciones: {c.observations || 'Sin observaciones'}</p></section>
    {available && positionOptions.length > 0 && <section className="card"><h2>Verificación de requisitos</h2><p>La inscripción normal se acepta inmediatamente si cumples las condiciones. No hay lista de espera. Para reemplazar, el titular debe haber solicitado cancelación y el encargado debe aprobarte.</p>{requirements.map((r, i) => <label className="requirement" key={i}><input type="checkbox" checked={checked.includes(r)} onChange={e => setChecked(old => e.target.checked ? [...old, r] : old.filter(x => x !== r))}/>{r}</label>)}
      {checked.length === requirements.length ? <DataForm fields={[{ name: 'position', label: 'Puesto', options: positionOptions }, { name: 'replacementFor', label: 'Código de inscripción a reemplazar (solo para reemplazo)', optional: true }]} label="Inscribirme" submit={v => api(`/callouts/${id}/enrollments`, 'POST', { requirementsAccepted: true, position: v.position, replacementFor: v.replacementFor || null })}/> : <p>Debes marcar todos los requisitos para inscribirte.</p>}
      <Link className="link" to="/services">Consultar mi inscripción →</Link>
    </section>}
    {editable && <><h2>Gestión de convocatoria</h2><div className="toolbar">{c.status === 'BORRADOR' && <Action path={`/callouts/${id}/status`} body={{ status: 'ABIERTA' }}>Publicar</Action>}<button className="button secondary" onClick={() => setEdit(!edit)}>Editar datos</button><Action path={`/callouts/${id}/status`} body={{ status: 'CANCELADA' }}>Cancelar convocatoria</Action></div>
      {edit && <DataForm fields={callFields} initial={{ ...c, code: c.service_code, serviceType: c.service_type, start: localInput(c.start_at), end: localInput(c.end_at), deadline: localInput(c.deadline), level: c.minimum_level, socorristaSlots: c.socorrista_slots, radioSlots: c.radio_slots, pilotSlots: c.pilot_slots }} submit={v => api(`/callouts/${id}`, 'PUT', v)}/>}
    </>}
    {c.can_view_team && <TeamPage id={id!} user={user} callout={c}/>}
  </>;
}
function TeamPage({ id, user, callout: c }: { id: string; user: Session; callout: Row }) {
  const applicants = useData<Row[]>(`/callouts/${id}/enrollments`, []); const [filter, setFilter] = useState('TODOS');
  return <><h2>Inscripciones y equipo a cargo</h2><ErrorNotice message={applicants.error}/><div className="filters">{['TODOS', 'CONFIRMADA', 'PENDIENTE_REEMPLAZO', 'RECHAZADA', 'CANCELADA'].map(f => <button key={f} className={`filter ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>{f === 'CONFIRMADA' ? 'ACEPTADA' : f.replaceAll('_', ' ')}</button>)}</div>
    {applicants.data.filter(e => filter === 'TODOS' || e.status === filter).map(e => <details className="card" key={e.id}><summary>{e.name} {e.surnames} · {positionNames[e.position]} · {e.status === 'CONFIRMADA' ? 'ACEPTADA' : e.status}</summary><p>Formación: {e.training_level} · Inscripción: {date(e.created_at)}</p><p>Código: {e.id}</p>{e.cancellation_requested_at && <p>Solicitó reemplazo: {e.cancellation_reason}</p>}{e.replacement_for && <p>Reemplaza inscripción: {e.replacement_for}</p>}
      {new Date(c.start_at).getTime() > Date.now() && ((e.status === 'PENDIENTE_REEMPLAZO' && c.can_operate) || (e.status === 'CONFIRMADA' && user.role === 'JEFATURA')) && <DataForm fields={[{ name: 'decision', label: 'Decisión', options: e.status === 'CONFIRMADA' ? ['RECHAZAR'] : ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo' }]} submit={v => api(`/enrollments/${e.id}/review`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/>}
    </details>)}
    {c.can_operate && <><OperationPanel id={id} user={user} kind={c.kind}/><MaterialsPanel id={id}/></>}
  </>;
}
function OperationPanel({ id, user, kind }: { id: string; user: Session; kind: string }) {
  const attendance = useData<Row[]>(`/callouts/${id}/attendance`, []);
  if (kind !== 'SERVICIO') return null;
  return <><h2>Apertura y cierre</h2><p>El encargado abre y cierra el servicio. Antes de abrir debe estar completo y tener foto del equipo.</p><PhotoForm id={id}/><DataForm fields={[{ name: 'action', label: 'Acción', options: ['ABRIR', 'CERRAR'] }, { name: 'observations', label: 'Observaciones e indicaciones', type: 'textarea' }]} submit={v => api(`/callouts/${id}/operation`, 'POST', v)}/>
    <h2>Revisión de asistencia</h2><p>Tolerancia: 10 minutos. Si supera los 10, se descuenta el doble del retraso hasta que jefatura apruebe su justificación.</p><ErrorNotice message={attendance.error}/>{attendance.data.map(a => <details className="card" key={a.id}><summary>{a.name} · {a.status} · {a.approved_hours ?? '—'} h</summary><p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p><p>Retraso: {delay(a.late_minutes)} · Deducción: {a.deduction} h</p><p>{a.observations}</p><DataForm key={`${a.id}-${a.check_in}-${a.check_out}`} initial={{ checkIn: localInput(a.check_in), checkOut: localInput(a.check_out), observations: a.observations }} fields={[
      { name: 'checkIn', label: 'Entrada (hora de Perú)', type: 'datetime-local' }, { name: 'checkOut', label: 'Salida (hora de Perú)', type: 'datetime-local' },
      ...(user.role === 'JEFATURA' ? [{ name: 'hours', label: 'Horas excepcionales autorizadas (vacío = horario y tolerancia)', type: 'number', min: 0, max: 48, step: '0.01', optional: true }] : []),
      { name: 'observations', label: 'Motivo / observaciones', type: 'textarea' },
    ]} label="Confirmar o corregir" submit={v => api(`/attendance/${a.id}/confirm`, 'POST', v)}/></details>)}</>;
}
function MaterialsPanel({ id }: { id: string }) {
  const materials = useData<Row[]>(`/callouts/${id}/materials`, []); const catalog = useData<Row[]>('/material-catalog', []);
  return <><h2>Solicitud de materiales al almacén</h2><p>Catálogo orientativo, sin control de stock. No bloquea el servicio. Registra quién recibe y devuelve cada artículo.</p><ErrorNotice message={materials.error || catalog.error}/><DataForm fields={[
    { name: 'materialId', label: 'Catálogo', optional: true, options: [{ value: '', label: 'Otro material / especificar' }, ...catalog.data.map(m => ({ value: m.id, label: m.name }))] },
    { name: 'name', label: 'Material o referencia', max: 160 }, { name: 'quantity', label: 'Cantidad solicitada', type: 'number', min: 1, max: 10000 }, { name: 'details', label: 'Especificaciones / indicaciones', type: 'textarea' },
  ]} submit={v => api(`/callouts/${id}/materials`, 'POST', { ...v, materialId: v.materialId || null })}/>
    {materials.data.map(m => <details className="card" key={m.id}><summary>{m.quantity} × {m.name}</summary><p>{m.details}</p><p>Recibido: {m.received_quantity} · {m.received_name || 'Pendiente'} · {date(m.received_at)}</p><p>Devuelto: {m.returned_quantity} · {m.returned_name || 'Pendiente'} · {date(m.returned_at)}</p><p>{m.condition_note}</p>{!m.returned_at && <DataForm fields={[
      { name: 'action', label: 'Movimiento', options: m.received_at ? ['DEVOLUCION'] : ['RECEPCION'] }, { name: 'quantity', label: m.received_at ? 'Cantidad devuelta en buen estado' : 'Cantidad recibida', type: 'number', min: m.received_at ? 0 : 1, max: m.received_at ? m.received_quantity : m.quantity },
      { name: 'observations', label: 'Observaciones (indica pérdidas o daños)', type: 'textarea' },
    ]} submit={v => api(`/materials/${m.id}/handover`, 'POST', v)}/>}</details>)}<button className="button secondary" onClick={() => window.print()}>Imprimir solicitud</button>
  </>;
}
function PhotoForm({ id }: { id: string }) {
  const [file, setFile] = useState<File>(); const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false);
  return <form className="panel" onSubmit={async e => { e.preventDefault(); if (!file) return; setBusy(true); const data = new FormData(); data.append('file', file); try { await api(`/callouts/${id}/photo`, 'POST', data); changed(); setMessage('Foto guardada.'); } catch (e) { setMessage((e as Error).message); } finally { setBusy(false); } }}><label className="field">Foto del equipo (JPG/PNG, máximo 2 MB)<input type="file" accept="image/jpeg,image/png" required onChange={e => setFile(e.target.files?.[0])}/></label><button className="button secondary" disabled={busy}>Guardar foto</button><a className="link" href={`/api/v1/callouts/${id}/photo`} target="_blank" rel="noreferrer"> Ver foto</a><p role="status">{message}</p></form>;
}
