import React, { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, date, localInput, manager, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice, Field } from './components';

const callFields: Field[] = [
  { name: 'title', label: 'Título', max: 150 }, { name: 'description', label: 'Descripción', type: 'textarea', max: 5000 },
  { name: 'kind', label: 'Tipo', options: ['SERVICIO', 'INGRESO'] }, { name: 'area', label: 'Área', max: 120 },
  { name: 'start', label: 'Inicio (hora de Perú)', type: 'datetime-local' }, { name: 'end', label: 'Fin (hora de Perú)', type: 'datetime-local' },
  { name: 'deadline', label: 'Inscripciones hasta (hora de Perú)', type: 'datetime-local' },
  { name: 'location', label: 'Lugar' }, { name: 'contact', label: 'Contacto de emergencia' },
  { name: 'requirements', label: 'Requisitos (uno por línea)', type: 'textarea', max: 5000 },
  { name: 'level', label: 'Nivel mínimo', options: ['BASICO', 'INTERMEDIO', 'AVANZADO'] },
  { name: 'capacity', label: 'Vacantes', type: 'number', min: 1, max: 10000 }, { name: 'minimum', label: 'Mínimo de participantes', type: 'number', min: 1, max: 10000 },
];

export function CalloutsPage({ user, history = false }: { user: Session; history?: boolean }) {
  const { data, error, loading } = useData<Row[]>(`/callouts?history=${history}`, []); const [query, setQuery] = useState(''); const [create, setCreate] = useState(false); const navigate = useNavigate();
  const filtered = data.filter(c => `${c.title} ${c.description} ${c.location} ${c.status} ${c.minimum_level}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="page-heading"><div><p className="eyebrow">{history ? 'Servicios anteriores' : 'Encuentra tu próxima actividad'}</p><h1>{history ? 'Historial' : 'Convocatorias'}</h1><p className="lead">{history ? 'Participaciones y servicios según tus permisos.' : 'Explora oportunidades compatibles con tu formación.'}</p></div>{manager(user) && !history && <button className="button" onClick={() => setCreate(!create)}>Nueva convocatoria</button>}</div>
    <ErrorNotice message={error}/>{create && <DataForm fields={callFields} initial={{ capacity: 10, minimum: 3, area: 'Voluntariado' }} label="Crear borrador" submit={async v => { const result = await api('/callouts', 'POST', v); navigate(`/callouts/${result.id}`); }}/>} 
    <div className="toolbar"><input aria-label="Buscar convocatorias" placeholder="Busca por actividad, lugar o estado…" value={query} onChange={e => setQuery(e.target.value)} list="callout-suggestions"/><datalist id="callout-suggestions">{filtered.slice(0, 4).map(c => <option key={c.id} value={c.title}/>)}</datalist><button className="button secondary" onClick={() => setQuery('')}>Limpiar</button></div>
    <div className="list">{filtered.map(c => <article className="list-item" key={c.id}><div><span className={`badge ${c.status === 'CANCELADA' ? 'red' : ''}`}>{c.status}</span><h2><Link to={`/callouts/${c.id}`}>{c.title}</Link></h2><p>{c.description} · 📍 {c.location}</p><p>🗓 {date(c.start_at)} · {c.enrolled}/{c.capacity} confirmados</p><small>Nivel: {c.minimum_level} · Mínimo: {c.minimum_participants}</small></div><Link className="button" to={`/callouts/${c.id}`}>Ver detalle</Link></article>)}</div>
    {!loading && !filtered.length && <div className="empty">No hay convocatorias para mostrar.</div>}
  </>;
}

export function DetailPage({ user }: { user: Session }) {
  const { id } = useParams(); const { data: c, error, loading } = useData<Row>(`/callouts/${id}`, {});
  const [checked, setChecked] = useState<string[]>([]); const [edit, setEdit] = useState(false);
  if (loading && !c.id) return <p>Cargando convocatoria…</p>;
  if (error) return <ErrorNotice message={error}/>;
  const requirements = String(c.requirements ?? '').split('\n').filter(Boolean);
  const canManage = user.role === 'JEFATURA' || manager(user) && c.responsible_id === user.id;
  const available = ['PUBLICADA', 'CONFIRMADA'].includes(c.status) && new Date(c.start_at).getTime() > Date.now() && (!c.deadline || new Date(c.deadline).getTime() > Date.now());
  return <><Link className="link" to="/callouts">← Volver a la lista</Link><div className="page-heading"><div><p className="eyebrow">{c.kind} · {c.area}</p><h1>{c.title}</h1><p className="lead">{c.description}</p></div><span className="badge">{c.status}</span></div>
    <div className="grid three"><article className="card"><p className="eyebrow">Vacantes disponibles</p><div className="stat">{Math.max(0, c.capacity - c.enrolled)}</div><p>{c.enrolled} confirmados de {c.capacity}</p></article><article className="card"><p className="eyebrow">Mínimo del servicio</p><div className="stat">{c.enrolled}/{c.minimum_participants}</div><p>{Math.max(0, c.minimum_participants - c.enrolled)} participantes por confirmar</p></article><article className="card"><p className="eyebrow">Duración programada</p><div className="stat">{((new Date(c.end_at).getTime() - new Date(c.start_at).getTime()) / 3600000).toLocaleString('es-PE')} h</div><p>Nivel {c.minimum_level}</p></article></div>
    <section className="panel"><p>📍 {c.location}</p><p>Inicio: {date(c.start_at)} · Fin: {date(c.end_at)}</p><p>Inscripciones hasta: {date(c.deadline || c.start_at)}</p><p>Contacto: {c.contact || 'Consultar al responsable'}</p><p>Observaciones: {c.observations || 'Sin observaciones'}</p></section>
    {available && <section className="card"><h2>Verificación de requisitos</h2><p>Marca los requisitos que cumples. Tu perfil y disponibilidad también se validan en el servidor.</p>{requirements.map((r, i) => <label className="requirement" key={i}><input type="checkbox" checked={checked.includes(r)} onChange={e => setChecked(old => e.target.checked ? [...old, r] : old.filter(x => x !== r))}/>{r}</label>)}
      {checked.length === requirements.length && <DataForm fields={[{ name: 'replacementFor', label: 'Código de inscripción a reemplazar (opcional)', optional: true }]} label="Solicitar inscripción" submit={v => api(`/callouts/${id}/enrollments`, 'POST', { requirementsAccepted: true, replacementFor: v.replacementFor || null })}/>}
      {checked.length !== requirements.length && <p>Debes marcar todos los requisitos para solicitar la inscripción.</p>}
    </section>}
    {canManage && <><h2>Gestión de convocatoria</h2><div className="toolbar">{c.status === 'BORRADOR' && <><Action path={`/callouts/${id}/status`} body={{ status: 'PUBLICADA' }}>Publicar</Action><button className="button secondary" onClick={() => setEdit(!edit)}>Editar</button></>}{c.status === 'PUBLICADA' && <Action path={`/callouts/${id}/status`} body={{ status: 'CONFIRMADA' }}>Confirmar servicio</Action>}{['BORRADOR', 'PUBLICADA', 'CONFIRMADA'].includes(c.status) && <Action path={`/callouts/${id}/status`} body={{ status: 'CANCELADA' }}>Cancelar convocatoria</Action>}</div>
      {['PUBLICADA', 'CONFIRMADA'].includes(c.status) && <button className="button secondary" onClick={() => setEdit(!edit)}>Editar datos</button>}
      {edit && <DataForm fields={callFields} initial={{ ...c, start: localInput(c.start_at), end: localInput(c.end_at), deadline: localInput(c.deadline || c.start_at), level: c.minimum_level, minimum: c.minimum_participants }} submit={v => api(`/callouts/${id}`, 'PUT', v)}/>}
      <TeamPage id={id!} user={user} kind={c.kind}/>
    </>}
  </>;
}

function TeamPage({ id, user, kind }: { id: string; user: Session; kind: string }) {
  const applicants = useData<Row[]>(`/callouts/${id}/enrollments`, []); const attendance = useData<Row[]>(`/callouts/${id}/attendance`, []);
  const materials = useData<Row[]>(`/callouts/${id}/materials`, []); const [filter, setFilter] = useState('TODOS');
  return <><h2>Postulantes y equipo a cargo</h2><ErrorNotice message={applicants.error || attendance.error || materials.error}/>
    <div className="filters">{['TODOS', 'PENDIENTE', 'CONFIRMADA', 'RECHAZADA', 'EXPIRADA'].map(f => <button key={f} className={`filter ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>{f}</button>)}</div>
    {applicants.data.filter(e => filter === 'TODOS' || e.status === filter).map(e => <details className="card" key={e.id}><summary>{e.name} {e.surnames} · {e.training_level} · {e.status}</summary><p>Solicitud: {date(e.created_at)}</p><p>Código: {e.id}</p>{e.replacement_for && <p>Reemplaza inscripción: {e.replacement_for}</p>}{e.status === 'PENDIENTE' && <DataForm fields={[{ name: 'decision', label: 'Decisión', options: ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo' }]} submit={v => api(`/enrollments/${e.id}/review`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/>}</details>)}
    {kind === 'SERVICIO' && <><h2>Apertura y cierre</h2><p>El responsable abre y cierra el servicio. La foto del equipo queda asociada al servicio.</p><PhotoForm id={id}/><DataForm fields={[{ name: 'action', label: 'Acción', options: ['ABRIR', 'CERRAR'] }, { name: 'observations', label: 'Observaciones e indicaciones', type: 'textarea' }]} submit={v => api(`/callouts/${id}/operation`, 'POST', v)}/>
      <h2>Revisión de asistencia</h2>{attendance.data.map(a => <details className="card" key={a.id}><summary>{a.name} · {a.status} · {a.approved_hours ?? '—'} h</summary><p>{a.observations}</p><p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p><DataForm initial={{ checkIn: localInput(a.check_in), checkOut: localInput(a.check_out), observations: a.observations }} fields={[
        { name: 'checkIn', label: 'Entrada (hora de Perú)', type: 'datetime-local' }, { name: 'checkOut', label: 'Salida (hora de Perú)', type: 'datetime-local' },
        ...(user.role === 'JEFATURA' ? [{ name: 'hours', label: 'Horas excepcionales autorizadas (vacío = cálculo automático)', type: 'number', min: 0, max: 48, step: '0.01', optional: true }] : []),
        { name: 'observations', label: 'Motivo / observaciones', type: 'textarea' },
      ]} label="Confirmar o corregir" submit={v => api(`/attendance/${a.id}/confirm`, 'POST', v)}/></details>)}</>}
    <h2>Formulario de materiales necesarios</h2><DataForm fields={[{ name: 'name', label: 'Material', max: 160 }, { name: 'quantity', label: 'Cantidad', type: 'number', min: 1, max: 10000 }, { name: 'details', label: 'Especificaciones / indicaciones', type: 'textarea' }]} submit={v => api(`/callouts/${id}/materials`, 'POST', v)}/>
    {materials.data.map(m => <article className="panel" key={m.id}><b>{m.quantity} × {m.name}</b><p>{m.details}</p></article>)}
  </>;
}
function PhotoForm({ id }: { id: string }) {
  const [file, setFile] = useState<File>(); const [message, setMessage] = useState('');
  return <form className="panel" onSubmit={async e => { e.preventDefault(); if (!file) return; const data = new FormData(); data.append('file', file); try { await api(`/callouts/${id}/photo`, 'POST', data); setMessage('Foto guardada.'); } catch (e) { setMessage((e as Error).message); } }}><label className="field">Foto del equipo (JPG/PNG, máximo 2 MB)<input type="file" accept="image/jpeg,image/png" required onChange={e => setFile(e.target.files?.[0])}/></label><button className="button secondary">Guardar foto</button><a className="link" href={`/api/v1/callouts/${id}/photo`} target="_blank" rel="noreferrer"> Ver foto</a><p role="status">{message}</p></form>;
}
