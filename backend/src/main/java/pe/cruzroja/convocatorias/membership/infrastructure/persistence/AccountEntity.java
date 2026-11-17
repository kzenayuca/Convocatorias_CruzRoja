package pe.cruzroja.convocatorias.membership.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.cruzroja.convocatorias.membership.application.AccountsUseCases;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;

@Entity
@Table(name = "users")
public class AccountEntity {
    @Id private UUID id;
    private String name;
    private String surnames;
    private String email;
    private String passwordHash;
    private String role;
    private boolean graduated;
    private String status;
    private String occupation;

    @Column(columnDefinition = "text")
    private String reviewComment;

    protected AccountEntity() {}

    public static AccountEntity pending(
            UUID id, AccountsUseCases.Registration request, String passwordHash) {
        var entity = new AccountEntity();
        entity.id = id;
        entity.name = request.profile().name().trim();
        entity.surnames = request.profile().surnames().trim();
        entity.email = request.profile().email().trim().toLowerCase(java.util.Locale.ROOT);
        entity.occupation = request.profile().occupation().trim();
        entity.passwordHash = passwordHash;
        entity.role = "VOLUNTARIO";
        entity.status = "PENDIENTE";
        return entity;
    }

    public void storeReview(Volunteer volunteer, String comment) {
        role = volunteer.role();
        status = volunteer.status();
        graduated = volunteer.graduated();
        reviewComment = comment;
    }
}
