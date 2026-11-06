import React, { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, changed, Row, useData } from './api';
import { ErrorNotice, saveSucceeded } from './components';

export function JustificationForm({ path, replacement = false }: { path: string; replacement?: boolean }) {
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false); const [saved, setSaved] = useState(false);
  const [chiefSearch, setChiefSearch] = useState(false);
  if (saved) return <p role="status">Solicitud enviada a Jefatura. <Link className="link" to="/justifications">Consultar estado →</Link></p>;
  return <form className="card flow-form" onSubmit={async e => {
    e.preventDefault(); const form = e.currentTarget; const data = new FormData(form); const file = data.get('file') as File;
    setBusy(true); setError('');
    try {
      if (replacement && chiefSearch) data.delete('proposedReplacementName');
      if (!file?.size) data.delete('file');
      else if (file.size > 10 * 1024 * 1024) throw new Error('La evidencia admite como máximo 10 MB.');
      await api(path, 'POST', data); saveSucceeded(form, 'Solicitud enviada a Jefatura.'); setSaved(true); changed();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }}>
    {replacement && <><p><b>Motivo: reemplazo.</b> Puedes presentar a la persona que conseguiste o solicitar que Jefatura la busque. En ambos casos, Jefatura verifica y confirma; conservas tu plaza hasta entonces.</p><label className="requirement"><input type="checkbox" checked={chiefSearch} onChange={e => setChiefSearch(e.target.checked)}/>Solicito que Jefatura busque y asigne al reemplazo</label>{!chiefSearch && <label className="field">Nombre completo de la persona propuesta como reemplazo<input name="proposedReplacementName" required maxLength={281} placeholder="Nombres y apellidos"/></label>}</>}
    <label className="field">Motivo y sustento de la solicitud<textarea name="reason" required maxLength={3000} placeholder="Explica la situación y la información que la sustenta."/></label>
    <label className="field">Evidencia opcional · foto del documento JPG/PNG, máximo 10 MB<input name="file" type="file" accept="image/jpeg,image/png"/></label>
    <p className="muted">El sustento y la evidencia son privados: solo tú y Jefatura pueden consultarlos.</p>
    <ErrorNotice message={error}/><button className="button" disabled={busy}>{busy ? 'Enviando…' : replacement ? 'Solicitar reemplazo a Jefatura' : 'Presentar justificación'}</button>
  </form>;
}

export function ReplacementResolution({ request }: { request: Row }) {
  const candidates = useData<Row[]>(`/justifications/${request.id}/replacement-candidates`, []);
  const [decision, setDecision] = useState('APROBAR'); const [query, setQuery] = useState(''); const [selected, setSelected] = useState('');
  const [verified, setVerified] = useState(false); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
  const normalize = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLocaleLowerCase().trim();
  const matches = candidates.data.filter(u => normalize(`${u.name} ${u.surnames}`).includes(normalize(query)));
  return <form className="card flow-form" onSubmit={async e => {
    e.preventDefault(); const form = e.currentTarget; const values = new FormData(form); setBusy(true); setError('');
    try {
      await api(`/justifications/${request.id}/replacement-resolution`, 'POST', { approved: decision === 'APROBAR', comment: values.get('comment'), replacementUserId: decision === 'APROBAR' ? selected : null, requirementsVerified: decision === 'APROBAR' && verified });
      saveSucceeded(form, 'Decisión registrada y notificada a la persona.'); changed();
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }}>
    <h3>Asignación y resolución · solo Jefatura</h3>
    <label className="field">Decisión<select name="replacementDecision" value={decision} onChange={e => setDecision(e.target.value)}><option value="APROBAR">Asignar reemplazo y aprobar</option><option value="RECHAZAR">Rechazar solicitud</option></select></label>
    {decision === 'APROBAR' && <><p>Selecciona una persona elegible para el mismo puesto. La asignación y la cancelación del titular se guardan juntas, sin duplicar plazas.</p>
      <label className="field">Buscar reemplazo por nombre<input name="replacementSearch" type="search" value={query} onChange={e => { setQuery(e.target.value); setSelected(''); setVerified(false); }}/></label>
      <label className="field">Reemplazo que asignará Jefatura<select name="replacementUser" required value={selected} onChange={e => { setSelected(e.target.value); setVerified(false); }} disabled={busy || candidates.loading}><option value="">Selecciona una persona</option>{matches.map(u => <option key={u.id} value={u.id}>{u.name} {u.surnames}</option>)}</select></label>
      {!candidates.loading && !matches.length && <p>No hay reemplazos elegibles con este nombre, puesto y horario. No se puede aprobar sin asignar uno.</p>}
      <label className="requirement"><input name="replacementVerified" type="checkbox" required checked={verified} onChange={e => setVerified(e.target.checked)}/>He verificado los requisitos de la convocatoria para esta persona.</label></>}
    <label className="field">Motivo obligatorio de la resolución<textarea name="comment" required maxLength={3000}/></label>
    <ErrorNotice message={error || candidates.error}/><button className="button" disabled={busy || decision === 'APROBAR' && (!selected || !verified)}>{busy ? 'Guardando…' : decision === 'APROBAR' ? 'Asignar reemplazo y aprobar' : 'Rechazar solicitud'}</button>
  </form>;
}
