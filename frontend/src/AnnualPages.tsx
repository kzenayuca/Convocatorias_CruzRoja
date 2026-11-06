import React, { useState } from 'react';
import { api, date, Row, useData } from './api';
import { DataForm, ErrorNotice, PdfDownload } from './components';

export function GraduationReview({ userId }: { userId: string }) {
  const { data, error } = useData<Row>(`/users/${userId}/graduation-progress`, {});
  return <div className="soft-panel"><h3>Evaluación de graduación</h3><ErrorNotice message={error}/><p>Saldo neto acumulado entre años: <b>{data.balance ?? '—'} h</b> · Meta de formación: <b>{data.target ?? 'Sin configurar'} h</b>.</p><p>{data.target != null && data.balance >= data.target ? 'Meta alcanzada: Jefatura puede aprobar la graduación indicando el motivo.' : 'Todavía no alcanza la meta; no puede aprobarse la graduación.'}</p></div>;
}

export function AnnualEnrollmentPanel({ userId }: { userId?: string }) {
  const path = userId ? `/users/${userId}/annual-enrollments` : '/profile/annual-enrollments';
  const { data, error } = useData<Row>(path, {});
  const year = Number(new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Lima', year: 'numeric' }).format(new Date()));
  if (data.applicable === false) return null;
  return <section className="soft-panel" data-focus="section:annual"><h2>Inscripción anual de graduados</h2><ErrorNotice message={error}/><p>Ciclo del 1 de enero al 31 de diciembre. La inscripción anual habilita los servicios de ese año; no borra horas ni cambia inscripciones anteriores. Para una actividad de otro año, inscríbete también en ese ciclo.</p>
    {(data.enrollments ?? []).map((a: Row) => <p key={a.year}><b>{a.year} · Inscrito</b> · {date(a.registered_at)} {a.source === 'MIGRACION' && '· Compromisos históricos conservados'}</p>)}
    <details><summary>Registrar o renovar ciclo anual</summary><DataForm fields={[{ name: 'year', label: 'Año del ciclo', type: 'number', min: year, max: 9999 }]} initial={{ year }} label="Registrar inscripción anual" submit={v => api(path, 'POST', v)}/></details>
  </section>;
}

export function AnnualReport({ general = false }: { general?: boolean }) {
  const currentYear = Number(new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Lima', year: 'numeric' }).format(new Date())); const [year, setYear] = useState(String(currentYear));
  const validYear = Number.isInteger(Number(year)) && Number(year) >= 1900 && Number(year) <= 9999;
  const base = general ? '/reports/annual' : '/attendance/year';
  const { data, error } = useData<any>(`${base}?year=${validYear ? year : currentYear}`, general ? [] : {});
  const rows: Row[] = !validYear ? [] : general ? data : data.year ? [{ ...data, id: 'me', name: 'Mi saldo anual' }] : [];
  return <section className="section-block"><h2>{general ? 'Reporte anual de voluntarios' : 'Mis horas del año'}</h2><p>Enero-diciembre: saldo neto acumulado del año y desglose mensual. La formación conserva además su acumulado histórico hasta graduarse. Los ajustes pertenecen al mes original, sin sumarse dos veces.</p>
    <div className="toolbar"><label className="field">Año del reporte<input aria-label="Año del reporte" type="number" min={1900} max={9999} value={year} onChange={e => setYear(e.target.value)}/></label>{validYear ? <PdfDownload path={`${base}/pdf?year=${year}`} filename={`reporte-anual-${year}.pdf`}/> : <p>Introduce un año entre 1900 y 9999.</p>}</div><ErrorNotice message={error}/>
    {rows.map(r => <details className="card" key={r.id}><summary>{r.name} · Saldo anual: {r.hours} h</summary><p>Reconocidas: {r.earned} h · Penalizaciones: {r.deductions} h · Pendientes: {r.pending}</p>{r.months.map((m: Row) => <div className="preview-row" key={m.month}><b>{m.month} · Saldo: {m.hours} h</b><p>Reconocidas: {m.earned} h · Penalizaciones: {m.deductions} h · Pendientes: {m.pending}{m.closed && ` · Mes aprobado · Ajustes: ${m.adjustment} h`}</p></div>)}</details>)}
  </section>;
}
