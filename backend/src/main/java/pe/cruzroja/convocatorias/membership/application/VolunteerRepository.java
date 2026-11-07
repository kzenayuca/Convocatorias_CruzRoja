package pe.cruzroja.convocatorias.membership.application;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;

public interface VolunteerRepository {
    void register(UUID id, AccountsUseCases.Registration request, String passwordHash);

    Map<String, Object> profile(UUID id);

    List<Map<String, Object>> users();

    void phone(UUID id, String phone);

    void requestChange(UUID id, AccountsUseCases.Profile profile);

    boolean hasPendingChange(UUID user);

    List<UUID> chiefs();

    List<Map<String, Object>> profileRequests(UUID user);

    Map<String, Object> profileRequest(UUID request);

    void resolveChange(
            UUID request, boolean approved, String comment, UUID actor, java.time.LocalDateTime at);

    List<Map<String, Object>> changes();

    Map<String, Object> lockChange(UUID id);

    void approveChange(UUID id, Map<String, Object> change);

    void deleteChange(UUID id);

    void lock(UUID id);

    void save(Volunteer volunteer, String comment);
}
