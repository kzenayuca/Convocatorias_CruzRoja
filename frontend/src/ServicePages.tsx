import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, changed, date, delay, localInput, manager, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice, Field, PdfDownload } from './components';

export const positionNames: Record<string, string> = { TOTAL: 'Total de voluntarios', RESPONSABLE: 'Responsable', SOCORRISTA: 'Voluntario · graduado o en formación', GRADUADO: 'Voluntario graduado', FORMACION: 'En formación · solo reemplazo histórico', RADIOPERADOR: 'Radioperador', PILOTO: 'Piloto' };
const statuses: Record<string, string> = { BORRADOR: 'Borrador', ABIERTA: 'Abierta', CERRADA: 'Completa', CANCELADA: 'Cancelada', FINALIZADA: 'Finalizada', PUBLICADA: 'Publicada (histórico)', CONFIRMADA: 'Confirmada (histórico)' };
const callFields: Field[] = [
  { name: 'code', label: 'Código único del servicio · asignado por jefatura', optional: true, max: 40 },
  { name: 'title', label: 'Título', max: 150 }, { name: 'description', label: 'Descripción', type: 'textarea', max: 5000 },
  { name: 'kind', label: 'Convocatoria', options: ['SERVICIO', 'INGRESO'] },
  { name: 'serviceType', label: 'Tipo de servicio (texto libre)', max: 100 }, { name: 'area', label: 'Área', max: 120 },
  { name: 'start', label: 'Inicio (hora de Perú)', type: 'datetime-local' }, { name: 'end', label: 'Fin (hora de Perú)', type: 'datetime-local' },
  { name: 'deadline', label: 'Completar puestos antes de (hora de Perú)', type: 'datetime-local' },
  { name: 'location', label: 'Lugar' },
  { name: 'requirements', label: 'Requisitos de esta convocatoria: conocimientos, cursos y experiencia (uno por línea; verificación por jefatura)', type: 'textarea', max: 5000 },
  { name: 'totalVolunteers', label: 'Total de voluntarios (incluye responsable, piloto y radioperador)', type: 'number', min: 1, max: 10000 },
  { name: 'graduateSlots', label: 'Mínimo de graduados dentro del total (0 = indistinto)', type: 'number', min: 0, max: 10000 },
  { name: 'radioSlots', label: 'Radioperador requerido dentro del total (0 o 1)', type: 'number', min: 0, max: 1 },
  { name: 'pilotSlots', label: 'Piloto requerido dentro del total (0 o 1)', type: 'number', min: 0, max: 1 },
];
export function Positions({ positions }: { positions: Row[] }) {
  return <div className="positions-grid">{positions.filter(p => p.position !== 'RESPONSABLE' && p.required > 0).map(p => <article className="position-card" key={p.position}>
    <p className="eyebrow">{p.position === 'GRADUADO' ? 'Graduados dentro del total · mínimo' : positionNames[p.position]}</p><div className="stat">{p.filled}<small> / {p.required}</small></div>
    <p>{p.filled >= p.required ? 'Requisito cubierto' : `${p.required - p.filled} pendientes`}</p>
  </article>)}</div>;
}
export function CalloutsPage({ user, history = false }: { user: Session; history?: boolean }) {
  const { data, error, loading } = useData<Row[]>(`/callouts?history=${history}`, []);
  const [query, setQuery] = useState(''); const [create, setCreate] = useState(false); const navigate = useNavigate();
  const filtered = data.filter(c => `${c.service_code ?? ''} ${c.title} ${c.service_type} ${c.location} ${statuses[c.status]}`.toLowerCase().includes(query.toLowerCase()));
  return <><div className="page-heading"><div><p className="eyebrow">{history ? 'Tu recorrido' : 'Encuentra tu próxima actividad'}</p><h1>{history ? 'Historial' : 'Convocatorias'}</h1><p className="lead">{history ? 'Servicios anteriores según tus permisos.' : 'Encuentra un puesto según tu formación y disponibilidad.'}</p></div>{manager(user) && !history && <button className="button" onClick={() => setCreate(!create)}>{create ? 'Cerrar formulario' : 'Nueva convocatoria'}</button>}</div>
    <ErrorNotice message={error}/>{create && <section className="section-block"><h2>Preparar convocatoria</h2><p>Voluntario = graduado o en formación. Total 4 y mínimo 1 graduado significa 4 personas, no 5. Responsable, piloto y radioperador cuentan dentro del total y pueden cubrir el mínimo de graduados. El responsable sigue siendo opcional y lo asigna jefatura.</p>
      <DataForm fields={callFields.filter(f => user.role === 'JEFATURA' || f.name !== 'code')} initial={{ totalVolunteers: 4, graduateSlots: 0, radioSlots: 0, pilotSlots: 0, area: 'Voluntariado', serviceType: 'Apoyo Salud' }} label="Crear borrador" submit={async v => { const result = await api('/callouts', 'POST', v); navigate(`/callouts/${result.id}`); }}/>
    </section>}
    <div className="toolbar"><input aria-label="Buscar convocatorias" placeholder="Código, actividad, tipo o lugar…" value={query} onChange={e => setQuery(e.target.value)}/><button className="button secondary" onClick={() => setQuery('')}>Limpiar</button></div>
    <div className="callout-grid">{filtered.map(c => <article className="card callout-card" key={c.id}><div className="card-topline"><span className="eyebrow">{c.service_code || 'Código pendiente'}</span><span className={`badge ${c.status === 'CANCELADA' ? 'red' : ''}`}>{statuses[c.status] || c.status}</span></div>
      <p className="muted">{c.service_type}</p><h2><Link to={`/callouts/${c.id}`}>{c.title}</Link></h2><p>📍 {c.location}</p><p>🗓 {date(c.start_at)}</p><p>{Number(c.planned_hours).toLocaleString('es-PE')} horas programadas</p>
      <p className="muted">Total: {c.positions.find((p: Row) => p.position === 'TOTAL')?.filled}/{c.total_volunteers} voluntarios · Mínimo {c.graduate_slots} graduados dentro del total</p>
      <Link className="button secondary" to={`/callouts/${c.id}`}>Ver detalle</Link>
    </article>)}</div>{!loading && !filtered.length && <div className="empty">No hay convocatorias para mostrar.</div>}
  </>;
}
export function DetailPage({ user }: { user: Session }) {
  const { id } = useParams(); const { data: c, error, loading } = useData<Row>(`/callouts/${id}`, {});
  const [checked, setChecked] = useState<number[]>([]); const [edit, setEdit] = useState(false);
  useEffect(() => { setChecked([]); setEdit(false); }, [id, c.requirements]);
  if (loading && !c.id) return <p>Cargando convocatoria…</p>;
  if (error) return <ErrorNotice message={error}/>;
  const requirements = String(c.requirements ?? '').split('\n').filter(Boolean);
  const future = new Date(c.start_at).getTime() > Date.now();
  const available = user.role !== 'JEFATURA' && ['ABIERTA', 'CERRADA'].includes(c.status) && future && (!c.my_enrollment || ['PENDIENTE', 'EXPIRADA'].includes(c.my_enrollment.status));
  const editable = c.can_edit && future && !c.opened_at && ['BORRADOR', 'ABIERTA', 'CERRADA'].includes(c.status);
  const positionOptions = (c.eligible_positions ?? []).map((p: string) => ({ value: p, label: positionNames[p] }));
  return <div className="detail-page"><Link className="link" to="/callouts">← Volver a convocatorias</Link>
    <section className="service-hero"><div className="card-topline"><p className="eyebrow">{c.service_code || 'Código pendiente de jefatura'} · {c.area}</p><span className="badge">{statuses[c.status] || c.status}</span></div>
      <h1>{c.title}</h1><p className="lead">{c.description}</p><div className="toolbar"><span className="pill">{c.service_type}</span><span className="pill">{c.execution}</span><span className="pill">Cobertura {c.coverage === 'COMPLETA' ? 'completa' : 'pendiente'}</span></div>
      {c.my_enrollment && <p className="participation-note">Tu inscripción: <b>{c.my_enrollment.status === 'CONFIRMADA' ? 'ACEPTADA' : c.my_enrollment.status}</b> · {positionNames[c.my_enrollment.position]} <Link className="link" to="/services">Consultar mi inscripción →</Link></p>}
    </section>
    <section className="section-block"><h2>Información del servicio</h2><dl className="info-grid">
      <div><dt>Inicio · hora de Perú</dt><dd>{date(c.start_at)}</dd></div><div><dt>Fin · hora de Perú</dt><dd>{date(c.end_at)}</dd></div>
      <div><dt>Lugar</dt><dd>{c.location}</dd></div><div><dt>Horas programadas</dt><dd>{Number(c.planned_hours).toLocaleString('es-PE')} h</dd></div>
      <div><dt>Responsable</dt><dd>{c.responsible_name || 'Sin asignar'}</dd></div><div><dt>Contacto</dt><dd>{c.contact || 'Consultar a jefatura'}</dd></div>
      <div><dt>Inscripciones hasta</dt><dd>{date(c.deadline)}</dd></div>
    </dl>{c.observations && <div className="soft-panel"><h3>Indicaciones</h3><p className="multiline">{c.observations}</p></div>}</section>
    <section className="section-block"><h2>Vacantes disponibles</h2><p className="muted">Cada persona cuenta una sola vez. Responsable, piloto y radioperador están incluidos en el total. El mínimo de graduados y las funciones son requisitos dentro de ese total, no plazas adicionales. Voluntario admite graduados y personas en formación; voluntario graduado, solo graduados.</p><Positions positions={c.positions ?? []}/></section>
    <section className="section-block"><h2>Verificación de requisitos</h2><p>Los conocimientos, cursos y demás condiciones se definen en esta convocatoria; jefatura verifica las declaraciones. El sistema comprueba condición de graduación, habilitaciones, cupo y horario.</p>
      {available && positionOptions.length ? <>{requirements.map((r, i) => <label className="requirement" key={i}><input type="checkbox" checked={checked.includes(i)} onChange={e => setChecked(old => e.target.checked ? [...old, i] : old.filter(x => x !== i))}/>{r}</label>)}
        {checked.length === requirements.length ? <DataForm fields={[{ name: 'position', label: 'Puesto', options: positionOptions }, { name: 'replacementFor', label: 'Código del titular (solo reemplazos)', optional: true }]} label="Inscribirme" submit={v => api(`/callouts/${id}/enrollments`, 'POST', { requirementsAccepted: true, position: v.position, replacementFor: v.replacementFor || null })}/> : <p>Marca todos los requisitos para continuar.</p>}
        <p className="muted">Inscripción automática para cuentas validadas. Los reemplazos requieren revisión del encargado o jefatura.</p>
      </> : <><ul className="requirements-list">{requirements.map((r, i) => <li key={i}>{r}</li>)}</ul><p className="muted">{c.my_enrollment ? 'Consulta el estado de tu participación en Mis servicios.' : 'No hay una inscripción disponible para tu perfil en este momento.'}</p></>}
    </section>
    {(c.has_photo || c.can_upload_photo) && <section className="section-block"><h2>Recuerdo del equipo</h2>{c.has_photo && <details className="photo-view"><summary>Ver foto del equipo</summary><a href={`/api/v1/callouts/${id}/photo`} target="_blank" rel="noreferrer"><img loading="lazy" src={`/api/v1/callouts/${id}/photo?v=${encodeURIComponent(c.photo_uploaded_at ?? '')}`} alt={`Equipo de ${c.title}`}/></a></details>}{c.can_upload_photo && <PhotoAttachment id={id!} hasPhoto={c.has_photo} closed={!!c.closed_at || c.status === 'FINALIZADA'}/>}</section>}
    {c.can_view_team && <section className="management-area" aria-label="Gestión del servicio"><h2>Gestión del servicio</h2><p className="muted">Equipo, asignaciones y operación. Estos controles solo aparecen para jefatura o el responsable de esta convocatoria.</p>
    {editable && <details className="section-block management"><summary>Gestionar convocatoria</summary><div className="toolbar">{c.status === 'BORRADOR' && <Action path={`/callouts/${id}/status`} body={{ status: 'ABIERTA' }}>Publicar</Action>}<button className="button secondary" onClick={() => setEdit(!edit)}>Editar datos</button><Action path={`/callouts/${id}/status`} body={{ status: 'CANCELADA' }}>Cancelar convocatoria</Action></div>
      {edit && <DataForm fields={callFields.filter(f => user.role === 'JEFATURA' || f.name !== 'code')} initial={{ ...c, code: c.service_code, serviceType: c.service_type, start: localInput(c.start_at), end: localInput(c.end_at), deadline: localInput(c.deadline), totalVolunteers: c.total_volunteers, graduateSlots: c.graduate_slots, radioSlots: c.radio_slots, pilotSlots: c.pilot_slots }} submit={v => api(`/callouts/${id}`, 'PUT', { ...v, code: user.role === 'JEFATURA' ? v.code : c.service_code })}/>}
    </details>}
    {user.role === 'JEFATURA' && editable && <ResponsiblePanel id={id!} current={c.responsible_id}/>}
    {user.role === 'JEFATURA' && editable && ['ABIERTA', 'CERRADA'].includes(c.status) && new Date(c.deadline).getTime() > Date.now() && <AssignmentPanel id={id!} requirements={c.requirements}/>}
    <TeamPage id={id!} user={user} callout={c}/>
    </section>}
  </div>;
}
function ResponsiblePanel({ id, current }: { id: string; current?: string }) {
  const candidates = useData<Row[]>(`/callouts/${id}/responsible-candidates`, []);
  const history = useData<Row[]>(`/callouts/${id}/responsible/history`, []); const [changing, setChanging] = useState(false);
  return <details className="section-block"><summary>{current ? 'Responsable asignado · jefatura' : 'Asignar responsable · jefatura'}</summary><p>Solo un voluntario graduado activo ya inscrito como voluntario general, sin cruce horario. Verifica los requisitos de la convocatoria. No se duplica su plaza ni se convierte al piloto/radioperador. Al retirar la responsabilidad conserva su inscripción, pero pierde los permisos de gestión.</p><ErrorNotice message={candidates.error || history.error}/>
    {current && <><p>Ya hay un responsable. No se puede asignar otro sin registrar un cambio explícito.</p><button className="button secondary" onClick={() => setChanging(!changing)}>{changing ? 'Cancelar cambio' : 'Cambiar o retirar responsable'}</button></>}
    {(!current || changing) && <DataForm key={current ?? 'none'} fields={[{ name: 'userId', label: current ? 'Nuevo responsable (vacío = retirar responsabilidad)' : 'Voluntario inscrito', optional: !!current, options: [{ value: '', label: current ? 'Retirar responsabilidad' : 'Selecciona una persona' }, ...candidates.data.map(u => ({ value: u.id, label: `${u.name} ${u.surnames}` }))] }, ...(current ? [{ name: 'reason', label: 'Motivo obligatorio del cambio o retiro', type: 'textarea' }] : [])]} label={current ? 'Confirmar cambio auditado' : 'Asignar responsable'} submit={async v => { await api(current ? `/callouts/${id}/responsible/change` : `/callouts/${id}/responsible`, current ? 'POST' : 'PUT', current ? { userId: v.userId || null, expectedResponsibleId: current, reason: v.reason } : { userId: v.userId }); setChanging(false); }}/ >}
    {!!history.data.length && <details><summary>Historial de responsables</summary>{history.data.map(h => <p key={h.id}>{date(h.changed_at)} · {h.changed_name}: {h.previous_name || 'Sin responsable'} → {h.next_name || 'Sin responsable'} · {h.reason}</p>)}</details>}
  </details>;
}
function AssignmentPanel({ id, requirements }: { id: string; requirements: string }) {
  const [position, setPosition] = useState('SOCORRISTA'); const [userId, setUserId] = useState('');
  const [query, setQuery] = useState('');
  const [verified, setVerified] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [success, setSuccess] = useState('');
  const candidates = useData<Row[]>(`/callouts/${id}/assignment-candidates?position=${position}`, []);
  useEffect(() => { setUserId(''); setQuery(''); setVerified(false); setSuccess(''); }, [id, position, requirements]);
  const matches = candidates.data.filter(u => `${u.name} ${u.surnames}`.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().includes(query.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase()));
  const options = ['SOCORRISTA', 'GRADUADO', 'RADIOPERADOR', 'PILOTO'];
  return <details className="section-block"><summary>Inscribir directamente · jefatura</summary><p>Busca por nombre y selecciona una persona elegible. Verifica conocimientos, cursos y demás requisitos de esta convocatoria. No se omiten graduación, habilitaciones, cupo, plazo ni cruces de horario. Se notificará a la persona.</p>
    <p className="multiline"><b>Requisitos que debes verificar:</b>{'\n'}{requirements}</p>
    <form className="card flow-form" onSubmit={async e => {
      e.preventDefault(); setBusy(true); setError(''); setSuccess('');
      try {
        await api(`/callouts/${id}/assignments`, 'POST', { userId, position, requirementsVerified: verified });
        setUserId(''); setVerified(false); setSuccess('Inscripción directa confirmada. Se notificó a la persona.'); changed();
      } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
    }}>
      <label className="field">Puesto<select name="assignmentPosition" value={position} onChange={e => setPosition(e.target.value)}>{options.map(p => <option key={p} value={p}>{positionNames[p]}</option>)}</select></label>
      <label className="field">Buscar persona por nombre<input name="assignmentSearch" type="search" autoComplete="off" value={query} placeholder="Escribe nombres o apellidos…" onChange={e => { setQuery(e.target.value); setUserId(''); setVerified(false); }}/></label>
      <label className="field">Persona elegible<select name="assignmentUser" required value={userId} onChange={e => { setUserId(e.target.value); setVerified(false); }} disabled={candidates.loading || busy}><option value="">Selecciona una persona</option>{matches.map(u => <option key={u.id} value={u.id}>{u.name} {u.surnames} · {u.graduated ? 'Graduado' : 'En formación'}</option>)}</select></label>
      {!candidates.loading && !!candidates.data.length && !matches.length && <p>No hay coincidencias para ese nombre.</p>}
      {!candidates.loading && !candidates.data.length && <p>No hay personas elegibles para este puesto con los cupos y horarios actuales.</p>}
      <label className="requirement"><input name="assignmentVerified" type="checkbox" required checked={verified} onChange={e => setVerified(e.target.checked)}/>He verificado todos los requisitos declarados para esta persona.</label>
      <ErrorNotice message={error || candidates.error}/>{success && <p role="status">{success}</p>}
      <button className="button" disabled={busy || candidates.loading || !userId || !verified}>{busy ? 'Inscribiendo…' : 'Confirmar inscripción directa'}</button>
    </form>
  </details>;
}
function TeamPage({ id, user, callout: c }: { id: string; user: Session; callout: Row }) {
  const applicants = useData<Row[]>(`/callouts/${id}/enrollments`, []);
  return <><details className="section-block"><summary>Equipo e inscripciones</summary><ErrorNotice message={applicants.error}/>
    {Object.keys(positionNames).map(position => {
      const rows = applicants.data.filter(e => e.position === position); if (!rows.length) return null;
      return <section className="team-group" key={position}><h3>{positionNames[position]}</h3>{rows.map(e => <details className="card" key={e.id}>
        <summary>{e.name} {e.surnames} · {e.status === 'CONFIRMADA' ? 'ACEPTADA' : e.status}</summary><p>Inscripción: {date(e.created_at)}</p>{e.assigned_by && <p>Asignación directa de jefatura: {date(e.assigned_at)} · Requisitos verificados.</p>}<p>Código: {e.id}</p>{e.cancellation_requested_at && <p>Solicitó reemplazo: {e.cancellation_reason}</p>}
        {new Date(c.start_at).getTime() > Date.now() && ((e.status === 'PENDIENTE_REEMPLAZO' && c.can_operate) || (e.status === 'CONFIRMADA' && user.role === 'JEFATURA')) && <DataForm fields={[{ name: 'decision', label: 'Decisión', options: e.status === 'CONFIRMADA' ? ['RECHAZAR'] : ['APROBAR', 'RECHAZAR'] }, { name: 'comment', label: 'Motivo' }]} submit={v => api(`/enrollments/${e.id}/review`, 'POST', { approved: v.decision === 'APROBAR', comment: v.comment })}/>}
      </details>)}</section>;
    })}
    {!applicants.data.length && <p>Aún no hay participantes.</p>}
    </details>{c.can_operate && <><OperationPanel id={id} user={user} kind={c.kind}/><MaterialsPanel id={id}/></>}</>;
}
function OperationPanel({ id, user, kind }: { id: string; user: Session; kind: string }) {
  const attendance = useData<Row[]>(`/callouts/${id}/attendance`, []);
  if (kind !== 'SERVICIO') return null;
  return <><details className="section-block"><summary>Apertura y cierre del servicio</summary><p>Puede abrirse con las vacantes completas, aunque no tenga responsable ni foto.</p><DataForm fields={[{ name: 'action', label: 'Acción', options: ['ABRIR', 'CERRAR'] }, { name: 'observations', label: 'Observaciones e indicaciones', type: 'textarea' }]} submit={v => api(`/callouts/${id}/operation`, 'POST', v)}/></details>
    <details className="section-block"><summary>Revisión de asistencia</summary><p>Después de 7 días sin revisión, se usan las marcas del participante. Sin entrada y salida no se acreditan horas automáticamente.</p><ErrorNotice message={attendance.error}/>{attendance.data.map(a => <details className="card" key={a.id}><summary>{a.name} · {a.status} · {a.approved_hours ?? '—'} h</summary><p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p><p>Retraso: {delay(a.late_minutes)} · Deducción: {a.deduction} h</p><p>{a.observations}</p>{a.month_closed ? <p>Mes aprobado: la asistencia ya no se puede modificar.</p> : <DataForm key={`${a.id}-${a.check_in}-${a.check_out}`} initial={{ checkIn: localInput(a.check_in), checkOut: localInput(a.check_out), observations: a.observations }} fields={[
      { name: 'checkIn', label: 'Entrada (hora de Perú)', type: 'datetime-local' }, { name: 'checkOut', label: 'Salida (hora de Perú)', type: 'datetime-local' },
      ...(user.role === 'JEFATURA' ? [{ name: 'hours', label: 'Horas excepcionales autorizadas (vacío = cálculo normal)', type: 'number', min: 0, max: 48, step: '0.01', optional: true }] : []),
      { name: 'observations', label: 'Motivo / observaciones', type: 'textarea' },
    ]} label="Confirmar o corregir" submit={v => api(`/attendance/${a.id}/confirm`, 'POST', v)}/>}</details>)}</details></>;
}
function MaterialsPanel({ id }: { id: string }) {
  const materials = useData<Row[]>(`/callouts/${id}/materials`, []); const catalog = useData<Row[]>('/material-catalog', []);
  return <details className="section-block"><summary>Solicitud de materiales al almacén</summary><p>No administra stock ni bloquea el servicio. Registra la entrega y devolución.</p><ErrorNotice message={materials.error || catalog.error}/>
    <details><summary>Añadir material</summary><DataForm fields={[
      { name: 'materialId', label: 'Catálogo orientativo', optional: true, options: [{ value: '', label: 'Otro material' }, ...catalog.data.map(m => ({ value: m.id, label: m.name }))] },
      { name: 'name', label: 'Material o referencia', max: 160 }, { name: 'quantity', label: 'Cantidad solicitada', type: 'number', min: 1, max: 10000 }, { name: 'details', label: 'Especificaciones', type: 'textarea' },
    ]} submit={v => api(`/callouts/${id}/materials`, 'POST', { ...v, materialId: v.materialId || null })}/></details>
    <div className="document-preview"><div className="page-heading"><div><p className="eyebrow">Vista previa digital</p><h3>Solicitud al almacén</h3></div><PdfDownload path={`/callouts/${id}/materials/pdf`} filename="solicitud-materiales.pdf"/></div>
      {!materials.data.length && <p>Sin materiales solicitados.</p>}{materials.data.map(m => <div className="preview-row" key={m.id}><b>{m.quantity} × {m.name}</b><p>{m.details}</p><small>Recibido: {m.received_quantity} · Devuelto: {m.returned_quantity}</small><p>{m.condition_note}</p></div>)}
    </div>{materials.data.map(m => <details className="card" key={m.id}><summary>Entrega / devolución · {m.name}</summary><p>Recibido: {m.received_quantity} · {m.received_name || 'Pendiente'} · {date(m.received_at)}</p><p>Devuelto: {m.returned_quantity} · {m.returned_name || 'Pendiente'} · {date(m.returned_at)}</p>{!m.returned_at && <DataForm fields={[
      { name: 'action', label: 'Movimiento', options: m.received_at ? ['DEVOLUCION'] : ['RECEPCION'] }, { name: 'quantity', label: 'Cantidad', type: 'number', min: m.received_at ? 0 : 1, max: m.received_at ? m.received_quantity : m.quantity }, { name: 'observations', label: 'Observaciones (pérdidas o daños)', type: 'textarea' },
    ]} submit={v => api(`/materials/${m.id}/handover`, 'POST', v)}/>}</details>)}
  </details>;
}
function PhotoForm({ id }: { id: string }) {
  const [file, setFile] = useState<File>(); const [message, setMessage] = useState(''); const [busy, setBusy] = useState(false);
  return <form className="soft-panel" onSubmit={async e => { e.preventDefault(); if (!file) return; setBusy(true); setMessage(''); try { if (file.size > 10 * 1024 * 1024) throw new Error('Máximo 10 MB.'); const data = new FormData(); data.append('file', file); await api(`/callouts/${id}/photo`, 'POST', data); changed(); setMessage('Foto guardada.'); } catch (e) { setMessage((e as Error).message); } finally { setBusy(false); } }}><label className="field">Foto opcional JPG/PNG · hasta 10 MB y 40 megapíxeles<input type="file" accept="image/jpeg,image/png" required onChange={e => setFile(e.target.files?.[0])}/></label><button className="button secondary" disabled={busy}>Guardar foto</button><p role="status">{message}</p></form>;
}
function PhotoAttachment({ id, hasPhoto, closed }: { id: string; hasPhoto: boolean; closed: boolean }) {
  const [open, setOpen] = useState(false);
  return <div>{closed && !hasPhoto && <p role="status">Servicio cerrado. Puedes adjuntar una foto del equipo ahora o más adelante; es opcional.</p>}<button className="button secondary" onClick={() => setOpen(!open)}>{open ? 'Cerrar adjunto' : hasPhoto ? 'Reemplazar foto' : 'Adjuntar foto'}</button>{open && <PhotoForm id={id}/>}</div>;
}
