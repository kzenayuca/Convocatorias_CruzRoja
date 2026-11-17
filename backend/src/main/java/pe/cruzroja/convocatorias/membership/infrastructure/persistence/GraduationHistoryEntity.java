package pe.cruzroja.convocatorias.membership.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentRepository.GraduationEligibilityData;

@Entity
@Table(name = "graduation_history")
public class GraduationHistoryEntity {
    @Id private UUID id;
    private UUID userId;
    private UUID approvedBy;
    private LocalDateTime approvedAt;

    @Column(precision = 12, scale = 4)
    private BigDecimal netHours;

    @Column(precision = 12, scale = 4)
    private BigDecimal requiredHours;

    @Column(columnDefinition = "text")
    private String reason;

    protected GraduationHistoryEntity() {}

    public static GraduationHistoryEntity approved(
            UUID user,
            UUID actor,
            LocalDateTime at,
            GraduationEligibilityData data,
            String reason) {
        var entity = new GraduationHistoryEntity();
        entity.id = UUID.randomUUID();
        entity.userId = user;
        entity.approvedBy = actor;
        entity.approvedAt = at;
        entity.netHours = BigDecimal.valueOf(data.balance());
        entity.requiredHours = BigDecimal.valueOf(data.target());
        entity.reason = reason;
        return entity;
    }
}
