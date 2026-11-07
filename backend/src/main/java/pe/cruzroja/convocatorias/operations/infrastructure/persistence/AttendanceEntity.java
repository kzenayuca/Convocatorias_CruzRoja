package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_records")
public class AttendanceEntity {
    @Id private UUID id;
    private LocalDateTime checkIn;
    private LocalDateTime checkOut;

    @Column(precision = 10, scale = 4)
    private BigDecimal approvedHours;

    private String status;
    private boolean signed;
    private int policyVersion;
    private UUID responsibleId;

    @Column(columnDefinition = "text")
    private String observations;

    protected AttendanceEntity() {}

    public void storeMark(String action, LocalDateTime at) {
        if ("ENTRADA".equals(action)) {
            checkIn = at;
        } else {
            checkOut = at;
        }
        status = "PENDIENTE_REVISION";
        signed = false;
        approvedHours = null;
    }

    public void storeConfirmation(
            LocalDateTime in, LocalDateTime out, double hours, UUID actor, String note) {
        checkIn = in;
        checkOut = out;
        approvedHours = BigDecimal.valueOf(hours);
        status = "CONFIRMADA";
        signed = true;
        policyVersion = 2;
        responsibleId = actor;
        observations = note;
    }
}
