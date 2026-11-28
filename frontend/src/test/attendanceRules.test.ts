import { describe, expect, it } from 'vitest';
import {
  canRequestReplacement,
  isAttendanceWindow,
  isJustificationWindow,
  MILLISECONDS_PER_HOUR,
} from '../attendanceRules';

const start = '2026-11-22T15:00:00Z';
const end = '2026-11-22T18:00:00Z';
const startTime = Date.parse(start);
const endTime = Date.parse(end);

describe('límites temporales de asistencia', () => {
  it('permite reemplazo exactamente 48 horas antes y no un milisegundo después', () => {
    const limit = startTime - 48 * MILLISECONDS_PER_HOUR;
    expect(canRequestReplacement(start, limit - 1)).toBe(true);
    expect(canRequestReplacement(start, limit)).toBe(true);
    expect(canRequestReplacement(start, limit + 1)).toBe(false);
  });

  it('incluye ambos extremos de la ventana de marcación de una hora', () => {
    const from = startTime - MILLISECONDS_PER_HOUR;
    const until = endTime + MILLISECONDS_PER_HOUR;
    expect(isAttendanceWindow(start, end, from - 1)).toBe(false);
    expect(isAttendanceWindow(start, end, from)).toBe(true);
    expect(isAttendanceWindow(start, end, until)).toBe(true);
    expect(isAttendanceWindow(start, end, until + 1)).toBe(false);
  });

  it('incluye el final del servicio y las 72 horas exactas para justificar', () => {
    const until = endTime + 72 * MILLISECONDS_PER_HOUR;
    expect(isJustificationWindow(end, endTime - 1)).toBe(false);
    expect(isJustificationWindow(end, endTime)).toBe(true);
    expect(isJustificationWindow(end, until)).toBe(true);
    expect(isJustificationWindow(end, until + 1)).toBe(false);
  });

  it('no habilita acciones con fechas inválidas', () => {
    expect(canRequestReplacement('invalid', startTime)).toBe(false);
    expect(isAttendanceWindow('invalid', end, startTime)).toBe(false);
    expect(isJustificationWindow('invalid', startTime)).toBe(false);
  });
});
