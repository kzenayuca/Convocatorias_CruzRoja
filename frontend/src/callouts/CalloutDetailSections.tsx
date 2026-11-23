import React, { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, date, Row } from '../api';
import { DataForm } from '../components';
import { categoryLabel, formatHours, positionNames, statusLabel } from './calloutLabels';
import { enrollmentStatusLabel } from './calloutRules';

type CalloutProps = Readonly<{ callout: Row }>;

export function CalloutHero({ callout }: CalloutProps) {
  return (
    <section className="service-hero">
      <div className="card-topline">
        <p className="eyebrow">{callout.service_code || 'Código pendiente de jefatura'} · {callout.area}</p>
        <span className="badge">{statusLabel(callout.status)}</span>
      </div>
      <h1>{callout.title}</h1>
      <p className="lead">{callout.description}</p>
      <div className="toolbar">
        <span className="pill">{categoryLabel(callout.service_category)}</span>
        <span className="pill">{callout.service_type}</span>
        <span className="pill">{callout.execution}</span>
        <span className="pill">Cobertura {callout.coverage === 'COMPLETA' ? 'completa' : 'pendiente'}</span>
      </div>
      {callout.my_enrollment && <MyEnrollmentNote enrollment={callout.my_enrollment}/>}
    </section>
  );
}

function MyEnrollmentNote({ enrollment }: Readonly<{ enrollment: Row }>) {
  return (
    <p className="participation-note">
      Tu inscripción: <b>{enrollmentStatusLabel(enrollment.status)}</b> · {positionNames[enrollment.position]}{' '}
      <Link className="link" to="/services">Consultar mi inscripción →</Link>
    </p>
  );
}

function InfoItem({ term, children }: Readonly<{ term: string; children: React.ReactNode }>) {
  return <div><dt>{term}</dt><dd>{children}</dd></div>;
}

export function ServiceInformation({ callout }: CalloutProps) {
  return (
    <section className="section-block">
      <h2>Información del servicio</h2>
      <dl className="info-grid">
        <InfoItem term="Inicio · hora de Perú">{date(callout.start_at)}</InfoItem>
        <InfoItem term="Fin · hora de Perú">{date(callout.end_at)}</InfoItem>
        <InfoItem term="Lugar">{callout.location}</InfoItem>
        <InfoItem term="Horas programadas">{formatHours(callout.planned_hours)} h</InfoItem>
        <InfoItem term="Responsable">{callout.responsible_name || 'Sin asignar'}</InfoItem>
        <InfoItem term="Contacto">{callout.contact || 'Consultar a jefatura'}</InfoItem>
        <InfoItem term="Inscripciones hasta">{date(callout.deadline)}</InfoItem>
      </dl>
      {callout.observations && (
        <div className="soft-panel">
          <h3>Indicaciones</h3>
          <p className="multiline">{callout.observations}</p>
        </div>
      )}
    </section>
  );
}

type PositionOption = { value: string; label: string };

type EnrollmentSectionProps = Readonly<{
  calloutId: string;
  requirements: string[];
  positionOptions: PositionOption[];
  available: boolean;
  alreadyEnrolled: boolean;
}>;

type ChecklistProps = Readonly<{
  calloutId: string;
  requirements: string[];
  positionOptions: PositionOption[];
}>;

export function EnrollmentSection({ calloutId, requirements, positionOptions, available, alreadyEnrolled }: EnrollmentSectionProps) {
  const canEnrollNow = available && positionOptions.length > 0;
  return (
    <section className="section-block">
      <h2>Verificación de requisitos</h2>
      <p>
        Los conocimientos, cursos y demás condiciones se definen en esta convocatoria; jefatura verifica las declaraciones.
        El sistema comprueba condición de graduación, habilitaciones, cupo y horario.
      </p>
      {canEnrollNow
        ? <RequirementsChecklist calloutId={calloutId} requirements={requirements} positionOptions={positionOptions}/>
        : <ReadOnlyRequirements requirements={requirements} alreadyEnrolled={alreadyEnrolled}/>}
    </section>
  );
}

function RequirementsChecklist({ calloutId, requirements, positionOptions }: ChecklistProps) {
  const [checked, setChecked] = useState<string[]>([]);
  const requirementsKey = requirements.join('\n');
  useEffect(() => setChecked([]), [calloutId, requirementsKey]);

  const toggleRequirement = (requirement: string, isChecked: boolean) =>
    setChecked(previous => isChecked ? [...previous, requirement] : previous.filter(item => item !== requirement));
  const enroll = (values: Row) =>
    api(`/callouts/${calloutId}/enrollments`, 'POST', { requirementsAccepted: true, position: values.position });
  const allAccepted = requirements.every(requirement => checked.includes(requirement));

  return (
    <>
      {requirements.map(requirement => (
        <label className="requirement" key={requirement}>
          <input type="checkbox" checked={checked.includes(requirement)} onChange={e => toggleRequirement(requirement, e.target.checked)}/>
          {requirement}
        </label>
      ))}
      {allAccepted
        ? <DataForm fields={[{ name: 'position', label: 'Puesto', options: positionOptions }]} label="Inscribirme" submit={enroll}/>
        : <p>Marca todos los requisitos para continuar.</p>}
      <p className="muted">
        Inscripción automática para cuentas validadas. Los reemplazos se solicitan en Mis servicios y solo Jefatura puede asignarlos.
      </p>
    </>
  );
}

function ReadOnlyRequirements({ requirements, alreadyEnrolled }: Readonly<{ requirements: string[]; alreadyEnrolled: boolean }>) {
  return (
    <>
      <ul className="requirements-list">{requirements.map(requirement => <li key={requirement}>{requirement}</li>)}</ul>
      <p className="muted">
        {alreadyEnrolled
          ? 'Consulta el estado de tu participación en Mis servicios.'
          : 'No hay una inscripción disponible para tu perfil en este momento.'}
      </p>
    </>
  );
}