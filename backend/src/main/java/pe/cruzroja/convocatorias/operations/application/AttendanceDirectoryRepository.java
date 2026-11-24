package pe.cruzroja.convocatorias.operations.application;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consulta de personas y roles que necesita el módulo de asistencia. */
public interface AttendanceDirectoryRepository {
    String userName(UUID user);

    boolean trainingVolunteer(UUID user);

    List<UUID> chiefs();

    String userRole(UUID id);

    List<Map<String, Object>> users();
}
