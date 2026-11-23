import { useEffect, useRef, useState } from 'react';
import { errorMessage } from './api';

/** Una sola petición por instancia; el éxito y sus efectos pertenecen al consumidor. */
export function useRequest() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const running = useRef(false);
  const mounted = useRef(true);

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  async function run(task: () => Promise<unknown>): Promise<boolean> {
    if (running.current) return false;
    running.current = true;
    setBusy(true);
    setError('');
    try {
      await task();
      return true;
    } catch (cause: unknown) {
      if (mounted.current) setError(errorMessage(cause));
      return false;
    } finally {
      running.current = false;
      if (mounted.current) setBusy(false);
    }
  }

  return { busy, error, run };
}
