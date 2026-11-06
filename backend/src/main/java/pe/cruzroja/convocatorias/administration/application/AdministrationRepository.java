package pe.cruzroja.convocatorias.administration.application;

import java.time.LocalDateTime;
import java.util.*;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;

public interface AdministrationRepository {
    Map<String,Object> settings();
    void save(HourTargets targets,UUID actor,LocalDateTime at);
}
