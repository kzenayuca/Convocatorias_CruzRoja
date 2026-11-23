import type { Row, Session } from '../api';

/** Estados en los que un voluntario todavía puede inscribirse. */
const ENROLLABLE_CALLOUT_STATUSES: ReadonlySet<string> = new Set(['ABIERTA', 'CERRADA']);
/** Estados de una inscripción previa que permiten volver a inscribirse. */
const RETRYABLE_ENROLLMENT_STATUSES: ReadonlySet<string> = new Set(['PENDIENTE', 'EXPIRADA']);
/** Estados en los que jefatura o el responsable pueden modificar la convocatoria. */
const EDITABLE_CALLOUT_STATUSES: ReadonlySet<string> = new Set(['BORRADOR', 'ABIERTA', 'CERRADA']);

export const isChief = (user: Session) => user.role === 'JEFATURA';

export function isFuture(isoDate: string | undefined, now = Date.now()): boolean {
  return !!isoDate && new Date(isoDate).getTime() > now;
}

export function parseRequirements(raw: string | null | undefined): string[] {
  return (raw ?? '').split('\n').filter(Boolean);
}

function hasNoActiveEnrollment(callout: Row): boolean {
  return !callout.my_enrollment || RETRYABLE_ENROLLMENT_STATUSES.has(callout.my_enrollment.status);
}

export function canEnroll(user: Session, callout: Row, now = Date.now()): boolean {
  return !isChief(user)
    && ENROLLABLE_CALLOUT_STATUSES.has(callout.status)
    && isFuture(callout.start_at, now)
    && hasNoActiveEnrollment(callout);
}

export function canEditCallout(callout: Row, now = Date.now()): boolean {
  return !!callout.can_edit
    && isFuture(callout.start_at, now)
    && !callout.opened_at
    && EDITABLE_CALLOUT_STATUSES.has(callout.status);
}

export function canAssignDirectly(user: Session, callout: Row, editable: boolean, now = Date.now()): boolean {
  return isChief(user)
    && editable
    && ENROLLABLE_CALLOUT_STATUSES.has(callout.status)
    && isFuture(callout.deadline, now);
}

export function enrollmentStatusLabel(status: string): string {
  return status === 'CONFIRMADA' ? 'ACEPTADA' : status;
}