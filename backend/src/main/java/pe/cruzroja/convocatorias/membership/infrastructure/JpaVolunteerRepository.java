package pe.cruzroja.convocatorias.membership.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.membership.application.AccountsUseCases;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.membership.infrastructure.persistence.AccountEntity;
import pe.cruzroja.convocatorias.membership.infrastructure.persistence.VolunteerProfileEntity;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

@Repository
@Transactional
public class JpaVolunteerRepository extends JdbcVolunteerRepository {
    private final ManagedEntities entities;

    public JpaVolunteerRepository(JdbcTemplate db, ManagedEntities entities) {
        super(db);
        this.entities = entities;
    }

    @Override
    public void register(UUID id, AccountsUseCases.Registration request, String hash) {
        entities.insert(AccountEntity.pending(id, request, hash));
        entities.insert(
                VolunteerProfileEntity.create(id, request.profile().document(), request.phone()));
    }

    @Override
    public void phone(UUID user, String phone) {
        entities.lock(VolunteerProfileEntity.class, user).storePhone(phone);
        entities.flush();
    }

    @Override
    public void save(Volunteer volunteer, String comment) {
        entities.lock(AccountEntity.class, volunteer.id()).storeReview(volunteer, comment);
        entities.lock(VolunteerProfileEntity.class, volunteer.id()).storeQualifications(volunteer);
        entities.flush();
    }
}
