package pe.cruzroja.convocatorias.attendance.api; import java.time.LocalDateTime; import java.util.UUID; public record AbsenceRecorded(UUID attendanceId,UUID volunteerId,LocalDateTime occurredAt) {}
