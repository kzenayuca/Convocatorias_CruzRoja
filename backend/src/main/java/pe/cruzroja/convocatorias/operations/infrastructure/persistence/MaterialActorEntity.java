package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Minimal projection for receipt/return labels; no dependency on membership's persistence model.
 */
@Entity
@Immutable
@Table(name = "users")
public class MaterialActorEntity {
    @Id private UUID id;
    private String name;

    protected MaterialActorEntity() {}
}
