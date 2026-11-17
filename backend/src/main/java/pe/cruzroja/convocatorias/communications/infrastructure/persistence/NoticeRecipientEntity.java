package pe.cruzroja.convocatorias.communications.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Read model: communications needs a recipient's status and role, not the account aggregate. */
@Entity
@Immutable
@Table(name = "users")
public class NoticeRecipientEntity {
    @Id private UUID id;
    private String role;
    private String status;

    protected NoticeRecipientEntity() {}
}
