import React, { useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { api, date, Session, Row, useData } from './api';
import { DataForm, ErrorNotice, Field, useRequestFocus } from './components';
import { HoursProgress } from './HoursProgress';
import { AnnualEnrollmentPanel, GraduationReview } from './AnnualPages';

const profileFields: Field[] = [
  { name: 'name', label: 'Nombres', max: 120 },
  { name: 'surnames', label: 'Apellidos', max: 160 },
  { name: 'email', label: 'Correo electrónico', type: 'email', max: 180 },
  { name: 'document', label: 'DNI (8 dígitos)', max: 8 },
  { name: 'occupation', label: 'Profesión u ocupación', max: 160 },
];
const phoneField: Field = { name: 'phone', label: 'Teléfono', type: 'tel', max: 30 };
const registrationFields: Field[] = [
  ...profileFields,
  phoneField,
  { name: 'password', label: 'Contraseña (8 a 72 caracteres)', type: 'password', max: 72 },
];

const isChiefRole = (role: string) => role === 'JEFATURA';
const fullName = (person: Row) => `${person.name} ${person.surnames}`;

/* ---------- Inicio de sesión y solicitud de cuenta ---------- */

export function Login({ onLogin }: Readonly<{ onLogin: (session: Session) => void }>) {
  const [registering, setRegistering] = useState(false);
  return (
    <main className="login-page">
      <div className="login-card">
        <div className="brand-mark">+</div>
        <p className="eyebrow">Cruz Roja · Arequipa</p>
        <h1>{registering ? 'Solicita tu cuenta' : 'Qué bueno tenerte aquí'}</h1>
        {registering ? <RegistrationPanel/> : <LoginForm onLogin={onLogin}/>}
        <button type="button" className="icon-button" onClick={() => setRegistering(!registering)}>
          {registering ? 'Volver al inicio de sesión' : 'Solicitar una cuenta'}
        </button>
      </div>
    </main>
  );
}

function LoginForm({ onLogin }: Readonly<{ onLogin: (session: Session) => void }>) {
  const [email, setEmail] = useState('voluntario@demo.pe');
  const [password, setPassword] = useState('convocatorias');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  async function handleLogin(event: React.FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      onLogin(await api('/auth/login', 'POST', { email, password }));
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={handleLogin}>
      <p>Ingresa para continuar con tus actividades de voluntariado.</p>
      <label className="field">
        {'Correo electrónico'}
        <input type="email" value={email} onChange={e => setEmail(e.target.value)} autoComplete="username" required/>
      </label>
      <label className="field">
        {'Contraseña'}
        <input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" required/>
      </label>
      <ErrorNotice message={error}/>
      <button type="submit" className="button login-button" disabled={busy}>{busy ? 'Ingresando…' : 'Iniciar sesión'}</button>
      <DemoAccounts/>
    </form>
  );
}

function DemoAccounts() {
  return (
    <details>
      <summary>Cuentas de demostración</summary>
      <p>voluntario@demo.pe · reclutador@demo.pe · jefatura@demo.pe<br/>Contraseña: convocatorias</p>
      <p>Más perfiles: maria@demo.pe, diego@demo.pe y lucia@demo.pe.</p>
    </details>
  );
}

/** El backend espera el perfil separado del teléfono y la contraseña. */
function toRegistrationRequest(values: Row) {
  const { phone, password, ...profile } = values;
  return { profile, phone, password };
}

function RegistrationPanel() {
  const [submitted, setSubmitted] = useState(false);

  async function register(values: Row) {
    await api('/auth/register', 'POST', toRegistrationRequest(values));
    setSubmitted(true);
  }

  if (submitted) return <p><output>Solicitud registrada. Jefatura revisará y activará tu cuenta.</output></p>;
  return (
    <>
      <p>Jefatura revisará tu solicitud y habilitaciones antes de activar el acceso.</p>
      <DataForm fields={registrationFields} label="Solicitar cuenta" submit={register}/>
    </>
  );
}

/* ---------- Perfil personal ---------- */

export function ProfilePage() {
  const { data: profile, error } = useData<Row>('/profile', {});
  const requests = useData<Row[]>('/profile/changes', []);
  const photos = useData<Row[]>('/profile/photos', []);
  const [params] = useSearchParams();
  const [editing, setEditing] = useState(false);
  useRequestFocus(profile);
  const focusedRequestId = params.get('request');
  const isChief = isChiefRole(profile.role);

  return (
    <>
      <div className="page-heading">
        <div>
          <p className="eyebrow">Tu espacio personal</p>
          <h1>Mi perfil</h1>
          <p className="lead">Tus datos, formación y recuerdos del voluntariado.</p>
        </div>
        <button type="button" className="button secondary" onClick={() => setEditing(!editing)}>
          {editing ? 'Cerrar edición' : 'Modificar'}
        </button>
      </div>
      <ErrorNotice message={error || photos.error}/>
      {profile.id && (
        <>
          <ProfileHero profile={profile}/>
          <PersonalInformation profile={profile}/>
          {isChief ? <ChiefAdministrationNote/> : <QualificationsSection profile={profile}/>}
          {!isChief && <HoursProgress/>}
          {profile.role === 'VOLUNTARIO' && profile.graduated && <AnnualEnrollmentPanel/>}
          {focusedRequestId && <FocusedProfileRequest id={focusedRequestId}/>}
          {requests.data.length > 0 && <MyProfileRequests requests={requests.data} error={requests.error}/>}
          {editing && <ProfileEditSection profile={profile} onSaved={() => setEditing(false)}/>}
          {photos.data.length > 0 && <TeamPhotos callouts={photos.data}/>}
        </>
      )}
    </>
  );
}

type ProfileProps = Readonly<{ profile: Row }>;

function ProfileHero({ profile }: ProfileProps) {
  return (
    <section className="profile-hero" data-focus="section:condition">
      <span className="avatar large-avatar">{profile.name.charAt(0)}</span>
      <div>
        <h2>{fullName(profile)}</h2>
        <p>{profile.occupation || 'Voluntariado Cruz Roja'}</p>
        <span className="badge">{profile.role}</span> <span className="badge">{profile.status}</span>
        {profile.review_comment && <p>Última decisión de Jefatura: {profile.review_comment}</p>}
      </div>
    </section>
  );
}

function InfoItem({ term, children }: Readonly<{ term: string; children: React.ReactNode }>) {
  return <div><dt>{term}</dt><dd>{children}</dd></div>;
}

function PersonalInformation({ profile }: ProfileProps) {
  return (
    <section className="section-block">
      <h2>Información personal</h2>
      <dl className="info-grid">
        <InfoItem term="Correo electrónico">{profile.email}</InfoItem>
        <InfoItem term="DNI">{profile.document}</InfoItem>
        <InfoItem term="Teléfono">{profile.phone || 'No registrado'}</InfoItem>
        <InfoItem term="Profesión u ocupación">{profile.occupation || 'No registrada'}</InfoItem>
      </dl>
    </section>
  );
}

function ChiefAdministrationNote() {
  return (
    <section className="section-block">
      <h2>Administración institucional</h2>
      <p>
        Tu cuenta administra convocatorias, asistencia y horas de todos los voluntarios. No tiene meta personal, penalizaciones ni
        obligación de justificarse.
      </p>
      <Link className="link" to="/reports">Consultar horas de los voluntarios →</Link>
    </section>
  );
}

const qualificationText = (qualified: boolean | null | undefined) => (qualified ? 'Habilitado por jefatura' : 'Sin habilitación');

function QualificationsSection({ profile }: ProfileProps) {
  return (
    <section className="section-block">
      <h2>Condición y habilitaciones</h2>
      <dl className="info-grid">
        <InfoItem term="Condición">{profile.graduated ? 'Graduado' : 'En formación'}</InfoItem>
        <InfoItem term="Radioperador">{qualificationText(profile.radio_qualified)}</InfoItem>
        <InfoItem term="Piloto">{qualificationText(profile.pilot_qualified)}</InfoItem>
      </dl>
      <p>Ser graduado no otorga gestión. Los permisos de responsable solo corresponden al servicio al que jefatura te asigna.</p>
      <Link className="link" to="/services">Ver mis servicios y horas →</Link>
    </section>
  );
}

function MyProfileRequests({ requests, error }: Readonly<{ requests: Row[]; error: string }>) {
  return (
    <section className="section-block">
      <h2>Mis solicitudes de cambio de perfil</h2>
      <ErrorNotice message={error}/>
      {requests.map(request => <ProfileRequestCard key={request.id} request={request}/>)}
    </section>
  );
}

function ProfileEditSection({ profile, onSaved }: Readonly<{ profile: Row; onSaved: () => void }>) {
  return (
    <section className="section-block">
      <h2>Modificar información</h2>
      <h3>Teléfono · cambio directo</h3>
      <DataForm initial={profile} fields={[phoneField]} label="Guardar teléfono" submit={values => api('/profile/phone', 'PUT', values)} onSaved={onSaved}/>
      <h3>Otros datos · requieren aprobación</h3>
      <p>Mientras jefatura revisa la solicitud, se conservan tus datos actuales.</p>
      <DataForm
        key={`${profile.name}-${profile.email}`}
        initial={profile}
        fields={profileFields}
        label="Solicitar cambio"
        submit={values => api('/profile/changes', 'POST', values)}
        onSaved={onSaved}
      />
    </section>
  );
}

function TeamPhotos({ callouts }: Readonly<{ callouts: Row[] }>) {
  return (
    <section className="section-block">
      <h2>Mis fotos de equipo</h2>
      <p className="muted">Servicios en los que participaste. Solo el responsable asignado y jefatura suben fotos.</p>
      <div className="callout-grid">
        {callouts.map(callout => (
          <article className="card" key={callout.id}>
            <h3>{callout.title}</h3>
            <p>{date(callout.start_at)}</p>
            <details className="photo-view">
              <summary>Ver foto</summary>
              <img loading="lazy" src={`/api/v1/callouts/${callout.id}/photo`} alt={`Equipo de ${callout.title}`}/>
            </details>
            <Link className="link" to={`/callouts/${callout.id}`}>Ver convocatoria →</Link>
          </article>
        ))}
      </div>
    </section>
  );
}

/* ---------- Administración institucional ---------- */

const hoursText = (hours: unknown) => (hours ? `${hours} h` : 'Sin configurar');

const hourTargetFields: Field[] = [
  { name: 'trainingHours', label: 'Meta de formación (saldo neto acumulado entre años)', type: 'number', min: 0.01, max: 100000, step: '0.01' },
  { name: 'graduateHours', label: 'Meta de graduados (saldo neto del año)', type: 'number', min: 0.01, max: 100000, step: '0.01' },
];

export function AdministrationPage() {
  const settings = useData<Row>('/administration', {});
  const codes = useData<Row[]>('/service-codes', []);
  return (
    <>
      <p className="eyebrow">Jefatura</p>
      <h1>Administración</h1>
      <p className="lead">Requisitos institucionales e historial de códigos.</p>
      <ErrorNotice message={settings.error || codes.error}/>
      <HourTargetsSection targets={settings.data}/>
      <ServiceCodesSection codes={codes.data}/>
    </>
  );
}

function HourTargetsSection({ targets }: Readonly<{ targets: Row }>) {
  /** Espera a que lleguen las metas para no mostrar un formulario vacío. */
  const hasHourTargets = 'training_hours' in targets && 'graduate_hours' in targets;
  return (
    <section className="section-block">
      <h2>Metas de horas por condición</h2>
      <p>
        Formación: saldo neto acumulado entre años hasta alcanzar la meta (por ejemplo, 200 h); Jefatura aprueba la graduación, no es
        automática. Graduados: seguimiento anual enero-diciembre e inscripción anual independiente. Cambiar metas no borra horas ni
        cierres.
      </p>
      <dl className="info-grid">
        <InfoItem term="En formación · acumulado histórico">{hoursText(targets.training_hours)}</InfoItem>
        <InfoItem term="Graduados · meta anual">{hoursText(targets.graduate_hours)}</InfoItem>
      </dl>
      {hasHourTargets && (
        <details>
          <summary>Editar metas de horas</summary>
          <DataForm
            key={`${targets.training_hours}-${targets.graduate_hours}`}
            fields={hourTargetFields}
            initial={{ trainingHours: targets.training_hours, graduateHours: targets.graduate_hours }}
            label="Guardar metas generales"
            submit={values => api('/administration', 'PUT', values)}
          />
        </details>
      )}
    </section>
  );
}

function ServiceCodesSection({ codes }: Readonly<{ codes: Row[] }>) {
  const [type, setType] = useState('');
  const types = [...new Set(codes.map(code => code.service_type))];
  const visible = codes.filter(code => !type || code.service_type === type);
  return (
    <section className="section-block">
      <h2>Códigos recientes por tipo</h2>
      <p>
        Automáticos por categoría y año, en orden de creación: E (Externo), I (Interno), G (Guardia). Ejemplo: I-FEB-2027-024. No se
        reinician cada mes ni cambian al editar fechas; los códigos históricos no se renumeran.
      </p>
      <label className="field">
        {'Descripción del servicio'}
        <select value={type} onChange={e => setType(e.target.value)}>
          <option value="">Todos los tipos</option>
          {types.map(item => <option key={item}>{item}</option>)}
        </select>
      </label>
      {visible.map(code => (
        <article className="preview-row" key={code.id}>
          <Link className="link" to={`/callouts/${code.callout_id}`}>{code.service_code}</Link>
          <p>{code.service_type} · {date(code.assigned_at)}</p>
          <small>{code.assigned_name || 'Registro migrado'}</small>
        </article>
      ))}
    </section>
  );
}

/* ---------- Solicitudes de cambio de perfil ---------- */

function FocusedProfileRequest({ id, chief = false }: Readonly<{ id: string; chief?: boolean }>) {
  const { data, error } = useData<Row>(`/profile-requests/${id}`, {});
  useRequestFocus(data);
  return (
    <section className="section-block">
      <h2>Solicitud seleccionada</h2>
      <ErrorNotice message={error}/>
      {data.id && <ProfileRequestCard request={data} chief={chief}/>}
    </section>
  );
}

const profileDecisionFields: Field[] = [
  { name: 'decision', label: 'Decisión sobre el cambio de perfil', options: ['APROBAR', 'RECHAZAR'] },
  { name: 'comment', label: 'Motivo de la decisión', type: 'textarea' },
];

function resolveProfileChange(request: Row, values: Row) {
  return api(`/users/${request.user_id}/changes`, 'POST', {
    approved: values.decision === 'APROBAR',
    comment: values.comment,
    requestId: request.id,
  });
}

function ProfileRequestCard({ request, chief = false }: Readonly<{ request: Row; chief?: boolean }>) {
  const canResolve = chief && request.status === 'PENDIENTE';
  return (
    <details className="card" data-focus={`${chief ? 'change' : 'request'}:${request.id}`}>
      <summary>Cambio de perfil · {fullName(request)} · {request.status}</summary>
      <p>Propuesta: {request.email} · DNI {request.document} · {request.occupation}</p>
      <p>Presentada: {date(request.submitted_at)}</p>
      <p>Resolución: {request.resolution_comment || 'Pendiente de Jefatura'}</p>
      {request.resolved_at && <p>{request.resolved_name} · {date(request.resolved_at)}</p>}
      {canResolve && (
        <DataForm
          fields={profileDecisionFields}
          label="Resolver cambio de perfil"
          submit={values => resolveProfileChange(request, values)}
        />
      )}
    </details>
  );
}

/* ---------- Usuarios (jefatura) ---------- */

const YES = 'SI';
const NO = 'NO';
const toYesNo = (value: unknown) => (value ? YES : NO);
const fromYesNo = (value: unknown) => value === YES;

const userReviewFields: Field[] = [
  { name: 'role', label: 'Rol de acceso (responsable se asigna por servicio)', options: ['VOLUNTARIO', 'JEFATURA'] },
  { name: 'graduated', label: 'Graduado', options: [NO, YES] },
  { name: 'status', label: 'Estado', options: ['PENDIENTE', 'ACTIVO', 'RECHAZADO', 'INACTIVO', 'SUSPENDIDO'] },
  { name: 'comment', label: 'Motivo de la decisión' },
  { name: 'radioQualified', label: 'Habilitación de radioperador verificada', options: [NO, YES] },
  { name: 'pilotQualified', label: 'Habilitación de piloto verificada', options: [NO, YES] },
];

/** Los selectores usan SI/NO; el formulario los muestra desde los booleanos del usuario. */
function toUserReviewForm(user: Row): Row {
  return {
    role: user.role,
    graduated: toYesNo(user.graduated),
    radioQualified: toYesNo(user.radio_qualified),
    pilotQualified: toYesNo(user.pilot_qualified),
    status: user.status,
    comment: user.review_comment,
  };
}

/** El backend espera booleanos en lugar de SI/NO. */
function toUserReviewRequest(values: Row): Row {
  return {
    ...values,
    graduated: fromYesNo(values.graduated),
    radioQualified: fromYesNo(values.radioQualified),
    pilotQualified: fromYesNo(values.pilotQualified),
  };
}

function matchesUserQuery(user: Row, query: string) {
  return `${fullName(user)} ${user.email} ${user.document} ${user.role} ${user.status}`.toLowerCase().includes(query.toLowerCase());
}

export function UsersPage() {
  const { data, error } = useData<Row[]>('/users', []);
  const changes = useData<Row[]>('/users/changes', []);
  const [query, setQuery] = useState('');
  const [params] = useSearchParams();
  useRequestFocus(data);
  const focusedUserId = params.get('user');
  const focusedChangeId = params.get('change');
  const visibleUsers = data.filter(user => user.id === focusedUserId || matchesUserQuery(user, query));
  const otherChanges = changes.data.filter(change => change.request_id !== focusedChangeId);

  return (
    <>
      <p className="eyebrow">Jefatura</p>
      <h1>Usuarios y solicitudes</h1>
      <ErrorNotice message={error || changes.error}/>
      <input aria-label="Buscar usuarios" placeholder="Nombre, DNI, rol o estado…" value={query} onChange={e => setQuery(e.target.value)}/>
      {focusedChangeId && <FocusedProfileRequest id={focusedChangeId} chief/>}
      {otherChanges.map(change => (
        <ProfileRequestCard key={change.request_id} request={{ ...change, id: change.request_id, status: 'PENDIENTE' }} chief/>
      ))}
      <div className="list">
        {visibleUsers.map(user => <UserCard key={`${user.id}-${user.status}-${user.role}`} user={user}/>)}
      </div>
    </>
  );
}

function UserCard({ user }: Readonly<{ user: Row }>) {
  const isVolunteer = user.role === 'VOLUNTARIO';
  return (
    <details className="card" data-focus={`user:${user.id}`}>
      <summary>{fullName(user)} · {user.status} · {user.role}</summary>
      <p>{user.email} · DNI {user.document} · {user.phone} · {user.occupation}</p>
      {isVolunteer && !user.graduated && <GraduationReview userId={user.id}/>}
      <DataForm
        initial={toUserReviewForm(user)}
        fields={userReviewFields}
        submit={values => api(`/users/${user.id}`, 'PUT', toUserReviewRequest(values))}
      />
      {isVolunteer && user.graduated && <AnnualEnrollmentPanel userId={user.id}/>}
    </details>
  );
}