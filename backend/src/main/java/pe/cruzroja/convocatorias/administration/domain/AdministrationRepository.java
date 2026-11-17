package pe.cruzroja.convocatorias.administration.domain;

import java.time.LocalDateTime;
import java.util.*;

public interface AdministrationRepository {
    Map<String, Object> settings();

    void save(HourTargets targets, UUID actor, LocalDateTime at);
}
