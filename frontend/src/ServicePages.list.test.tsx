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
vi.mock('./AnnualPages', () => ({ AnnualEnrollmentPanel: () => <p>Panel anual</p> }));

import { CalloutsPage, DetailPage } from './ServicePages';

const volunteer: Session = { id: 'u1', name: 'Ana', email: 'a@demo.pe', role: 'VOLUNTARIO', graduated: false };
const chief: Session = { id: 'j1', name: 'Jefa', email: 'j@demo.pe', role: 'JEFATURA', graduated: true };
const concert: Row = {
  id: '7', service_code: 'E-NOV-2026-001', title: 'Guardia de concierto', service_type: 'Concierto', location: 'Estadio',
  status: 'ABIERTA', service_category: 'E', start_at: '2099-01-01T10:00:00-05:00', planned_hours: 8,
  total_volunteers: 4, graduate_slots: 1, positions: [{ position: 'TOTAL', filled: 2 }],
};
const cleaning: Row = {
  ...concert, id: '8', service_code: null, title: 'Limpieza de playa', service_type: 'Limpieza', location: 'Mollendo',
  status: 'CANCELADA', service_category: 'X',
};

function renderList(user: Session, history = false) {
  responses[`/callouts?history=${history}`] = [concert, cleaning];
  return render(<MemoryRouter initialEntries={['/callouts']}><Routes>
    <Route path="/callouts" element={<CalloutsPage user={user} history={history}/>}/>
    <Route path="/callouts/:id" element={<p>Detalle abierto</p>}/>
  </Routes></MemoryRouter>);
}

beforeEach(() => {
  for (const key of Object.keys(responses)) delete responses[key];
  vi.mocked(api).mockResolvedValue({});
});

describe('CalloutsPage: listado de convocatorias', () => {
  it('muestra cada convocatoria con su código, estado, categoría y cobertura', () => {
    renderList(volunteer);
    expect(screen.getByRole('link', { name: 'Guardia de concierto' })).toBeInTheDocument();
    expect(screen.getByText('E-NOV-2026-001')).toBeInTheDocument();
    expect(screen.getByText('Código pendiente')).toBeInTheDocument();
    expect(screen.getByText('Cancelada')).toHaveClass('badge', 'red');
    expect(screen.getByText(/Categoría histórica/)).toBeInTheDocument();
    expect(screen.getAllByText(/Total: 2\/4 voluntarios/)).toHaveLength(2);
  });

  it('filtra por texto sin distinguir mayúsculas y limpia la búsqueda', async () => {
    renderList(volunteer);
    await userEvent.type(screen.getByLabelText('Buscar convocatorias'), 'MOLLENDO');
    expect(screen.queryByText('Guardia de concierto')).not.toBeInTheDocument();
    expect(screen.getByText('Limpieza de playa')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Limpiar' }));
    expect(screen.getByText('Guardia de concierto')).toBeInTheDocument();
  });

  it('avisa cuando ninguna convocatoria coincide', async () => {
    renderList(volunteer);
    await userEvent.type(screen.getByLabelText('Buscar convocatorias'), 'inexistente');
    expect(screen.getByText('No hay convocatorias para mostrar.')).toBeInTheDocument();
  });

  it('solo jefatura puede abrir el formulario de nueva convocatoria, y no en el historial', () => {
    renderList(volunteer);
    expect(screen.queryByRole('button', { name: 'Nueva convocatoria' })).not.toBeInTheDocument();
    renderList(chief, true);
    expect(screen.queryByRole('button', { name: 'Nueva convocatoria' })).not.toBeInTheDocument();
  });

  it('crea un borrador y abre su detalle', async () => {
    vi.mocked(api).mockResolvedValue({ id: '99' });
    renderList(chief);
    await userEvent.click(screen.getByRole('button', { name: 'Nueva convocatoria' }));
    fireEvent.submit(screen.getByRole('button', { name: 'Crear borrador' }).closest('form')!);
    expect(await screen.findByText('Detalle abierto')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/callouts', 'POST', expect.objectContaining({ category: 'I', totalVolunteers: 4 }));
  });
});

describe('TeamPage: equipo e inscripciones', () => {
  const managed: Row = {
    id: '7', title: 'Guardia de concierto', status: 'ABIERTA', start_at: '2099-01-01T10:00:00-05:00',
    deadline: '2098-12-31T10:00:00-05:00', requirements: '', positions: [], eligible_positions: [], can_view_team: true,
  };

  function renderTeam(callout: Row, enrollments: Row[]) {
    responses['/callouts/7'] = callout;
    responses['/callouts/7/enrollments'] = enrollments;
    return render(<MemoryRouter initialEntries={['/callouts/7']}><Routes>
      <Route path="/callouts/:id" element={<DetailPage user={chief}/>}/>
    </Routes></MemoryRouter>);
  }

  it('agrupa por puesto y muestra CONFIRMADA como ACEPTADA', () => {
    renderTeam(managed, [{ id: 'e1', name: 'Lucía', surnames: 'Pérez', position: 'PILOTO', status: 'CONFIRMADA', created_at: '2026-11-01' }]);
    expect(screen.getByRole('heading', { name: 'Piloto' })).toBeInTheDocument();
    expect(screen.getByText('Lucía Pérez · ACEPTADA')).toBeInTheDocument();
  });

  it('permite a jefatura solo rechazar una inscripción confirmada', async () => {
    renderTeam(managed, [{ id: 'e1', name: 'Lucía', surnames: 'Pérez', position: 'SOCORRISTA', status: 'CONFIRMADA' }]);
    const decision = screen.getByLabelText('Decisión');
    expect(decision).toHaveTextContent('RECHAZAR');
    expect(decision).not.toHaveTextContent('APROBAR');
    await userEvent.type(screen.getByLabelText('Motivo'), 'No cumple');
    await userEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(api).toHaveBeenCalledWith('/enrollments/e1/review', 'POST', { approved: false, comment: 'No cumple' });
  });

  it('no muestra la revisión cuando el servicio ya empezó', () => {
    renderTeam({ ...managed, start_at: '2000-01-01T10:00:00-05:00' }, [{ id: 'e1', name: 'Lucía', surnames: 'Pérez', position: 'SOCORRISTA', status: 'CONFIRMADA' }]);
    expect(screen.queryByLabelText('Decisión')).not.toBeInTheDocument();
  });

  it('avisa cuando aún no hay participantes y oculta la operación si no puede operar', () => {
    renderTeam(managed, []);
    expect(screen.getByText('Aún no hay participantes.')).toBeInTheDocument();
    expect(screen.queryByText('Apertura y cierre del servicio')).not.toBeInTheDocument();
  });

  it('muestra apertura, cierre y materiales a quien puede operar un servicio', () => {
    renderTeam({ ...managed, can_operate: true, kind: 'SERVICIO' }, []);
    expect(screen.getByText('Apertura y cierre del servicio')).toBeInTheDocument();
    expect(screen.getByText('Solicitud de materiales al almacén')).toBeInTheDocument();
  });
});