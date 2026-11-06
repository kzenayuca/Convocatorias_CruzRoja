package pe.cruzroja.convocatorias.operations.domain;

import java.time.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public record ServiceWindow(LocalDateTime start,LocalDateTime end,LocalDateTime deadline) {
    public ServiceWindow { valid(start!=null && end!=null && deadline!=null && end.isAfter(start),"El servicio debe terminar después del inicio."); }
    public void validatePlanning(LocalDateTime now) { valid(start.isAfter(now),"El servicio debe ser futuro."); valid(Duration.between(start,end).compareTo(Duration.ofHours(48))<=0,"El servicio no puede durar más de 48 horas."); valid(deadline.isAfter(now) && !deadline.isAfter(start),"El cierre de inscripciones debe ser futuro y anterior o igual al inicio."); }
    public boolean overlaps(ServiceWindow other) { return start.isBefore(other.end) && end.isAfter(other.start); }
}
