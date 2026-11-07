package pe.cruzroja.convocatorias.administration.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "volunteer_hour_targets")
public class HourTargetEntity {
    @Id
    @Column(length = 20)
    private String condition;

    @Column(precision = 10, scale = 2)
    private BigDecimal hours;

    private UUID updatedBy;
    private LocalDateTime updatedAt;

    protected HourTargetEntity() {}

    public void store(BigDecimal value, UUID actor, LocalDateTime at) {
        hours = value;
        updatedBy = actor;
        updatedAt = at;
    }
}
