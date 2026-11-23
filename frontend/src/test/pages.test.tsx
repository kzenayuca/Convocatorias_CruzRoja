import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Login } from '../AccountPages';
import { DetailPage } from '../ServicePages';
import { Session } from '../api';

const chief: Session = {
  id: 'chief',
  name: 'Jefatura',
  email: 'chief@example.com',
  role: 'JEFATURA',
  graduated: false,
};
const callout = {
  id: 'c1',
  title: 'Servicio de prueba',
  status: 'ABIERTA',
  kind: 'SERVICIO',
  start_at: '2099-01-02T15:00:00Z',
  end_at: '2099-01-02T18:00:00Z',
  deadline: '2099-01-01T15:00:00Z',
  requirements: 'Formación básica',
  can_edit: true,
  can_view_team: true,
  can_upload_photo: true,
  positions: [],
  eligible_positions: [],
  planned_hours: 3,
};
let failWrite: boolean;

beforeEach(() => {
  failWrite = false;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const path = String(input);
      if (path.endsWith('/auth/csrf'))
        return Response.json({ token: 'token', headerName: 'X-CSRF-TOKEN' });
      if (init?.method && init.method !== 'GET') {
        if (failWrite) return Response.json({ mensaje: 'No guardado' }, { status: 500 });
        if (path.endsWith('/auth/login')) return Response.json(chief);
        return new Response(null, { status: 204 });
      }
      if (path.endsWith('/callouts/c1')) return Response.json(callout);
      if (path.includes('/assignment-candidates'))
        return Response.json([{ id: 'u1', name: 'Lizeth', surnames: 'Prueba', graduated: true }]);
      return Response.json([]);
    }),
  );
});

function detail() {
  render(
    <MemoryRouter initialEntries={['/callouts/c1']}>
      <Routes>
        <Route path="/callouts/:id" element={<DetailPage user={chief} />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('cuentas y formularios de convocatorias refactorizados', () => {
  it('login conserva su contrato de sesión', async () => {
    const onLogin = vi.fn();
    render(<Login onLogin={onLogin} />);
    await userEvent.setup().click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    await waitFor(() => expect(onLogin).toHaveBeenCalledWith(chief));
  });

  it('login muestra el error y libera el botón', async () => {
    failWrite = true;
    render(<Login onLogin={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No guardado');
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  });

  it('la asignación directa conserva verificaciones, valores al fallar y efectos al guardar', async () => {
    detail();
    fireEvent.click(await screen.findByText('Inscribir directamente · jefatura'));
    const person = await screen.findByLabelText('Persona elegible');
    await waitFor(() =>
      expect(screen.getByRole('option', { name: 'Lizeth Prueba · Graduado' })).toBeInTheDocument(),
    );
    fireEvent.change(person, { target: { value: 'u1' } });
    fireEvent.click(
      screen.getByLabelText('He verificado todos los requisitos declarados para esta persona.'),
    );
    failWrite = true;
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar inscripción directa' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No guardado');
    expect(person).toHaveValue('u1');
    failWrite = false;
    fireEvent.click(screen.getByRole('button', { name: 'Confirmar inscripción directa' }));
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/callouts/c1/assignments',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            userId: 'u1',
            position: 'SOCORRISTA',
            requirementsVerified: true,
          }),
        }),
      ),
    );
    expect(await screen.findByRole('status')).toHaveTextContent('Inscripción directa confirmada.');
    expect(person).toHaveValue('');
  });

  it('la foto rechaza archivos demasiado grandes sin enviar y permite corregirlos', async () => {
    detail();
    fireEvent.click(await screen.findByRole('button', { name: 'Adjuntar foto' }));
    const input = screen.getByLabelText('Foto opcional JPG/PNG · hasta 10 MB y 40 megapíxeles');
    const large = new File(['x'], 'large.png', { type: 'image/png' });
    Object.defineProperty(large, 'size', { value: 10 * 1024 * 1024 + 1 });
    const interaction = userEvent.setup();
    await interaction.upload(input as HTMLInputElement, large);
    expect((input as HTMLInputElement).files?.[0]).toBe(large);
    fireEvent.submit(input.closest('form')!);
    expect(await screen.findByRole('alert')).toHaveTextContent('Máximo 10 MB.');
    expect(vi.mocked(fetch).mock.calls.some(([path]) => String(path).endsWith('/photo'))).toBe(
      false,
    );
    await interaction.upload(
      input as HTMLInputElement,
      new File(['foto'], 'small.png', { type: 'image/png' }),
    );
    fireEvent.submit(input.closest('form')!);
    await waitFor(() =>
      expect(fetch).toHaveBeenCalledWith(
        '/api/v1/callouts/c1/photo',
        expect.objectContaining({
          method: 'POST',
          body: expect.any(FormData),
        }),
      ),
    );
    expect(await screen.findByRole('button', { name: 'Adjuntar foto' })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
