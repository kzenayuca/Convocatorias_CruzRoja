import React, { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { api, changed, date, delay, localInput, manager, Row, Session, useData } from './api';
import { Action, DataForm, ErrorNotice, Field, PdfDownload, saveSucceeded, useRequestFocus } from './components';
import { AnnualEnrollmentPanel } from './AnnualPages';
import { categoryLabel, categoryNames, formatHours, positionNames, statusLabel } from './callouts/calloutLabels';
import {
  canAssignDirectly, canEditCallout, canEnroll, canReviewEnrollment, enrollmentStatusLabel, isChief, parseRequirements, reviewDecisions,
} from './callouts/calloutRules';
import { CalloutHero, EnrollmentSection, ServiceInformation } from './callouts/CalloutDetailSections';

export { positionNames };
const DEFAULT_CATEGORY = 'I';
const categoryOptions = Object.entries(categoryNames).map(([value, label]) => ({ value, label }));

const callFields: Field[] = [
  { name: 'category', label: 'Etiqueta de servicio · código automático al crear', options: categoryOptions },
  { name: 'title', label: 'Título', max: 150 },
  { name: 'description', label: 'Descripción', type: 'textarea', max: 5000 },
  { name: 'kind', label: 'Convocatoria', options: ['SERVICIO', 'INGRESO'] },
  { name: 'serviceType', label: 'Descripción del servicio (concierto, limpieza, apoyo, guardia…)', max: 100 },
  { name: 'area', label: 'Área', max: 120 },
  { name: 'start', label: 'Inicio (hora de Perú)', type: 'datetime-local' },
  { name: 'end', label: 'Fin (hora de Perú)', type: 'datetime-local' },
  { name: 'deadline', label: 'Completar puestos antes de (hora de Perú)', type: 'datetime-local' },
  { name: 'location', label: 'Lugar' },
  {
    name: 'requirements',
    label: 'Requisitos de esta convocatoria: conocimientos, cursos y experiencia (uno por línea; verificación por jefatura)',
    type: 'textarea',
    max: 5000,
  },
  { name: 'totalVolunteers', label: 'Total de voluntarios (incluye responsable, piloto y radioperador)', type: 'number', min: 1, max: 10000 },
  { name: 'graduateSlots', label: 'Mínimo de graduados dentro del total (0 = indistinto)', type: 'number', min: 0, max: 10000 },
  { name: 'radioSlots', label: 'Radioperador requerido dentro del total (0 o 1)', type: 'number', min: 0, max: 1 },
  { name: 'pilotSlots', label: 'Piloto requerido dentro del total (0 o 1)', type: 'number', min: 0, max: 1 },
];

function positionTitle(position: string) {
  return position === 'GRADUADO' ? 'Graduados dentro del total · mínimo' : positionNames[position];
}

function coverageText(filled: number, required: number) {
  return filled >= required ? 'Requisito cubierto' : `${required - filled} pendientes`;
}

export function Positions({ positions }: Readonly<{ positions: Row[] }>) {
  const visible = positions.filter(item => item.position !== 'RESPONSABLE' && item.required > 0);
  return (
    <div className="positions-grid">
      {visible.map(item => (
        <article className="position-card" key={item.position}>
          <p className="eyebrow">{positionTitle(item.position)}</p>
          <div className="stat">{item.filled}<small> / {item.required}</small></div>
          <p>{coverageText(item.filled, item.required)}</p>
        </article>
      ))}
    </div>
  );
}

const NEW_CALLOUT_DEFAULTS: Row = {
  category: DEFAULT_CATEGORY,
  totalVolunteers: 4,
  graduateSlots: 0,
  radioSlots: 0,
  pilotSlots: 0,
  area: 'Voluntariado',
  serviceType: 'Apoyo Salud',
};

function matchesCalloutQuery(callout: Row, query: string) {
  const searchable = `${callout.service_code ?? ''} ${callout.title} ${callout.service_type} ${callout.location} ${statusLabel(callout.status)}`;
  return searchable.toLowerCase().includes(query.toLowerCase());
}

export function CalloutsPage({ user, history = false }: Readonly<{ user: Session; history?: boolean }>) {
  const { data, error, loading } = useData<Row[]>(`/callouts?history=${history}`, []);
  const [query, setQuery] = useState('');
  const [creating, setCreating] = useState(false);
  const filtered = data.filter(callout => matchesCalloutQuery(callout, query));
  const canCreate = manager(user) && !history;
  const showAnnualEnrollment = !history && user.role === 'VOLUNTARIO' && user.graduated;

  return (
    <>
      <div className="page-heading">
        <CalloutsHeading history={history}/>
        {canCreate && (
          <button type="button" className="button" onClick={() => setCreating(!creating)}>
            {creating ? 'Cerrar formulario' : 'Nueva convocatoria'}
          </button>
        )}
      </div>
      <ErrorNotice message={error}/>
      {creating && <NewCalloutForm/>}
      {showAnnualEnrollment && <AnnualEnrollmentPanel/>}
      <div className="toolbar">
        <input aria-label="Buscar convocatorias" placeholder="Código, actividad, tipo o lugar…" value={query} onChange={e => setQuery(e.target.value)}/>
        <button type="button" className="button secondary" onClick={() => setQuery('')}>Limpiar</button>
      </div>
      <div className="callout-grid">
        {filtered.map(callout => <CalloutCard key={callout.id} callout={callout}/>)}
      </div>
      {!loading && filtered.length === 0 && <div className="empty">No hay convocatorias para mostrar.</div>}
    </>
  );
}

function CalloutsHeading({ history }: Readonly<{ history: boolean }>) {
  return (
    <div>
      <p className="eyebrow">{history ? 'Tu recorrido' : 'Encuentra tu próxima actividad'}</p>
      <h1>{history ? 'Historial' : 'Convocatorias'}</h1>
      <p className="lead">{history ? 'Servicios anteriores según tus permisos.' : 'Encuentra un puesto según tu formación y disponibilidad.'}</p>
    </div>
  );
}

function NewCalloutForm() {
  const navigate = useNavigate();

  async function createDraft(values: Row) {
    const created = await api('/callouts', 'POST', values);
    await navigate(`/callouts/${created.id}`);
  }

  return (
    <section className="section-block">
      <h2>Preparar convocatoria</h2>
      <p>
        Voluntario = graduado o en formación. Total 4 y mínimo 1 graduado significa 4 personas, no 5. Responsable, piloto y
        radioperador cuentan dentro del total y pueden cubrir el mínimo de graduados. El responsable sigue siendo opcional y lo
        asigna jefatura.
      </p>
      <p>
        Código generado automáticamente, por ejemplo I-FEB-2027-024: categoría, mes/año de creación y orden de creación en esa
        categoría/año. El correlativo no vuelve a uno en marzo.
      </p>
      <DataForm fields={callFields} initial={NEW_CALLOUT_DEFAULTS} label="Crear borrador" submit={createDraft}/>
    </section>
  );
}

function CalloutCard({ callout }: Readonly<{ callout: Row }>) {
  const detailPath = `/callouts/${callout.id}`;
  const total = callout.positions.find((item: Row) => item.position === 'TOTAL');
  return (
    <article className="card callout-card">
      <div className="card-topline">
        <span className="eyebrow">{callout.service_code || 'Código pendiente'}</span>
        <span className={`badge ${callout.status === 'CANCELADA' ? 'red' : ''}`}>{statusLabel(callout.status)}</span>
      </div>
      <p className="muted">{categoryLabel(callout.service_category)} · {callout.service_type}</p>
      <h2><Link to={detailPath}>{callout.title}</Link></h2>
      <p>📍 {callout.location}</p>
      <p>🗓 {date(callout.start_at)}</p>
      <p>{formatHours(callout.planned_hours)} horas programadas</p>
      <p className="muted">
        Total: {total?.filled}/{callout.total_volunteers} voluntarios · Mínimo {callout.graduate_slots} graduados dentro del total
      </p>
      <Link className="button secondary" to={detailPath}>Ver detalle</Link>
    </article>
  );
}

export function DetailPage({ user }: Readonly<{ user: Session }>) {
  const { id } = useParams();
  const calloutId = id!;
  const { data: callout, error, loading } = useData<Row>(`/callouts/${calloutId}`, {});

  if (loading && !callout.id) return <p>Cargando convocatoria…</p>;
  if (error) return <ErrorNotice message={error}/>;

  const positionOptions = (callout.eligible_positions ?? []).map((position: string) => ({ value: position, label: positionNames[position] }));
  return (
    <div className="detail-page">
      <Link className="link" to="/callouts">← Volver a convocatorias</Link>
      <CalloutHero callout={callout}/>
      <ServiceInformation callout={callout}/>
      <VacanciesSection positions={callout.positions ?? []}/>
      <EnrollmentSection
        calloutId={calloutId}
        requirements={parseRequirements(callout.requirements)}
        positionOptions={positionOptions}
        available={canEnroll(user, callout)}
        alreadyEnrolled={!!callout.my_enrollment}
      />
      {(callout.has_photo || callout.can_upload_photo) && <TeamPhotoSection calloutId={calloutId} callout={callout}/>}
      {callout.can_view_team && <ServiceManagementArea calloutId={calloutId} user={user} callout={callout}/>}
    </div>
  );
}

function VacanciesSection({ positions }: Readonly<{ positions: Row[] }>) {
  return (
    <section className="section-block">
      <h2>Vacantes disponibles</h2>
      <p className="muted">
        Cada persona cuenta una sola vez. Responsable, piloto y radioperador están incluidos en el total. El mínimo de graduados
        y las funciones son requisitos dentro de ese total, no plazas adicionales. Voluntario admite graduados y personas en
        formación; voluntario graduado, solo graduados.
      </p>
      <Positions positions={positions}/>
    </section>
  );
}

function TeamPhotoSection({ calloutId, callout }: Readonly<{ calloutId: string; callout: Row }>) {
  const photoUrl = `/api/v1/callouts/${calloutId}/photo`;
  const isClosed = !!callout.closed_at || callout.status === 'FINALIZADA';
  return (
    <section className="section-block">
      <h2>Recuerdo del equipo</h2>
      {callout.has_photo && (
        <details className="photo-view">
          <summary>Ver foto del equipo</summary>
          <a href={photoUrl} target="_blank" rel="noreferrer">
            <img loading="lazy" src={`${photoUrl}?v=${encodeURIComponent(callout.photo_uploaded_at ?? '')}`} alt={`Equipo de ${callout.title}`}/>
          </a>
        </details>
      )}
      {callout.can_upload_photo && <PhotoAttachment id={calloutId} hasPhoto={callout.has_photo} closed={isClosed}/>}
    </section>
  );
}

type ManagementProps = Readonly<{ calloutId: string; user: Session; callout: Row }>;

function ServiceManagementArea({ calloutId, user, callout }: ManagementProps) {
  const editable = canEditCallout(callout);
  return (
    <section className="management-area" aria-label="Gestión del servicio">
      <h2>Gestión del servicio</h2>
      <p className="muted">Equipo, asignaciones y operación. Estos controles solo aparecen para jefatura o el responsable de esta convocatoria.</p>
      {editable && <CalloutAdministration calloutId={calloutId} user={user} callout={callout}/>}
      {isChief(user) && editable && <ResponsiblePanel id={calloutId} current={callout.responsible_id}/>}
      {canAssignDirectly(user, callout, editable) && <AssignmentPanel id={calloutId} requirements={callout.requirements}/>}
      <TeamPage id={calloutId} user={user} callout={callout}/>
    </section>
  );
}

function CalloutAdministration({ calloutId, user, callout }: ManagementProps) {
  const [editing, setEditing] = useState(false);
  useEffect(() => setEditing(false), [calloutId]);
  const statusPath = `/callouts/${calloutId}/status`;
  const canChooseCategory = !callout.service_code && isChief(user);
  return (
    <details className="section-block management">
      <summary>Gestionar convocatoria</summary>
      <div className="toolbar">
        {callout.status === 'BORRADOR' && <Action path={statusPath} body={{ status: 'ABIERTA' }}>Publicar</Action>}
        <button className="button secondary" onClick={() => setEditing(!editing)}>Editar datos</button>
        <Action path={statusPath} body={{ status: 'CANCELADA' }}>Cancelar convocatoria</Action>
      </div>
      {canChooseCategory && <p>Borrador histórico sin código: selecciona la categoría en Editar datos y guarda para generar su código una sola vez.</p>}
      {editing && (
        <DataForm
          fields={callFields.filter(field => field.name !== 'category' || canChooseCategory)}
          initial={toCalloutForm(callout)}
          submit={values => api(`/callouts/${calloutId}`, 'PUT', toCalloutUpdateRequest(callout, values))}
          onSaved={() => setEditing(false)}
        />
      )}
    </details>
  );
}

/** Traduce la proyección del backend (snake_case) a los nombres del formulario (camelCase). */
function toCalloutForm(callout: Row): Row {
  return {
    ...callout,
    category: callout.service_category || DEFAULT_CATEGORY,
    serviceType: callout.service_type,
    start: localInput(callout.start_at),
    end: localInput(callout.end_at),
    deadline: localInput(callout.deadline),
    totalVolunteers: callout.total_volunteers,
    graduateSlots: callout.graduate_slots,
    radioSlots: callout.radio_slots,
    pilotSlots: callout.pilot_slots,
  };
}

/** La categoría ya asignada no cambia: fija el código generado. */
function toCalloutUpdateRequest(callout: Row, values: Row): Row {
  return { ...values, category: callout.service_category || values.category || DEFAULT_CATEGORY, code: callout.service_code };
}
/** Primera asignación (PUT) o cambio auditado con motivo (POST). */
function saveResponsible(calloutId: string, current: string | undefined, values: Row) {
  if (!current) return api(`/callouts/${calloutId}/responsible`, 'PUT', { userId: values.userId });
  return api(`/callouts/${calloutId}/responsible/change`, 'POST', {
    userId: values.userId || null,
    expectedResponsibleId: current,
    reason: values.reason,
  });
}

function responsibleFields(current: string | undefined, candidates: Row[]): Field[] {
  const people = candidates.map(person => ({ value: person.id, label: `${person.name} ${person.surnames}` }));
  const userField: Field = {
    name: 'userId',
    label: current ? 'Nuevo responsable (vacío = retirar responsabilidad)' : 'Voluntario inscrito',
    optional: !!current,
    options: [{ value: '', label: current ? 'Retirar responsabilidad' : 'Selecciona una persona' }, ...people],
  };
  const reasonField: Field = { name: 'reason', label: 'Motivo obligatorio del cambio o retiro', type: 'textarea' };
  return current ? [userField, reasonField] : [userField];
}

function ResponsiblePanel({ id, current }: Readonly<{ id: string; current?: string }>) {
  const candidates = useData<Row[]>(`/callouts/${id}/responsible-candidates`, []);
  const history = useData<Row[]>(`/callouts/${id}/responsible/history`, []);
  const [changing, setChanging] = useState(false);

  async function handleSave(values: Row) {
    await saveResponsible(id, current, values);
    setChanging(false);
  }

  return (
    <details className="section-block">
      <summary>{current ? 'Responsable asignado · jefatura' : 'Asignar responsable · jefatura'}</summary>
      <p>
        Solo un voluntario graduado activo ya inscrito como voluntario general, sin cruce horario. Verifica los requisitos de la
        convocatoria. No se duplica su plaza ni se convierte al piloto/radioperador. Al retirar la responsabilidad conserva su
        inscripción, pero pierde los permisos de gestión.
      </p>
      <ErrorNotice message={candidates.error || history.error}/>
      {current && (
        <>
          <p>Ya hay un responsable. No se puede asignar otro sin registrar un cambio explícito.</p>
          <button type="button" className="button secondary" onClick={() => setChanging(!changing)}>
            {changing ? 'Cancelar cambio' : 'Cambiar o retirar responsable'}
          </button>
        </>
      )}
      {(!current || changing) && (
        <DataForm
          key={current ?? 'none'}
          fields={responsibleFields(current, candidates.data)}
          label={current ? 'Confirmar cambio auditado' : 'Asignar responsable'}
          submit={handleSave}
        />
      )}
      {history.data.length > 0 && <ResponsibleHistory changes={history.data}/>}
    </details>
  );
}

function ResponsibleHistory({ changes }: Readonly<{ changes: Row[] }>) {
  return (
    <details>
      <summary>Historial de responsables</summary>
      {changes.map(change => (
        <p key={change.id}>
          {date(change.changed_at)} · {change.changed_name}: {change.previous_name || 'Sin responsable'} →{' '}
          {change.next_name || 'Sin responsable'} · {change.reason}
        </p>
      ))}
    </details>
  );
}

const ASSIGNABLE_POSITIONS = ['SOCORRISTA', 'GRADUADO', 'RADIOPERADOR', 'PILOTO'];
const ASSIGNMENT_CONFIRMED = 'Inscripción directa confirmada. Se notificó a la persona.';

/** Quita tildes y mayúsculas para encontrar "Lucía" escribiendo "lucia". */
function normalizeForSearch(text: string) {
  return text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').trim().toLowerCase();
}

function matchesName(person: Row, query: string) {
  return normalizeForSearch(`${person.name} ${person.surnames}`).includes(normalizeForSearch(query));
}

function AssignmentPanel({ id, requirements }: Readonly<{ id: string; requirements: string }>) {
  const [position, setPosition] = useState('SOCORRISTA');
  const [userId, setUserId] = useState('');
  const [query, setQuery] = useState('');
  const [verified, setVerified] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const candidates = useData<Row[]>(`/callouts/${id}/assignment-candidates?position=${position}`, []);

  useEffect(() => {
    setUserId('');
    setQuery('');
    setVerified(false);
    setSuccess('');
  }, [id, position, requirements]);

  const matches = candidates.data.filter(person => matchesName(person, query));
  const hasCandidates = candidates.data.length > 0;

  function changeQuery(text: string) {
    setQuery(text);
    setUserId('');
    setVerified(false);
  }

  function choosePerson(personId: string) {
    setUserId(personId);
    setVerified(false);
  }

  async function handleAssign(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    setBusy(true);
    setError('');
    setSuccess('');
    try {
      await api(`/callouts/${id}/assignments`, 'POST', { userId, position, requirementsVerified: verified });
      setUserId('');
      setVerified(false);
      setSuccess(ASSIGNMENT_CONFIRMED);
      saveSucceeded(form, ASSIGNMENT_CONFIRMED);
      changed();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <details className="section-block">
      <summary>Inscribir directamente · jefatura</summary>
      <p>
        Busca por nombre y selecciona una persona elegible. Verifica conocimientos, cursos y demás requisitos de esta convocatoria.
        No se omiten graduación, habilitaciones, cupo, plazo ni cruces de horario. Se notificará a la persona.
      </p>
      <p className="multiline"><b>Requisitos que debes verificar:</b>{'\n'}{requirements}</p>
      <form className="card flow-form" onSubmit={handleAssign}>
        <label className="field">
          {'Puesto'}
          <select name="assignmentPosition" value={position} onChange={e => setPosition(e.target.value)}>
            {ASSIGNABLE_POSITIONS.map(item => <option key={item} value={item}>{positionNames[item]}</option>)}
          </select>
        </label>
        <label className="field">
          {'Buscar persona por nombre'}
          <input
            name="assignmentSearch"
            type="search"
            autoComplete="off"
            value={query}
            placeholder="Escribe nombres o apellidos…"
            onChange={e => changeQuery(e.target.value)}
          />
        </label>
        <label className="field">
          {'Persona elegible'}
          <select
            name="assignmentUser"
            required
            value={userId}
            onChange={e => choosePerson(e.target.value)}
            disabled={candidates.loading || busy}
          >
            <option value="">Selecciona una persona</option>
            {matches.map(person => (
              <option key={person.id} value={person.id}>
                {person.name} {person.surnames} · {person.graduated ? 'Graduado' : 'En formación'}
              </option>
            ))}
          </select>
        </label>
        {!candidates.loading && hasCandidates && matches.length === 0 && <p>No hay coincidencias para ese nombre.</p>}
        {!candidates.loading && !hasCandidates && <p>No hay personas elegibles para este puesto con los cupos y horarios actuales.</p>}
        <label className="requirement">
          <input name="assignmentVerified" type="checkbox" required checked={verified} onChange={e => setVerified(e.target.checked)}/>
          {'He verificado todos los requisitos declarados para esta persona.'}
        </label>
        <ErrorNotice message={error || candidates.error}/>
        {success && <p><output>{success}</output></p>}
        <button type="submit" className="button" disabled={busy || candidates.loading || !userId || !verified}>
          {busy ? 'Inscribiendo…' : 'Confirmar inscripción directa'}
        </button>
      </form>
    </details>
  );
}

function TeamPage({ id, user, callout }: Readonly<{ id: string; user: Session; callout: Row }>) {
  const applicants = useData<Row[]>(`/callouts/${id}/enrollments`, []);
  useRequestFocus(applicants.data);
  const groups = Object.keys(positionNames)
    .map(position => ({ position, enrollments: applicants.data.filter(enrollment => enrollment.position === position) }))
    .filter(group => group.enrollments.length > 0);

  return (
    <>
      <details className="section-block">
        <summary>Equipo e inscripciones</summary>
        <ErrorNotice message={applicants.error}/>
        {groups.map(group => (
          <section className="team-group" key={group.position}>
            <h3>{positionNames[group.position]}</h3>
            {group.enrollments.map(enrollment => (
              <EnrollmentCard key={enrollment.id} enrollment={enrollment} reviewable={canReviewEnrollment(user, callout, enrollment)}/>
            ))}
          </section>
        ))}
        {applicants.data.length === 0 && <p>Aún no hay participantes.</p>}
      </details>
      {callout.can_operate && (
        <>
          <OperationPanel id={id} user={user} kind={callout.kind}/>
          <MaterialsPanel id={id}/>
        </>
      )}
    </>
  );
}

function reviewEnrollment(enrollmentId: string, values: Row) {
  return api(`/enrollments/${enrollmentId}/review`, 'POST', { approved: values.decision === 'APROBAR', comment: values.comment });
}

function EnrollmentCard({ enrollment, reviewable }: Readonly<{ enrollment: Row; reviewable: boolean }>) {
  const reviewFields: Field[] = [
    { name: 'decision', label: 'Decisión', options: reviewDecisions(enrollment.status) },
    { name: 'comment', label: 'Motivo' },
  ];
  return (
    <details className="card" data-focus={`enrollment:${enrollment.id}`}>
      <summary>{enrollment.name} {enrollment.surnames} · {enrollmentStatusLabel(enrollment.status)}</summary>
      <p>Inscripción: {date(enrollment.created_at)}</p>
      {enrollment.assigned_by && <p>Asignación de jefatura: {date(enrollment.assigned_at)} · Requisitos verificados.</p>}
      {enrollment.replaced_name && <p>Reemplaza a: {enrollment.replaced_name}</p>}
      {enrollment.cancellation_requested_at && <p>Solicitó reemplazo. Jefatura gestiona la solicitud en Justificaciones.</p>}
      {reviewable && <DataForm fields={reviewFields} submit={values => reviewEnrollment(enrollment.id, values)}/>}
    </details>
  );
}

function OperationPanel({ id, user, kind }: { id: string; user: Session; kind: string }) {
  const attendance = useData<Row[]>(`/callouts/${id}/attendance`, []);
  useRequestFocus(attendance.data);
  if (kind !== 'SERVICIO') return null;
  return <><details className="section-block"><summary>Apertura y cierre del servicio</summary><p>Puede abrirse con las vacantes completas, aunque no tenga responsable ni foto.</p><DataForm fields={[{ name: 'action', label: 'Acción', options: ['ABRIR', 'CERRAR'] }, { name: 'observations', label: 'Observaciones e indicaciones', type: 'textarea' }]} submit={v => api(`/callouts/${id}/operation`, 'POST', v)}/></details>
    <details className="section-block"><summary>Revisión de asistencia</summary><p>Después de 7 días sin revisión, se usan las marcas del participante. Sin entrada y salida no se acreditan horas automáticamente.</p><ErrorNotice message={attendance.error}/>{attendance.data.map(a => <details className="card" data-focus={`attendance:${a.id}`} key={a.id}><summary>{a.name} · {a.status} · {a.approved_hours ?? '—'} h</summary><p>Entrada: {date(a.check_in)} · Salida: {date(a.check_out)}</p><p>Retraso: {delay(a.late_minutes)} · Deducción: {a.deduction} h</p><p>{a.observations}</p>
      {(a.review_requests ?? []).map((r: Row) => <section className="soft-panel" key={r.id}><h3>Solicitud de revisión · {r.status}</h3><p>{r.reason}</p><p>{r.resolution_comment || 'Pendiente de decisión'}</p>{r.status === 'PENDIENTE' && <><p>Para aprobar, confirma o corrige las marcas y horas de abajo. Para rechazar, registra el motivo sin alterar las marcas.</p><details><summary>Rechazar solicitud de revisión</summary><DataForm fields={[{ name: 'comment', label: 'Motivo del rechazo', type: 'textarea' }]} label="Rechazar revisión" submit={v => api(`/attendance-reviews/${r.id}/resolve`, 'POST', { approved: false, comment: v.comment })}/></details></>}</section>)}
      {a.month_closed ? <p>Mes aprobado: la asistencia ya no se puede modificar.</p> : <DataForm key={`${a.id}-${a.check_in}-${a.check_out}`} initial={{ checkIn: localInput(a.check_in), checkOut: localInput(a.check_out), observations: a.observations }} fields={[
      { name: 'checkIn', label: 'Entrada (hora de Perú)', type: 'datetime-local' }, { name: 'checkOut', label: 'Salida (hora de Perú)', type: 'datetime-local' },
      ...(user.role === 'JEFATURA' ? [{ name: 'hours', label: 'Horas excepcionales autorizadas (vacío = cálculo normal)', type: 'number', min: 0, max: 48, step: '0.01', optional: true }] : []),
      { name: 'observations', label: 'Motivo / observaciones', type: 'textarea' },
    ]} label="Confirmar o corregir" submit={v => api(`/attendance/${a.id}/confirm`, 'POST', v)}/>}</details>)}</details></>;
}
function MaterialsPanel({ id }: { id: string }) {
  const materials = useData<Row[]>(`/callouts/${id}/materials`, []);
  return <details className="section-block"><summary>Solicitud de materiales al almacén</summary><p>Escribe el material requerido, sin seleccionar un catálogo. No administra stock ni bloquea el servicio. Registra la entrega y devolución.</p><ErrorNotice message={materials.error}/>
    <details><summary>Añadir material</summary><DataForm fields={[
      { name: 'name', label: 'Material o referencia', max: 160 }, { name: 'quantity', label: 'Cantidad solicitada', type: 'number', min: 1, max: 10000 }, { name: 'details', label: 'Especificaciones', type: 'textarea' },
    ]} submit={v => api(`/callouts/${id}/materials`, 'POST', { ...v, materialId: null })}/></details>
    <div className="document-preview"><div className="page-heading"><div><p className="eyebrow">Vista previa digital</p><h3>Solicitud al almacén</h3></div><PdfDownload path={`/callouts/${id}/materials/pdf`} filename="solicitud-materiales.pdf"/></div>
      {!materials.data.length && <p>Sin materiales solicitados.</p>}{materials.data.map(m => <div className="preview-row" key={m.id}><b>{m.quantity} × {m.name}</b><p>{m.details}</p><small>Recibido: {m.received_quantity} · Devuelto: {m.returned_quantity}</small><p>{m.condition_note}</p></div>)}
    </div>{materials.data.map(m => <details className="card" key={m.id}><summary>Entrega / devolución · {m.name}</summary><p>Recibido: {m.received_quantity} · {m.received_name || 'Pendiente'} · {date(m.received_at)}</p><p>Devuelto: {m.returned_quantity} · {m.returned_name || 'Pendiente'} · {date(m.returned_at)}</p>{!m.returned_at && <DataForm fields={[
      { name: 'action', label: 'Movimiento', options: m.received_at ? ['DEVOLUCION'] : ['RECEPCION'] }, { name: 'quantity', label: 'Cantidad', type: 'number', min: m.received_at ? 0 : 1, max: m.received_at ? m.received_quantity : m.quantity }, { name: 'observations', label: 'Observaciones (pérdidas o daños)', type: 'textarea' },
    ]} submit={v => api(`/materials/${m.id}/handover`, 'POST', v)}/>}</details>)}
  </details>;
}
const MAX_PHOTO_MEGABYTES = 10;
const MAX_PHOTO_BYTES = MAX_PHOTO_MEGABYTES * 1024 * 1024;
const PHOTO_SAVED = 'Foto guardada.';

function assertPhotoSize(file: File) {
  if (file.size > MAX_PHOTO_BYTES) throw new Error(`Máximo ${MAX_PHOTO_MEGABYTES} MB.`);
}

function uploadTeamPhoto(calloutId: string, file: File) {
  const data = new FormData();
  data.append('file', file);
  return api(`/callouts/${calloutId}/photo`, 'POST', data);
}

function PhotoForm({ id, onSaved }: Readonly<{ id: string; onSaved: () => void }>) {
  const [file, setFile] = useState<File>();
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!file) return;
    setBusy(true);
    setMessage('');
    try {
      assertPhotoSize(file);
      await uploadTeamPhoto(id, file);
      saveSucceeded(form, PHOTO_SAVED);
      onSaved();
      changed();
      setMessage(PHOTO_SAVED);
    } catch (e) {
      setMessage((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form className="soft-panel" onSubmit={handleSubmit}>
      <label className="field">
        {`Foto opcional JPG/PNG · hasta ${MAX_PHOTO_MEGABYTES} MB y 40 megapíxeles`}
        <input type="file" accept="image/jpeg,image/png" required onChange={e => setFile(e.target.files?.[0])}/>
      </label>
      <button type="submit" className="button secondary" disabled={busy}>Guardar foto</button>
      <p><output>{message}</output></p>
    </form>
  );
}

function attachButtonLabel(open: boolean, hasPhoto: boolean) {
  if (open) return 'Cerrar adjunto';
  return hasPhoto ? 'Reemplazar foto' : 'Adjuntar foto';
}

function PhotoAttachment({ id, hasPhoto, closed }: Readonly<{ id: string; hasPhoto: boolean; closed: boolean }>) {
  const [open, setOpen] = useState(false);
  return (
    <div>
      {closed && !hasPhoto && <p><output>Servicio cerrado. Puedes adjuntar una foto del equipo ahora o más adelante; es opcional.</output></p>}
      <button type="button" className="button secondary" onClick={() => setOpen(!open)}>{attachButtonLabel(open, hasPhoto)}</button>
      {open && <PhotoForm id={id} onSaved={() => setOpen(false)}/>}
    </div>
  );
}