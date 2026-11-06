import React from 'react';
import { Row, useData } from './api';
import { ErrorNotice } from './components';

export function HoursProgress() {
  const { data, error } = useData<Row>('/attendance/progress', {}); const p = data.progress;
  if (data.applicable === false) return null;
  const condition = data.condition === 'GRADUADO' ? 'Graduado' : 'En formación';
  return <section className="section-block hours-focus"><p className="eyebrow">Tu avance importa</p><h2>Mi meta de horas</h2><ErrorNotice message={error}/>{p && <>
    <p>Condición actual: <b>{condition}</b> · Meta general de saldo acumulado, no mensual.</p>
    <div className="hours-total">{p.balance} <small>horas de saldo acumulado</small></div>
    <p>{p.required == null ? `Jefatura todavía no configuró la meta para ${condition.toLowerCase()}.` : `Meta: ${p.required} h · Faltan ${p.remaining} h de saldo para alcanzarla.`}</p>
    {p.required != null && <progress aria-label={`Avance de horas · ${condition}`} max={p.required} value={Math.max(0, Math.min(p.required, p.balance))}/>}
    <details><summary>Ver detalle de horas</summary><dl className="info-grid"><div><dt>Reconocidas</dt><dd>{p.earned} h</dd></div><div><dt>Deducciones</dt><dd>{p.deductions} h</dd></div><div><dt>Deuda actual</dt><dd>{p.debt} h</dd></div><div><dt>Registros pendientes / incompletos</dt><dd>{p.pending}</dd></div></dl>
      <p>La meta depende de tu condición, no de tu rol de acceso. Cambiarla no altera horas ya registradas, no reinicia tu saldo ni te gradúa automáticamente. Una justificación aprobada elimina la penalización, no añade tiempo no trabajado.</p>
      {(data.months ?? []).map((m: Row) => <div className="preview-row" key={m.month}><b>{m.month} · {m.hours} h</b><p>Reconocidas: {m.earned} h · Deducciones: {m.deductions} h · Pendientes: {m.pending} {m.closed && '· Aprobado'}</p></div>)}
    </details>
  </>}</section>;
}
