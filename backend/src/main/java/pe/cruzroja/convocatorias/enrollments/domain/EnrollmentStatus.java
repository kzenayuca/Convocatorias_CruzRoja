package pe.cruzroja.convocatorias.enrollments.domain; public enum EnrollmentStatus { PENDIENTE, CONFIRMADA, RECHAZADA, CANCELADA; public boolean isFinal(){return this!=PENDIENTE;} }
