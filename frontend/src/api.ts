import { useEffect, useState } from 'react';

export type Row = Record<string, any>;
export type Session = { id: string; name: string; email: string; role: string; graduated: boolean };
let csrf: { token: string; headerName: string } | undefined;

export async function api<T = any>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    csrf ??= await fetch('/api/v1/auth/csrf').then(r => r.json());
    headers[csrf!.headerName] = csrf!.token;
  }
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json';
  const response = await fetch(`/api/v1${path}`, { method, headers, body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body) });
  if (path === '/auth/login' || path === '/auth/logout') csrf = undefined;
  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    if (response.status === 401 && path !== '/auth/login') { csrf = undefined; window.dispatchEvent(new Event('session-expired')); }
    throw new Error(data.mensaje || (response.status === 403 ? 'No tienes permiso o tu sesión cambió. Recarga la página.' : 'No se pudo completar la operación.'));
  }
  const text = await response.text();
  return text ? JSON.parse(text) : undefined as T;
}

export function useData<T>(path: string, initial: T) {
  const [data, setData] = useState<T>(initial);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    api<T>(path).then(result => { if (active) { setData(result); setError(''); } })
      .catch(e => { if (active) setError(e.message); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [path, version]);
  useEffect(() => {
    const refresh = () => setVersion(v => v + 1);
    window.addEventListener('data-changed', refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => { window.removeEventListener('data-changed', refresh); window.clearInterval(timer); };
  }, []);
  return { data, error, loading };
}

export function changed() { window.dispatchEvent(new Event('data-changed')); }
export const date = (value: string) => value ? new Date(value).toLocaleString('es-PE', { timeZone: 'America/Lima' }) : '—';
export const delay = (minutes: number) => { const seconds = Math.round(Number(minutes) * 60); return `${Math.floor(seconds / 60)} min ${seconds % 60} s`; };
export const monthNow = () => new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit' }).format(new Date());
export function localInput(value: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'America/Lima', year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(value)).replace(' ', 'T');
}
export const manager = (user: Session) => user.role === 'JEFATURA' || user.role === 'RECLUTADOR' && user.graduated;
