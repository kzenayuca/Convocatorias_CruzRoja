package pe.cruzroja.convocatorias.membership.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;

@Entity
@Table(name = "volunteer_profiles")
public class VolunteerProfileEntity {
    @Id private UUID userId;
    private String document;
    private String phone;
    private String trainingLevel;
    private boolean radioQualified;
    private boolean pilotQualified;

    protected VolunteerProfileEntity() {}

    public static VolunteerProfileEntity create(UUID user, String document, String phone) {
        var entity = new VolunteerProfileEntity();
        entity.userId = user;
        entity.document = document;
        entity.phone = phone;
        entity.trainingLevel = "BASICO";
        return entity;
    }

    public void storePhone(String value) {
        phone = value;
    }

    public void storeQualifications(Volunteer volunteer) {
        radioQualified = volunteer.radioQualified();
        pilotQualified = volunteer.pilotQualified();
    }
}
