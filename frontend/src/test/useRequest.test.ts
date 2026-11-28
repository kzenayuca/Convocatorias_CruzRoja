import { act, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useRequest } from '../useRequest';

describe('ciclo compartido de peticiones', () => {
  it('activa carga, evita envíos simultáneos y finaliza en éxito', async () => {
    const { result } = renderHook(useRequest);
    let finish!: () => void;
    let pending!: Promise<boolean>;
    act(() => {
      pending = result.current.run(
        () =>
          new Promise<void>((resolve) => {
            finish = resolve;
          }),
      );
    });
    expect(result.current.busy).toBe(true);
    await expect(
      result.current.run(async () => {
        throw new Error('No debe ejecutarse');
      }),
    ).resolves.toBe(false);
    await act(async () => {
      finish();
      await pending;
    });
    expect(result.current.busy).toBe(false);
    expect(result.current.error).toBe('');
    await expect(pending).resolves.toBe(true);
  });

  it('controla fallos y limpia el error cuando se reintenta', async () => {
    const { result } = renderHook(useRequest);
    await act(async () => {
      expect(
        await result.current.run(async () => {
          throw new Error('Falló');
        }),
      ).toBe(false);
    });
    expect(result.current.error).toBe('Falló');
    expect(result.current.busy).toBe(false);
    await act(async () => {
      expect(await result.current.run(async () => {})).toBe(true);
    });
    expect(result.current.error).toBe('');
  });

  it('normaliza rechazos que no son Error', async () => {
    const { result } = renderHook(useRequest);
    await act(async () => {
      await result.current.run(() => Promise.reject(null));
    });
    expect(result.current.error).toBe('No se pudo completar la operación.');
    expect(result.current.busy).toBe(false);
  });

  it('termina sin rechazos sin manejar después del desmontaje', async () => {
    const { result, unmount } = renderHook(useRequest);
    let reject!: (reason: Error) => void;
    let pending!: Promise<boolean>;
    act(() => {
      pending = result.current.run(
        () =>
          new Promise((_, fail) => {
            reject = fail;
          }),
      );
    });
    unmount();
    reject(new Error('Desmontado'));
    await expect(pending).resolves.toBe(false);
  });
});
