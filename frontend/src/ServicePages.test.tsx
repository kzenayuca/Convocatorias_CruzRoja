import React from 'react';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Row, Session } from './api';

// Simula el backend: la prueba no llama a la API real.
const responses: Record<string, unknown> = {};
vi.mock('./api', async original => {
  const actual = await original<typeof import('./api')>();
  return {
    ...actual,
    api: vi.fn().mockResolvedValue({}),
    useData: (path: string, initial: unknown) => ({ data: responses[path] ?? initial, error: '', loading: false }),
  };
});

import { DetailPage } from './ServicePages';

const volunteer: Session = { id: 'u1', name: 'Ana', email: 'a@demo.pe', role: 'VOLUNTARIO', graduated: true };
const chief: Session = { ...volunteer, id: 'u2', role: 'JEFATURA' };
const baseCallout: Row = {
  id: '7', title: 'Guardia de concierto', description: 'Apoyo de salud', status: 'ABIERTA',
  service_code: 'E-NOV-2026-001', service_category: 'E', service_type: 'Concierto', area: 'Voluntariado',
  start_at: '2099-01-01T10:00:00-05:00', end_at: '2099-01-01T18:00:00-05:00', deadline: '2098-12-31T10:00:00-05:00',
  location: 'Estadio', planned_hours: 8, requirements: 'Curso de primeros auxilios\nUniforme completo',
  eligible_positions: ['SOCORRISTA'], positions: [], coverage: 'PENDIENTE', execution: 'PROGRAMADA',
};

function renderDetail(user: Session, callout: Row) {
  responses['/callouts/7'] = callout;
  return render(<MemoryRouter initialEntries={['/callouts/7']}><Routes>
    <Route path="/callouts/:id" element={<DetailPage user={user}/>}/>
  </Routes></MemoryRouter>);
}

describe('DetailPage', () => {
  beforeEach(() => { for (const key of Object.keys(responses)) delete responses[key]; });

  it('muestra título, código e información del servicio', () => {
    // Arrange + Act
    renderDetail(volunteer, baseCallout);
    // Assert
    expect(screen.getByRole('heading', { name: 'Guardia de concierto' })).toBeInTheDocument();
    expect(screen.getByText(/E-NOV-2026-001/)).toBeInTheDocument();
    expect(screen.getByText('Estadio')).toBeInTheDocument();
  });

  it('exige marcar todos los requisitos antes de mostrar el formulario de inscripción', async () => {
    renderDetail(volunteer, baseCallout);
    expect(screen.getByText('Marca todos los requisitos para continuar.')).toBeInTheDocument();
    for (const checkbox of screen.getAllByRole('checkbox')) await userEvent.click(checkbox);
    expect(screen.getByRole('button', { name: 'Inscribirme' })).toBeInTheDocument();
  });

  it('muestra los requisitos como lista de solo lectura a jefatura', () => {
    renderDetail(chief, baseCallout);
    expect(screen.queryByRole('checkbox')).not.toBeInTheDocument();
    expect(screen.getByText('Uniforme completo').tagName).toBe('LI');
  });

  it('muestra la inscripción confirmada como ACEPTADA', () => {
    renderDetail(volunteer, { ...baseCallout, my_enrollment: { status: 'CONFIRMADA', position: 'SOCORRISTA' } });
    expect(screen.getByText('ACEPTADA')).toBeInTheDocument();
  });

  it('oculta la gestión del servicio a quien no puede ver el equipo', () => {
    renderDetail(volunteer, baseCallout);
    expect(screen.queryByRole('region', { name: 'Gestión del servicio' })).not.toBeInTheDocument();
  });

  it('muestra la gestión y el botón Publicar a jefatura en un borrador', () => {
    renderDetail(chief, { ...baseCallout, status: 'BORRADOR', can_edit: true, can_view_team: true });
    expect(screen.getByRole('region', { name: 'Gestión del servicio' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Publicar' })).toBeInTheDocument();
  });
});