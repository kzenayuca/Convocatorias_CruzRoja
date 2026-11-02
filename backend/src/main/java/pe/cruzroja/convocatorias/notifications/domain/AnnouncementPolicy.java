package pe.cruzroja.convocatorias.notifications.domain;

import pe.cruzroja.convocatorias.users.domain.UserRole;

public class AnnouncementPolicy {
    public void validatePublisher(UserRole role) {
        if (role != UserRole.JEFATURA) {
            throw new IllegalStateException("Solo jefatura puede publicar comunicados");
        }
    }
}
