package pe.cruzroja.convocatorias.users.application;

import pe.cruzroja.convocatorias.users.api.CurrentUser;
import pe.cruzroja.convocatorias.users.domain.TrainingLevel;
import pe.cruzroja.convocatorias.users.domain.VolunteerProfile;

public class TrainingLevelService {
    public void assign(CurrentUser currentUser, VolunteerProfile profile, TrainingLevel level) {
        if (!"JEFATURA".equals(currentUser.role())) {
            throw new IllegalStateException("Solo jefatura puede asignar el nivel de formación");
        }
        profile.promoteTo(level);
    }
}
