import { useEffect, useState } from 'react';

// Compatibilidad con proyecciones SQL heterogéneas y formularios configurables.
// No usar este tipo para contratos nuevos: definir DTOs explícitos (p. ej. Notification).
// Se conserva este único any para no inventar un contrato uniforme para todas las proyecciones.
export type Row = Record<string, any>;
export type Session = { id: string; name: string; email: string; role: string; graduated: boolean };
export type CreatedResource = { id: string };
export type Notification = {
  id: string;
  type: string;
  message: string;
  link: string | null;
  is_read: boolean;
};
export type CsrfToken = { token: string; headerName: string };
export type ApiErrorResponse = { mensaje: string };
let csrf: CsrfToken | undefined;

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isApiErrorResponse(value: unknown): value is ApiErrorResponse {
  return isRecord(value) && typeof value.mensaje === 'string' && value.mensaje.trim().length > 0;
}

export function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : 'No se pudo completar la operación.';
}

function expireSession() {
  csrf = undefined;
  window.dispatchEvent(new Event('session-expired'));
}

async function responseError(response: Response): Promise<Error> {
  const data: unknown = await response.json().catch(() => undefined);
  const fallback =
    response.status === 403
      ? 'No tienes permiso o tu sesión cambió. Recarga la página.'
      : 'No se pudo completar la operación.';
  return new Error(isApiErrorResponse(data) ? data.mensaje : fallback);
}

async function fetchCsrf(): Promise<CsrfToken> {
  const response = await fetch('/api/v1/auth/csrf');
  if (!response.ok) {
    if (response.status === 401) expireSession();
    throw await responseError(response);
  }
  const data: unknown = await response.json().catch(() => undefined);
  if (
    !isRecord(data) ||
    typeof data.token !== 'string' ||
    !data.token.trim() ||
    /[\r\n]/.test(data.token) ||
    typeof data.headerName !== 'string' ||
    !/^[!#$%&'*+.^_`|~0-9A-Za-z-]+$/.test(data.headerName)
  ) {
    throw new Error('La respuesta CSRF no es válida. Recarga la página e inténtalo de nuevo.');
  }
  return { token: data.token, headerName: data.headerName };
}

// T documenta el contrato del consumidor; no sustituye validación en tiempo de ejecución.
// Sin contrato explícito, la respuesta es unknown, nunca any.
export async function api<T = unknown>(path: string, method = 'GET', body?: unknown): Promise<T> {
  const headers: Record<string, string> = {};
  if (method !== 'GET') {
    csrf ??= await fetchCsrf();
    headers[csrf.headerName] = csrf.token;
  }
  if (body !== undefined && !(body instanceof FormData))
    headers['Content-Type'] = 'application/json';
  const response = await fetch(`/api/v1${path}`, {
    method,
    headers,
    body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
  });
  if (path === '/auth/login' || path === '/auth/logout') csrf = undefined;
  if (!response.ok) {
    if (response.status === 401 && path !== '/auth/login') expireSession();
    throw await responseError(response);
  }
  const text = await response.text();
  if (!text) return undefined as T;
  try {
    const data: unknown = JSON.parse(text);
    return data as T;
  } catch {
    throw new Error('El servidor devolvió una respuesta JSON no válida.');
  }
}

export function useData<T>(path: string, initial: T) {
  const [data, setData] = useState<T>(initial);
  const [source, setSource] = useState(path);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError('');
    api<T>(path)
      .then((result) => {
        if (active) {
          setData(result);
          setSource(path);
          setError('');
        }
      })
      .catch((e: unknown) => {
        if (active) setError(errorMessage(e));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [path, version]);
  useEffect(() => {
    const refresh = () => setVersion((v) => v + 1);
    window.addEventListener('data-changed', refresh);
    const timer = window.setInterval(refresh, 60000);
    return () => {
      window.removeEventListener('data-changed', refresh);
      window.clearInterval(timer);
    };
  }, []);
  return { data: source === path ? data : initial, error, loading };
}

export function changed() {
  window.dispatchEvent(new Event('data-changed'));
}
export const date = (value: string) =>
  value ? new Date(value).toLocaleString('es-PE', { timeZone: 'America/Lima' }) : '—';
export const delay = (minutes: number) => {
  const seconds = Math.round(Number(minutes) * 60);
  return `${Math.floor(seconds / 60)} min ${seconds % 60} s`;
};
export const monthNow = () =>
  new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
  }).format(new Date());
export function localInput(value: string) {
  if (!value) return '';
  return new Intl.DateTimeFormat('sv-SE', {
    timeZone: 'America/Lima',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  })
    .format(new Date(value))
    .replace(' ', 'T');
}
export const manager = (user: Session) => user.role === 'JEFATURA';
