package pe.cruzroja.convocatorias.shared.application;

import java.time.LocalDateTime;

public interface ExecutionContext {
    Actor actor();
    LocalDateTime now();
}
