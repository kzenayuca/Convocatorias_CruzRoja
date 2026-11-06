import React, { useEffect, useState } from 'react';
import { useLocation } from 'react-router-dom';
import { api, changed, Row } from './api';

/** Cierra solo después del éxito; los datos/error permanecen disponibles para reabrir. */
export function saveSucceeded(origin: HTMLElement, message = 'Guardado correctamente.') {
  let parent: HTMLElement | null = origin;
  let outer: HTMLDetailsElement | undefined;
  while (parent) { if (parent instanceof HTMLDetailsElement) { parent.open = false; outer = parent; } parent = parent.parentElement; }
  outer?.querySelector('summary')?.focus({ preventScroll: true });
  window.dispatchEvent(new CustomEvent('save-feedback', { detail: message }));
}
export function SaveFeedback() {
  const [notice, setNotice] = useState({ text: '', id: 0 });
  useEffect(() => { const listener = (e: Event) => setNotice(old => ({ text: (e as CustomEvent<string>).detail, id: old.id + 1 })); window.addEventListener('save-feedback', listener); return () => window.removeEventListener('save-feedback', listener); }, []);
  return notice.text ? <div className="save-feedback" role="status" key={notice.id}>{notice.text} <button className="icon-button" type="button" aria-label="Cerrar aviso de guardado" onClick={() => setNotice(old => ({ ...old, text: '' }))}>×</button></div> : null;
}
let handledNavigation = '';
/** Se abre una vez por navegación explícita, no al refrescar datos ni tras guardar. */
export function useRequestFocus(ready: unknown) {
  const location = useLocation();
  useEffect(() => {
    const token = `${location.key}:${location.pathname}:${location.search}`; if (handledNavigation === token) return;
    const params = new URLSearchParams(location.search);
    for (const type of ['request', 'change', 'user', 'review', 'attendance', 'enrollment', 'section']) {
      const value = params.get(type); if (!value) continue;
      const target = Array.from(document.querySelectorAll<HTMLElement>('[data-focus]')).find(e => e.dataset.focus === `${type}:${value}`);
      if (!target) continue;
      let parent: HTMLElement | null = target;
      while (parent) { if (parent instanceof HTMLDetailsElement) parent.open = true; parent = parent.parentElement; }
      if (target instanceof HTMLDetailsElement) target.open = true;
      target.querySelectorAll<HTMLDetailsElement>('details[data-primary-form]').forEach(element => { element.open = true; });
      target.classList.add('request-highlight'); target.scrollIntoView({ block: 'center', behavior: 'auto' });
      if (target instanceof HTMLDetailsElement) target.querySelector('summary')?.focus({ preventScroll: true });
      handledNavigation = token; break;
    }
  }, [location.key, location.pathname, location.search, ready]);
}

export function ErrorNotice({ message }: { message?: string }) {
  return message ? <p className="form-error" role="alert">{message}</p> : null;
}
export function PdfDownload({ path, filename }: { path: string; filename: string }) {
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  return <span><button className="button secondary" disabled={busy} onClick={async () => {
    setBusy(true); setError('');
    try {
      const response = await fetch(`/api/v1${path}`);
      if (!response.ok) { const data = await response.json().catch(() => ({})); throw new Error(data.mensaje || 'No se pudo descargar el PDF.'); }
      const url = URL.createObjectURL(await response.blob()); const link = document.createElement('a');
      link.href = url; link.download = filename; document.body.append(link); link.click(); link.remove(); window.setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }}>{busy ? 'Preparando PDF…' : 'Descargar PDF ↓'}</button><ErrorNotice message={error}/></span>;
}
export function Action({ path, body = {}, children, method = 'POST' }: { path: string; body?: unknown; children: React.ReactNode; method?: string }) {
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false);
  return <span><button className="button secondary" disabled={busy} onClick={async event => {
    const eventOrigin = event.currentTarget; setBusy(true); setError('');
    try { await api(path, method, body); saveSucceeded(eventOrigin); changed(); } catch (e) { setError((e as Error).message); } finally { setBusy(false); }
  }}>{busy ? 'Guardando…' : children}</button><ErrorNotice message={error}/></span>;
}
export type Field = { name: string; label: string; type?: string; options?: (string | { value: string; label: string })[]; optional?: boolean; min?: number; max?: number; step?: string };
export function DataForm({ fields, initial = {}, submit, label = 'Guardar', onSaved }: { fields: Field[]; initial?: Row; submit: (values: Row) => Promise<unknown>; label?: string; onSaved?: () => void }) {
  const [error, setError] = useState(''); const [success, setSuccess] = useState(''); const [busy, setBusy] = useState(false);
  async function onSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const values: Row = Object.fromEntries(new FormData(form));
    fields.forEach(f => { if (f.type === 'number') values[f.name] = values[f.name] === '' ? null : Number(values[f.name]); });
    setBusy(true); setError(''); setSuccess('');
    try { await submit(values); saveSucceeded(form); onSaved?.(); changed(); setSuccess('Guardado correctamente.'); }
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
