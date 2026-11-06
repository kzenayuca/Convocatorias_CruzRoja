import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { date, localInput, Row, Session, useData } from './api';
import { ErrorNotice, PdfDownload } from './components';
import { positionNames, Positions } from './ServicePages';

export function SchedulePage({ user }: { user: Session }) {
  const [day, setDay] = useState(localInput(new Date().toISOString()).slice(0, 10));
  const calls = useData<Row[]>(`/schedule?date=${encodeURIComponent(day)}`, []); const chief = user.role === 'JEFATURA';
  return <><div className="page-heading"><div><p className="eyebrow">{chief ? 'Panorama de toda la filial' : 'Tu agenda de voluntariado'}</p><h1>{chief ? 'Programación general' : 'Mi programación'}</h1><p className="lead">{chief ? 'Servicios, equipos y cobertura para organizar cada jornada.' : 'Solo servicios en los que tu participación está aceptada.'}</p></div></div>
    <div className="toolbar"><label className="field">Fecha · hora de Perú<input aria-label="Fecha de programación" type="date" required value={day} onChange={e => { if (e.target.value) setDay(e.target.value); }}/></label>{chief && <PdfDownload path={`/schedule/pdf?date=${day}`} filename={`programacion-${day}.pdf`}/>}</div>
    <ErrorNotice message={calls.error}/>{chief && <p className="muted">Responsable, piloto, radioperador y graduados están incluidos en el total; los requisitos no se suman como plazas adicionales.</p>}{calls.loading ? <p>Cargando programación…</p> : <><p className="muted">{calls.data.length} servicios · Vista previa digital</p>
      {calls.data.map(c => <article className="section-block schedule-card" key={c.id}>
        <Link className="schedule-link" to={`/callouts/${c.id}`}><div className="card-topline"><p className="eyebrow">{c.service_code || 'Código pendiente'} · {c.service_type}</p><span className="badge">{c.execution}</span></div><h2>{c.title} <span aria-hidden="true">→</span></h2><p>{date(c.start_at)} — {date(c.end_at)}</p><p>📍 {c.location}</p><p>Responsable: {c.responsible_name || 'Sin asignar'}</p>{!chief && c.my_enrollment && <p className="participation-note">Mi puesto: {positionNames[c.my_enrollment.position]} · Inscripción aceptada</p>}</Link>
        {chief && <><p className="pill">Cobertura: {c.coverage === 'COMPLETA' ? 'Completa' : 'Puestos pendientes'}</p><Positions positions={c.positions}/><Link className="link" to={`/callouts/${c.id}`}>Consultar equipo y detalles →</Link></>}
      </article>)}{!calls.data.length && <div className="empty">{chief ? 'No hay servicios para esta fecha.' : 'No tienes servicios aceptados para esta fecha.'}<p><Link className="link" to="/callouts">Explorar convocatorias →</Link></p></div>}
    </>}
  </>;
}
