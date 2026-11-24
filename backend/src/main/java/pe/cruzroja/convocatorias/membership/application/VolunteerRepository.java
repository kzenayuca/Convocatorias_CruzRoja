package pe.cruzroja.convocatorias.membership.application;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;

public interface VolunteerRepository {
    void register(UUID id, AccountsUseCases.Registration request, String passwordHash);

    VolunteerProfile profile(UUID id);

    List<VolunteerProfile> users();

    void phone(UUID id, String phone);

    void requestChange(UUID id, AccountsUseCases.Profile profile);

    boolean hasPendingChange(UUID user);

    List<UUID> chiefs();

    List<ProfileChangeRequest> profileRequests(UUID user);

    ProfileChangeRequest profileRequest(UUID request);

    void resolveChange(
            UUID request, boolean approved, String comment, UUID actor, java.time.LocalDateTime at);

    List<PendingProfileChange> changes();

    PendingProfileChange lockChange(UUID id);

    void approveChange(UUID id, PendingProfileChange change);

    void deleteChange(UUID id);

    void lock(UUID id);

    void save(Volunteer volunteer, String comment);
}
