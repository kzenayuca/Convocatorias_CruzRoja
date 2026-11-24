import React from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { api, type Row } from './api';

const responses: Record<string, unknown> = {};
vi.mock('./api', async original => {
  const actual = await original<typeof import('./api')>();
  return {
    ...actual,
    api: vi.fn(),
    useData: (path: string, initial: unknown) => ({ data: responses[path] ?? initial, error: '', loading: false }),
  };
});
vi.mock('./AnnualPages', () => ({
  AnnualEnrollmentPanel: () => <p>Panel anual</p>,
  GraduationReview: () => <p>Revisión de graduación</p>,
}));
vi.mock('./HoursProgress', () => ({ HoursProgress: () => <p>Progreso de horas</p> }));

import { AdministrationPage, Login, ProfilePage, UsersPage } from './AccountPages';

const volunteerProfile: Row = {
  id: 'u1', name: 'Ana', surnames: 'Quispe', email: 'ana@demo.pe', document: '12345678', phone: '999111222',
  occupation: 'Enfermera', role: 'VOLUNTARIO', status: 'ACTIVO', graduated: true, radio_qualified: true, pilot_qualified: false,
};

function renderAt(element: React.ReactElement, path = '/') {
  return render(<MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>);
}

beforeEach(() => {
  for (const key of Object.keys(responses)) delete responses[key];
  vi.mocked(api).mockResolvedValue({});
});

describe('Login', () => {
  it('inicia sesión con correo y contraseña y entrega la sesión', async () => {
    // Arrange
    const session = { id: 'u1', name: 'Ana', email: 'ana@demo.pe', role: 'VOLUNTARIO', graduated: true };
    vi.mocked(api).mockResolvedValue(session);
    const onLogin = vi.fn();
    renderAt(<Login onLogin={onLogin}/>);
    // Act
    await userEvent.clear(screen.getByLabelText('Correo electrónico'));
    await userEvent.type(screen.getByLabelText('Correo electrónico'), 'ana@demo.pe');
    await userEvent.clear(screen.getByLabelText('Contraseña'));
    await userEvent.type(screen.getByLabelText('Contraseña'), 'secreta123');
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    // Assert
    expect(api).toHaveBeenCalledWith('/auth/login', 'POST', { email: 'ana@demo.pe', password: 'secreta123' });
    expect(onLogin).toHaveBeenCalledWith(session);
  });

  it('muestra el error y vuelve a habilitar el botón si el acceso falla', async () => {
    vi.mocked(api).mockRejectedValue(new Error('Credenciales inválidas'));
    renderAt(<Login onLogin={vi.fn()}/>);
    await userEvent.click(screen.getByRole('button', { name: 'Iniciar sesión' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Credenciales inválidas');
    expect(screen.getByRole('button', { name: 'Iniciar sesión' })).toBeEnabled();
  });

  it('envía la solicitud de cuenta separando perfil, teléfono y contraseña', async () => {
    renderAt(<Login onLogin={vi.fn()}/>);
    await userEvent.click(screen.getByRole('button', { name: 'Solicitar una cuenta' }));
    expect(screen.getByRole('heading', { name: 'Solicita tu cuenta' })).toBeInTheDocument();
    await userEvent.type(screen.getByLabelText('Nombres'), 'Ana');
    await userEvent.type(screen.getByLabelText('Teléfono'), '999');
    await userEvent.type(screen.getByLabelText('Contraseña (8 a 72 caracteres)'), 'secreta123');
    fireEvent.submit(screen.getByRole('button', { name: 'Solicitar cuenta' }).closest('form')!);
    expect(await screen.findByText('Solicitud registrada. Jefatura revisará y activará tu cuenta.')).toBeInTheDocument();
    expect(api).toHaveBeenCalledWith('/auth/register', 'POST', {
      profile: expect.objectContaining({ name: 'Ana' }), phone: '999', password: 'secreta123',
    });
  });

  it('vuelve al inicio de sesión desde la solicitud', async () => {
    renderAt(<Login onLogin={vi.fn()}/>);
    await userEvent.click(screen.getByRole('button', { name: 'Solicitar una cuenta' }));
    await userEvent.click(screen.getByRole('button', { name: 'Volver al inicio de sesión' }));
    expect(screen.getByRole('heading', { name: 'Qué bueno tenerte aquí' })).toBeInTheDocument();
  });
});

describe('ProfilePage', () => {
  it('muestra datos personales y habilitaciones del voluntario', () => {
    responses['/profile'] = volunteerProfile;
    renderAt(<ProfilePage/>);
    expect(screen.getByRole('heading', { name: 'Ana Quispe' })).toBeInTheDocument();
    expect(screen.getByText('ana@demo.pe')).toBeInTheDocument();
    expect(screen.getByText('Graduado')).toBeInTheDocument();
    expect(screen.getByText('Habilitado por jefatura')).toBeInTheDocument();
    expect(screen.getByText('Sin habilitación')).toBeInTheDocument();
    expect(screen.getByText('Progreso de horas')).toBeInTheDocument();
    expect(screen.getByText('Panel anual')).toBeInTheDocument();
  });

  it('muestra textos por defecto cuando faltan datos opcionales', () => {
    responses['/profile'] = { ...volunteerProfile, phone: null, occupation: null, graduated: false };
    renderAt(<ProfilePage/>);
    expect(screen.getByText('No registrado')).toBeInTheDocument();
    expect(screen.getByText('No registrada')).toBeInTheDocument();
    expect(screen.getByText('En formación')).toBeInTheDocument();
    expect(screen.queryByText('Panel anual')).not.toBeInTheDocument();
  });

  it('muestra la administración institucional a jefatura, sin metas personales', () => {
    responses['/profile'] = { ...volunteerProfile, role: 'JEFATURA' };
    renderAt(<ProfilePage/>);
    expect(screen.getByRole('heading', { name: 'Administración institucional' })).toBeInTheDocument();
    expect(screen.queryByText('Progreso de horas')).not.toBeInTheDocument();
  });

  it('cambia el teléfono directamente y solicita aprobación para otros datos', async () => {
    responses['/profile'] = volunteerProfile;
    renderAt(<ProfilePage/>);
    await userEvent.click(screen.getByRole('button', { name: 'Modificar' }));
    fireEvent.submit(screen.getByRole('button', { name: 'Guardar teléfono' }).closest('form')!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/profile/phone', 'PUT', { phone: '999111222' }));
    fireEvent.submit(screen.getByRole('button', { name: 'Solicitar cambio' }).closest('form')!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/profile/changes', 'POST', expect.objectContaining({ name: 'Ana', email: 'ana@demo.pe' })));
  });

  it('lista mis solicitudes de cambio de perfil', () => {
    responses['/profile'] = volunteerProfile;
    responses['/profile/changes'] = [{ id: 'r1', name: 'Ana', surnames: 'Quispe', status: 'RECHAZADA', resolution_comment: 'DNI no coincide' }];
    renderAt(<ProfilePage/>);
    expect(screen.getByText('Cambio de perfil · Ana Quispe · RECHAZADA')).toBeInTheDocument();
    expect(screen.getByText('Resolución: DNI no coincide')).toBeInTheDocument();
  });
});

describe('AdministrationPage', () => {
  it('muestra metas configuradas y filtra los códigos por tipo', async () => {
    responses['/administration'] = { training_hours: 200, graduate_hours: 60 };
    responses['/service-codes'] = [
      { id: 'c1', callout_id: '7', service_code: 'E-NOV-2026-001', service_type: 'Concierto' },
      { id: 'c2', callout_id: '8', service_code: 'I-NOV-2026-002', service_type: 'Limpieza' },
    ];
    renderAt(<AdministrationPage/>);
    expect(screen.getByText('200 h')).toBeInTheDocument();
    expect(screen.getByText('60 h')).toBeInTheDocument();
    await userEvent.selectOptions(screen.getByLabelText('Descripción del servicio'), 'Limpieza');
    expect(screen.queryByText('E-NOV-2026-001')).not.toBeInTheDocument();
    expect(screen.getByText('I-NOV-2026-002')).toBeInTheDocument();
  });

   it('no muestra el formulario de metas hasta recibirlas del servidor', () => {
    responses['/administration'] = {};
    renderAt(<AdministrationPage/>);
    expect(screen.queryByText('Editar metas de horas')).not.toBeInTheDocument();
  });

  it('indica cuando las metas no están configuradas y guarda nuevas metas', async () => {
    responses['/administration'] = { training_hours: null, graduate_hours: null };
    
    renderAt(<AdministrationPage/>);
    expect(screen.getAllByText('Sin configurar')).toHaveLength(2);
    await userEvent.type(screen.getByLabelText('Meta de formación (saldo neto acumulado entre años)'), '150');
    await userEvent.type(screen.getByLabelText('Meta de graduados (saldo neto del año)'), '40');
    fireEvent.submit(screen.getByRole('button', { name: 'Guardar metas generales' }).closest('form')!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/administration', 'PUT', { trainingHours: 150, graduateHours: 40 }));
  });
});

describe('UsersPage', () => {
  beforeEach(() => {
    responses['/users'] = [
      volunteerProfile,
      { ...volunteerProfile, id: 'u2', name: 'Bruno', surnames: 'Torres', email: 'bruno@demo.pe', graduated: false, radio_qualified: false },
    ];
  });

  it('filtra usuarios por nombre, correo, DNI, rol o estado', async () => {
    renderAt(<UsersPage/>, '/users');
    await userEvent.type(screen.getByLabelText('Buscar usuarios'), 'bruno');
    expect(screen.queryByText(/Ana Quispe/)).not.toBeInTheDocument();
    expect(screen.getByText(/Bruno Torres/)).toBeInTheDocument();
  });

  it('muestra la revisión de graduación solo a voluntarios en formación', () => {
    renderAt(<UsersPage/>, '/users');
    expect(screen.getAllByText('Revisión de graduación')).toHaveLength(1);
  });

  it('convierte SI/NO en booleanos al guardar la revisión de un usuario', async () => {
    renderAt(<UsersPage/>, '/users');
    const anaCard = screen.getByText(/Ana Quispe · ACTIVO · VOLUNTARIO/).closest('details')!;
    fireEvent.submit(within(anaCard).getByRole('button', { name: 'Guardar' }).closest('form')!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/users/u1', 'PUT', {
      role: 'VOLUNTARIO', graduated: true, status: 'ACTIVO', comment: '', radioQualified: true, pilotQualified: false,
    }));
  });

  it('permite a jefatura resolver un cambio de perfil pendiente', async () => {
    responses['/users/changes'] = [{ request_id: 'r9', user_id: 'u1', name: 'Ana', surnames: 'Quispe', email: 'nuevo@demo.pe', document: '12345678', occupation: 'Médica' }];
    renderAt(<UsersPage/>, '/users');
    const requestCard = screen.getByText('Cambio de perfil · Ana Quispe · PENDIENTE').closest('details')!;
    await userEvent.type(within(requestCard).getByLabelText('Motivo de la decisión'), 'Verificado');
    fireEvent.submit(within(requestCard).getByRole('button', { name: 'Resolver cambio de perfil' }).closest('form')!);
    await vi.waitFor(() => expect(api).toHaveBeenCalledWith('/users/u1/changes', 'POST', { approved: true, comment: 'Verificado', requestId: 'r9' }));
  });
});