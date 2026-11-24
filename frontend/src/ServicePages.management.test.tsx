import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { api, type Row, type Session } from './api';

const responses: Record<string, unknown> = {};
vi.mock('./api', async original => {
  const actual = await original<typeof import('./api')>();
  return {
    ...actual,
    api: vi.fn(),
    useData: (path: string, initial: unknown) => ({ data: responses[path] ?? initial, error: '', loading: false }),
  };
});

import { DetailPage } from './ServicePages';

const chief: Session = { id: 'j1', name: 'Jefa', email: 'j@demo.pe', role: 'JEFATURA', graduated: true };
const managedCallout: Row = {
  id: '7', title: 'Guardia de concierto', status: 'ABIERTA', service_code: 'E-NOV-2026-001', service_category: 'E',
  start_at: '2099-01-01T10:00:00-05:00', end_at: '2099-01-01T18:00:00-05:00', deadline: '2098-12-31T10:00:00-05:00',
  requirements: 'Curso de primeros auxilios', positions: [], eligible_positions: [],
  can_edit: true, can_view_team: true,
};
const candidates = [
  { id: 'p1', name: 'Lucía', surnames: 'Pérez', graduated: true },
  { id: 'p2', name: 'Mario', surnames: 'Ruiz', graduated: false },
];

function renderDetail(callout: Row) {
  responses['/callouts/7'] = callout;
  return render(<MemoryRouter initialEntries={['/callouts/7']}><Routes>
    <Route path="/callouts/:id" element={<DetailPage user={chief}/>}/>
  </Routes></MemoryRouter>);
}

beforeEach(() => {
  for (const key of Object.keys(responses)) delete responses[key];
  vi.mocked(api).mockResolvedValue({});
});

describe('AssignmentPanel: inscripción directa por jefatura', () => {
  beforeEach(() => { responses['/callouts/7/assignment-candidates?position=SOCORRISTA'] = candidates; });

  it('busca por nombre sin distinguir tildes ni mayúsculas', async () => {
    // Arrange
    renderDetail(managedCallout);
    // Act
    await userEvent.type(screen.getByLabelText('Buscar persona por nombre'), 'LUCIA');
    // Assert
    const people = screen.getByLabelText('Persona elegible');
    expect(people).toHaveTextContent('Lucía Pérez');
    expect(people).not.toHaveTextContent('Mario Ruiz');
  });

  it('exige verificar requisitos antes de confirmar y envía la asignación', async () => {
    renderDetail(managedCallout);
    const confirm = screen.getByRole('button', { name: 'Confirmar inscripción directa' });
    await userEvent.selectOptions(screen.getByLabelText('Persona elegible'), 'p1');
    expect(confirm).toBeDisabled();

    await userEvent.click(screen.getByLabelText('He verificado todos los requisitos declarados para esta persona.'));
    await userEvent.click(confirm);

    expect(api).toHaveBeenCalledWith('/callouts/7/assignments', 'POST', { userId: 'p1', position: 'SOCORRISTA', requirementsVerified: true });
    expect(await screen.findByText('Inscripción directa confirmada. Se notificó a la persona.')).toBeInTheDocument();
  });
});

describe('ResponsiblePanel: responsable del servicio', () => {
  beforeEach(() => { responses['/callouts/7/responsible-candidates'] = candidates; });

  it('asigna el primer responsable con PUT', async () => {
    renderDetail(managedCallout);
    await userEvent.selectOptions(screen.getByLabelText('Voluntario inscrito'), 'p1');
    await userEvent.click(screen.getByRole('button', { name: 'Asignar responsable' }));
    expect(api).toHaveBeenCalledWith('/callouts/7/responsible', 'PUT', { userId: 'p1' });
  });

  it('registra un cambio auditado con POST cuando ya hay responsable', async () => {
    renderDetail({ ...managedCallout, responsible_id: 'p2' });
    await userEvent.click(screen.getByRole('button', { name: 'Cambiar o retirar responsable' }));
    await userEvent.selectOptions(screen.getByLabelText('Nuevo responsable (vacío = retirar responsabilidad)'), 'p1');
    await userEvent.type(screen.getByLabelText('Motivo obligatorio del cambio o retiro'), 'Cambio de turno');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar cambio auditado' }));
    expect(api).toHaveBeenCalledWith('/callouts/7/responsible/change', 'POST', {
      userId: 'p1', expectedResponsibleId: 'p2', reason: 'Cambio de turno',
    });
  });
});

/** jsdom marca como inválido un input file obligatorio aunque tenga archivo: se envía el formulario directamente. */
function submitPhotoForm() {
  fireEvent.submit(screen.getByRole('button', { name: 'Guardar foto' }).closest('form')!);
}

describe('PhotoForm: foto del equipo', () => {
  const photoCallout = { ...managedCallout, can_view_team: false, can_upload_photo: true };

  it('rechaza fotos de más de 10 MB sin llamar a la API', async () => {
    renderDetail(photoCallout);
    await userEvent.click(screen.getByRole('button', { name: 'Adjuntar foto' }));
    const bigPhoto = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'equipo.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText(/Foto opcional JPG\/PNG/), bigPhoto);
    submitPhotoForm();
    expect(await screen.findByText('Máximo 10 MB.')).toBeInTheDocument();
    expect(api).not.toHaveBeenCalled();
  });

  it('sube una foto válida como FormData', async () => {
    renderDetail(photoCallout);
    await userEvent.click(screen.getByRole('button', { name: 'Adjuntar foto' }));
    const photo = new File(['png'], 'equipo.png', { type: 'image/png' });
    await userEvent.upload(screen.getByLabelText(/Foto opcional JPG\/PNG/), photo);
    submitPhotoForm();
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/callouts/7/photo', 'POST', expect.any(FormData)));
  });
});