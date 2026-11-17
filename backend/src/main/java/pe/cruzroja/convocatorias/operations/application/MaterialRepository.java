package pe.cruzroja.convocatorias.operations.application;

import java.time.LocalDateTime;
import java.util.*;

public interface MaterialRepository {
    List<Map<String, Object>> catalog();

    List<Map<String, Object>> list(UUID callout);

    String name(UUID catalogId);

    void request(UUID callout, UUID actor, MaterialsUseCases.Material request, String name);

    Map<String, Object> find(UUID id, boolean lock);

    void handover(
            UUID id,
            UUID callout,
            MaterialsUseCases.Handover command,
            UUID actor,
            LocalDateTime at,
            String note);
}
