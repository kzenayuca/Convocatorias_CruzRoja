package pe.cruzroja.convocatorias.operations.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Una solicitud se resuelve una vez; rechazarla no modifica las marcas ni inventa horas. */
public record AttendanceReview(UUID id,String status) {
    public Decision resolve(boolean approved,String comment,UUID actor,LocalDateTime at) {
        valid("PENDIENTE".equals(status),"La revisión de asistencia ya fue resuelta."); required(comment,3000,"Motivo de resolución");
        return new Decision(id,approved?"APROBADA":"RECHAZADA",comment,actor,at);
    }
    public record Decision(UUID request,String status,String comment,UUID actor,LocalDateTime at) { }
}
