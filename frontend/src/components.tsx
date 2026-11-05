import React, { useState } from 'react';
import { api, changed, Row } from './api';

export function ErrorNotice({ message }: { message?: string }) {
  return message ? <p className="form-error" role="alert">{message}</p> : null;
}
export function Action({ path, body = {}, children, method = 'POST' }: { path: string; body?: unknown; children: React.ReactNode; method?: string }) {
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  return <span><button className="button secondary" disabled={busy} onClick={async () => {
    setBusy(true); setError('');
    try { await api(path, method, body); changed(); } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }}>{busy ? 'Guardando…' : children}</button><ErrorNotice message={error}/></span>;
}
export type Field = { name: string; label: string; type?: string; options?: (string | { value: string; label: string })[]; optional?: boolean; min?: number; max?: number; step?: string };
export function DataForm({ fields, initial = {}, submit, label = 'Guardar' }: { fields: Field[]; initial?: Row; submit: (values: Row) => Promise<unknown>; label?: string }) {
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [busy, setBusy] = useState(false);
  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const values: Row = Object.fromEntries(new FormData(event.currentTarget));
    fields.forEach(f => { if (f.type === 'number') values[f.name] = values[f.name] === '' ? null : Number(values[f.name]); });
    setBusy(true); setError(''); setSuccess('');
    try { await submit(values); changed(); setSuccess('Guardado correctamente.'); }
    catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }
  return <form className="card flow-form" onSubmit={onSubmit}>
    {fields.map(f => <label className="field" key={f.name}>{f.label}
      {f.options ? <select name={f.name} required={!f.optional} defaultValue={initial[f.name] ?? (typeof f.options[0] === 'string' ? f.options[0] : f.options[0]?.value)}>{f.options.map(o => <option key={typeof o === 'string' ? o : o.value} value={typeof o === 'string' ? o : o.value}>{typeof o === 'string' ? o : o.label}</option>)}</select>
        : f.type === 'textarea' ? <textarea name={f.name} defaultValue={initial[f.name] ?? ''} required={!f.optional} maxLength={f.max ?? 3000}/>
        : <input name={f.name} type={f.type ?? 'text'} defaultValue={initial[f.name] ?? ''} required={!f.optional} min={f.min} max={f.type === 'number' ? f.max : undefined} step={f.step} maxLength={f.type === 'number' ? undefined : f.max ?? 200} />}
    </label>)}
    <ErrorNotice message={error}/>{success && <p role="status">{success}</p>}
    <button className="button" disabled={busy}>{busy ? 'Guardando…' : label}</button>
  </form>;
}
