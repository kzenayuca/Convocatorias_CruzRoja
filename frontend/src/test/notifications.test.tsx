import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { NotificationsPage } from '../ActivityPages';
import { Session } from '../api';

const user: Session = {
  id: 'u1',
  name: 'Lizeth',
  email: 'test@example.com',
  role: 'VOLUNTARIO',
  graduated: false,
};
const notices = [
  {
    id: 'n1',
    type: 'ASISTENCIA',
    message: 'Revisa tu asistencia',
    link: '/services',
    is_read: false,
  },
];
let failRead: boolean;

beforeEach(() => {
  failRead = false;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL) => {
      const path = String(input);
      if (path.endsWith('/auth/csrf'))
        return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      if (path.endsWith('/notifications/n1/read')) {
        return failRead
          ? Response.json({ mensaje: 'No se pudo marcar leída' }, { status: 500 })
          : new Response(null, { status: 204 });
      }
      return Response.json(notices);
    }),
  );
});

function page() {
  render(
    <MemoryRouter initialEntries={['/notifications']}>
      <Routes>
        <Route path="/notifications" element={<NotificationsPage user={user} />} />
        <Route path="/services" element={<h1>Destino del aviso</h1>} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('lectura de notificaciones', () => {
  it('marca leída antes de navegar al detalle', async () => {
    page();
    fireEvent.click(await screen.findByRole('link', { name: 'Ver detalle →' }));
    expect(await screen.findByRole('heading', { name: 'Destino del aviso' })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledWith(
      '/api/v1/notifications/n1/read',
      expect.objectContaining({ method: 'POST' }),
    );
  });

  it('muestra el fallo, libera la carga y permite reintentar sin navegar', async () => {
    failRead = true;
    page();
    fireEvent.click(await screen.findByRole('link', { name: 'Ver detalle →' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo marcar leída');
    expect(screen.queryByText('Destino del aviso')).not.toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByRole('link', { name: 'Ver detalle →' })).toHaveAttribute(
        'aria-disabled',
        'false',
      ),
    );
    failRead = false;
    fireEvent.click(screen.getByRole('link', { name: 'Ver detalle →' }));
    expect(await screen.findByText('Destino del aviso')).toBeInTheDocument();
  });

  it('controla también los fallos de red', async () => {
    page();
    const link = await screen.findByRole('link', { name: 'Ver detalle →' });
    vi.mocked(fetch).mockRejectedValue(new Error('Sin conexión'));
    fireEvent.click(link);
    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión');
    expect(link).toHaveAttribute('aria-disabled', 'false');
  });
});
