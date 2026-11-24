import { describe, expect, it } from 'vitest';
import {
  canAssignDirectly, canEditCallout, canEnroll, enrollmentStatusLabel, isFuture, parseRequirements,
} from './calloutRules';
import type { Session } from '../api';

const NOW = new Date('2026-11-20T12:00:00-05:00').getTime();
const FUTURE = '2026-12-01T08:00:00-05:00';
const PAST = '2026-11-01T08:00:00-05:00';
const volunteer: Session = { id: 'u1', name: 'Ana', email: 'a@demo.pe', role: 'VOLUNTARIO', graduated: true };
const chief: Session = { ...volunteer, id: 'u2', role: 'JEFATURA' };

describe('parseRequirements', () => {
  it('separa por líneas y descarta vacías', () => {
    // Arrange
    const raw = 'Curso de primeros auxilios\n\nExperiencia en guardias\n';
    // Act
    const result = parseRequirements(raw);
    // Assert
    expect(result).toEqual(['Curso de primeros auxilios', 'Experiencia en guardias']);
  });
  it('devuelve lista vacía cuando no hay requisitos', () => expect(parseRequirements(undefined)).toEqual([]));
});

describe('isFuture', () => {
  it('es verdadero para una fecha posterior a ahora', () => expect(isFuture(FUTURE, NOW)).toBe(true));
  it('es falso para una fecha pasada', () => expect(isFuture(PAST, NOW)).toBe(false));
});

describe('canEnroll', () => {
  const openCallout = { status: 'ABIERTA', start_at: FUTURE };
  it('permite inscribirse a un voluntario en convocatoria abierta y futura', () => expect(canEnroll(volunteer, openCallout, NOW)).toBe(true));
  it('no permite inscribirse a jefatura', () => expect(canEnroll(chief, openCallout, NOW)).toBe(false));
  it('no permite inscribirse si ya empezó', () => expect(canEnroll(volunteer, { ...openCallout, start_at: PAST }, NOW)).toBe(false));
  it('no permite inscribirse en borrador o cancelada', () => {
    expect(canEnroll(volunteer, { ...openCallout, status: 'BORRADOR' }, NOW)).toBe(false);
    expect(canEnroll(volunteer, { ...openCallout, status: 'CANCELADA' }, NOW)).toBe(false);
  });
  it('permite reintentar si la inscripción previa expiró', () =>
    expect(canEnroll(volunteer, { ...openCallout, my_enrollment: { status: 'EXPIRADA' } }, NOW)).toBe(true));
  it('no permite inscribirse dos veces si ya está confirmada', () =>
    expect(canEnroll(volunteer, { ...openCallout, my_enrollment: { status: 'CONFIRMADA' } }, NOW)).toBe(false));
});

describe('canEditCallout', () => {
  const editable = { can_edit: true, start_at: FUTURE, status: 'BORRADOR' };
  it('permite editar un borrador futuro con permiso', () => expect(canEditCallout(editable, NOW)).toBe(true));
  it('no permite editar sin permiso', () => expect(canEditCallout({ ...editable, can_edit: false }, NOW)).toBe(false));
  it('no permite editar un servicio ya abierto', () => expect(canEditCallout({ ...editable, opened_at: PAST }, NOW)).toBe(false));
  it('no permite editar una finalizada', () => expect(canEditCallout({ ...editable, status: 'FINALIZADA' }, NOW)).toBe(false));
});

describe('canAssignDirectly', () => {
  const callout = { status: 'ABIERTA', deadline: FUTURE };
  it('permite a jefatura asignar antes del plazo', () => expect(canAssignDirectly(chief, callout, true, NOW)).toBe(true));
  it('no permite a voluntarios', () => expect(canAssignDirectly(volunteer, callout, true, NOW)).toBe(false));
  it('no permite si venció el plazo', () => expect(canAssignDirectly(chief, { ...callout, deadline: PAST }, true, NOW)).toBe(false));
  it('no permite si no es editable', () => expect(canAssignDirectly(chief, callout, false, NOW)).toBe(false));
});

describe('enrollmentStatusLabel', () => {
  it('muestra ACEPTADA para CONFIRMADA', () => expect(enrollmentStatusLabel('CONFIRMADA')).toBe('ACEPTADA'));
  it('mantiene otros estados', () => expect(enrollmentStatusLabel('PENDIENTE')).toBe('PENDIENTE'));
});