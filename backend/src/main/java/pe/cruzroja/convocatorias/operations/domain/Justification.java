package pe.cruzroja.convocatorias.operations.domain;

import java.time.LocalDateTime;
import java.util.UUID;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Cada decisión es un hecho inmutable; repetir aprobación no devuelve dos veces la penalización. */
public record Justification(UUID id,String status) {
    public Decision resolve(boolean approved,String comment,UUID actor,LocalDateTime at,double before,double after) {
        required(comment,3000,"Motivo de resolución");
        return new Decision(UUID.randomUUID(),id,status,approved?"APROBADA":"RECHAZADA",actor,at,comment,before,after);
    }
    public record Decision(UUID id,UUID justification,String previousStatus,String status,UUID actor,LocalDateTime at,String comment,double before,double after) {
        public double deductionDelta() { return Math.round((after-before)*10000.0)/10000.0; }
    }
}
