package pe.cruzroja.convocatorias.membership.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read mapping; atomic native inserts preserve the existing person/year uniqueness contract. */
@Entity
@Immutable
@IdClass(AnnualEnrollmentEntity.Key.class)
@Table(name = "annual_enrollments")
public class AnnualEnrollmentEntity {
    @Id private UUID userId;
    @Id private int year;
    private UUID registeredBy;
    private LocalDateTime registeredAt;

    @Column(length = 30)
    private String source;

    protected AnnualEnrollmentEntity() {}

    public Map<String, Object> projection() {
        var row = new LinkedHashMap<String, Object>();
        row.put("year", year);
        row.put("registered_at", registeredAt);
        row.put("registered_by", registeredBy);
        row.put("source", source);
        return row;
    }

    public static class Key implements Serializable {
        private static final long serialVersionUID = 1L;
        private UUID userId;
        private int year;

        public Key() {}

        @Override
        public boolean equals(Object other) {
            return other instanceof Key key
                    && Objects.equals(userId, key.userId)
                    && year == key.year;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, year);
        }
    }
}
