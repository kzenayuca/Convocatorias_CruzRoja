import React from 'react';
import { act, fireEvent, render, renderHook, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Action, DataForm } from '../components';
import { useData } from '../api';

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn());
});

describe('useData: comportamiento previo a la refactorización', () => {
  it('carga datos y termina el indicador de carga', async () => {
    vi.mocked(fetch).mockResolvedValue(Response.json([{ id: '1' }]));
    const { result } = renderHook(() => useData('/items', []));
    expect(result.current.loading).toBe(true);
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.data).toEqual([{ id: '1' }]);
    expect(result.current.error).toBe('');
  });

  it('muestra errores sin dejar la carga activa', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('Sin conexión'));
    const { result } = renderHook(() => useData('/items', []));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBe('Sin conexión');
  });

  it('refresca al recibir data-changed', async () => {
    vi.mocked(fetch).mockImplementation(async () => Response.json([]));
    renderHook(() => useData('/items', []));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    act(() => window.dispatchEvent(new Event('data-changed')));
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(2));
  });

  it('ignora la respuesta anterior al cambiar de ruta', async () => {
    let resolveOld!: (value: Response) => void;
    vi.mocked(fetch)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce(Response.json(['nuevo']));
    const { result, rerender } = renderHook(({ path }) => useData(path, []), {
      initialProps: { path: '/old' },
    });
    rerender({ path: '/new' });
    await waitFor(() => expect(result.current.data).toEqual(['nuevo']));
    await act(async () => resolveOld(Response.json(['viejo'])));
    expect(result.current.data).toEqual(['nuevo']);
  });
});

describe('formularios y acciones compartidos', () => {
  it('convierte números, avisa y cierra details solo después del éxito', async () => {
    const submit = vi.fn().mockResolvedValue(undefined);
    const changed = vi.fn();
    window.addEventListener('data-changed', changed);
    try {
      render(
        <details open>
          <summary>Editar</summary>
          <DataForm fields={[{ name: 'hours', label: 'Horas', type: 'number' }]} submit={submit} />
        </details>,
      );
      fireEvent.change(screen.getByLabelText('Horas'), { target: { value: '2' } });
      fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
      await waitFor(() => expect(submit).toHaveBeenCalledWith({ hours: 2 }));
      expect(await screen.findByRole('status')).toHaveTextContent('Guardado correctamente.');
      expect(screen.getByText('Editar').closest('details')).not.toHaveAttribute('open');
      expect(changed).toHaveBeenCalledOnce();
    } finally {
      window.removeEventListener('data-changed', changed);
    }
  });

  it('conserva los valores y permite reintentar al fallar', async () => {
    const submit = vi
      .fn()
      .mockRejectedValueOnce(new Error('No guardado'))
      .mockResolvedValueOnce(undefined);
    render(<DataForm fields={[{ name: 'name', label: 'Nombre' }]} submit={submit} />);
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Lizeth' } });
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('No guardado');
    expect(screen.getByLabelText('Nombre')).toHaveValue('Lizeth');
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }));
    expect(await screen.findByRole('status')).toHaveTextContent('Guardado correctamente.');
  });

  it('Action muestra fallos y vuelve a habilitar el botón', async () => {
    vi.mocked(fetch).mockRejectedValue(new Error('Sin conexión'));
    render(<Action path="/items/1">Enviar</Action>);
    fireEvent.click(screen.getByRole('button', { name: 'Enviar' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Sin conexión');
    expect(screen.getByRole('button', { name: 'Enviar' })).toBeEnabled();
  });
});
