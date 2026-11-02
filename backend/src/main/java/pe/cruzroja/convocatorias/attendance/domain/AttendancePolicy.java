package pe.cruzroja.convocatorias.attendance.domain;

import pe.cruzroja.convocatorias.enrollments.domain.EnrollmentStatus;
import pe.cruzroja.convocatorias.users.domain.UserRole;

public class AttendancePolicy {
    public void validate(EnrollmentStatus enrollmentStatus, UserRole responsibleRole) {
        if (enrollmentStatus != EnrollmentStatus.CONFIRMADA) {
            throw new IllegalStateException("Solo se registra asistencia para inscripciones confirmadas");
        }
        if (responsibleRole != UserRole.RECLUTADOR && responsibleRole != UserRole.JEFATURA) {
            throw new IllegalStateException("Solo reclutamiento o jefatura registra asistencia");
        }
    }
}
