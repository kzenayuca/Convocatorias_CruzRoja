import { beforeEach, describe, expect, it, vi } from 'vitest';

// Cada caso importa un módulo nuevo para aislar la caché CSRF sin exponer un reset de producción.
beforeEach(() => {
  vi.resetModules();
  vi.stubGlobal('fetch', vi.fn());
});

const csrf = { token: 'demo-token', headerName: 'X-CSRF-TOKEN' };

describe('contrato HTTP y CSRF', () => {
  it('GET no solicita CSRF y devuelve el JSON', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch).mockResolvedValue(Response.json({ id: '1' }));
    expect(await api<{ id: string }>('/items/1')).toEqual({ id: '1' });
    expect(fetch).toHaveBeenCalledOnce();
  });

  it('valida, adjunta y reutiliza el token para escrituras', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json(csrf))
      .mockResolvedValueOnce(Response.json({ id: '1' }))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api('/items', 'POST', { name: 'Demo' });
    await api<void>('/items/1', 'DELETE');
    expect(fetch).toHaveBeenCalledTimes(3);
    expect(fetch).toHaveBeenNthCalledWith(
      2,
      '/api/v1/items',
      expect.objectContaining({
        headers: { 'X-CSRF-TOKEN': 'demo-token', 'Content-Type': 'application/json' },
        body: '{"name":"Demo"}',
      }),
    );
  });

  it.each([400, 401, 403, 500])('no escribe cuando CSRF devuelve HTTP %s', async (status) => {
    const { api } = await import('../api');
    vi.mocked(fetch).mockResolvedValue(Response.json({ mensaje: 'CSRF rechazado' }, { status }));
    await expect(api('/items', 'POST', {})).rejects.toThrow('CSRF rechazado');
    expect(fetch).toHaveBeenCalledOnce();
  });

  it.each([
    null,
    [],
    {},
    { token: 42, headerName: 'X-CSRF' },
    { token: '', headerName: 'X-CSRF' },
    { token: '  ', headerName: 'X-CSRF' },
    { token: 'token', headerName: '' },
    { token: 'token', headerName: 'bad header' },
    { token: 'bad\r\nvalue', headerName: 'X-CSRF' },
  ])('rechaza una estructura CSRF inesperada: %j', async (payload) => {
    const { api } = await import('../api');
    vi.mocked(fetch).mockResolvedValue(Response.json(payload));
    await expect(api('/items', 'POST')).rejects.toThrow('La respuesta CSRF no es válida');
    expect(fetch).toHaveBeenCalledOnce();
  });

  it('rechaza JSON CSRF malformado y permite reintentar', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch)
      .mockResolvedValueOnce(new Response('<html>'))
      .mockResolvedValueOnce(Response.json(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api('/items', 'POST')).rejects.toThrow('La respuesta CSRF no es válida');
    await expect(api<void>('/items', 'POST')).resolves.toBeUndefined();
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it('no fija Content-Type para FormData', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    const body = new FormData();
    body.append('reason', 'Prueba');
    await api<void>('/items', 'POST', body);
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/v1/items',
      expect.objectContaining({
        body,
        headers: { 'X-CSRF-TOKEN': 'demo-token' },
      }),
    );
  });

  it.each([null, [], { mensaje: { unsafe: 'objeto' } }, { mensaje: '' }])(
    'controla errores con cuerpo inesperado: %j',
    async (payload) => {
      const { api } = await import('../api');
      vi.mocked(fetch).mockResolvedValue(Response.json(payload, { status: 500 }));
      await expect(api('/items')).rejects.toThrow('No se pudo completar la operación.');
    },
  );

  it('controla errores sin JSON', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch).mockResolvedValue(new Response('Forbidden', { status: 403 }));
    await expect(api('/items')).rejects.toThrow('No tienes permiso');
  });

  it('controla JSON malformado en una respuesta exitosa', async () => {
    const { api } = await import('../api');
    vi.mocked(fetch).mockResolvedValue(new Response('no-json'));
    await expect(api('/items')).rejects.toThrow('respuesta JSON no válida');
  });

  it('invalida CSRF y emite expiración al recibir 401', async () => {
    const { api } = await import('../api');
    const listener = vi.fn();
    window.addEventListener('session-expired', listener);
    try {
      vi.mocked(fetch)
        .mockResolvedValueOnce(Response.json(csrf))
        .mockResolvedValueOnce(new Response(null, { status: 401 }))
        .mockResolvedValueOnce(Response.json(csrf))
        .mockResolvedValueOnce(new Response(null, { status: 204 }));
      await expect(api('/items', 'POST')).rejects.toThrow();
      expect(listener).toHaveBeenCalledOnce();
      await api('/items', 'POST');
      expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/auth/csrf');
    } finally {
      window.removeEventListener('session-expired', listener);
    }
  });

  it.each(['/auth/login', '/auth/logout'])('invalida el token después de %s', async (path) => {
    const { api } = await import('../api');
    vi.mocked(fetch)
      .mockResolvedValueOnce(Response.json(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(Response.json(csrf))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api(path, 'POST');
    await api('/items', 'POST');
    expect(fetch).toHaveBeenNthCalledWith(3, '/api/v1/auth/csrf');
  });
});
