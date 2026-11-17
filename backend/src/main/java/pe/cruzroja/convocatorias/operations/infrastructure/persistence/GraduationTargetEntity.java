package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Read-only projection; operations does not depend on administration's persistence model. */
@Entity
@Immutable
@Table(name = "volunteer_hour_targets")
public class GraduationTargetEntity {
    @Id
    @Column(length = 20)
    private String condition;

    @Column(precision = 10, scale = 2)
    private BigDecimal hours;

    protected GraduationTargetEntity() {}
}
