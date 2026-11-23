export const MILLISECONDS_PER_HOUR = 60 * 60 * 1000;
export const REPLACEMENT_NOTICE_HOURS = 48;
export const ATTENDANCE_WINDOW_HOURS = 1;
export const JUSTIFICATION_DEADLINE_HOURS = 72;
export const LATE_TOLERANCE_MINUTES = 10;
export const CURRENT_ATTENDANCE_POLICY_VERSION = 2;

export function canRequestReplacement(start: string, now = Date.now()): boolean {
  return new Date(start).getTime() - now >= REPLACEMENT_NOTICE_HOURS * MILLISECONDS_PER_HOUR;
}

export function isAttendanceWindow(start: string, end: string, now = Date.now()): boolean {
  const margin = ATTENDANCE_WINDOW_HOURS * MILLISECONDS_PER_HOUR;
  return now >= new Date(start).getTime() - margin && now <= new Date(end).getTime() + margin;
}

export function isJustificationWindow(end: string, now = Date.now()): boolean {
  const serviceEnd = new Date(end).getTime();
  return (
    now >= serviceEnd && now <= serviceEnd + JUSTIFICATION_DEADLINE_HOURS * MILLISECONDS_PER_HOUR
  );
}
