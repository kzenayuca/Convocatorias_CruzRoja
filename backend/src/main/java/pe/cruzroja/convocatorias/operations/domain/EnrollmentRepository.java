package pe.cruzroja.convocatorias.operations.domain;

import java.time.LocalDateTime;
import java.util.*;

public interface EnrollmentRepository {
    Map<String, Object> find(UUID id, boolean lock);

    List<Map<String, Object>> byUser(UUID callout, UUID user, boolean lock);

    void lockVolunteer(UUID user);

    List<Map<String, Object>> candidates(UUID callout);

    List<Map<String, Object>> applicants(UUID callout);

    List<Map<String, Object>> mine(UUID user);

    int pendingReplacements(UUID enrollment);

    boolean replacementRequested(UUID callout, UUID user);

    void save(
            UUID existing,
            UUID user,
            UUID callout,
            String position,
            UUID replacement,
            UUID assignedBy,
            LocalDateTime at);

    void cancellation(UUID id, String reason, LocalDateTime at);

    void clearCancellation(UUID id);

    void cancelReplaced(UUID id);

    void review(UUID id, boolean approved, String comment, LocalDateTime at);

    List<UUID> commitments(UUID user);

    List<Map<String, Object>> activeForUser(UUID callout, UUID user);

    void cancelIneligible(UUID enrollment);
}
