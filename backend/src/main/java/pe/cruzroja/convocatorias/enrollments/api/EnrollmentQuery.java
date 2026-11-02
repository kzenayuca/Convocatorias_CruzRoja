package pe.cruzroja.convocatorias.enrollments.api; import java.util.UUID; public interface EnrollmentQuery { boolean isConfirmed(UUID enrollmentId); int countActive(UUID calloutId); }
