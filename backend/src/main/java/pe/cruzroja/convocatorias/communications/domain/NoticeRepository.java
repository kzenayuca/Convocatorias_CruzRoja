package pe.cruzroja.convocatorias.communications.domain;

import java.time.LocalDateTime;
import java.util.*;

public interface NoticeRepository {
    List<Notification> notices(UUID user);

    void read(UUID notice, UUID user, LocalDateTime at);

    void save(UUID id, Announcement announcement, UUID actor);

    List<UUID> recipients(String audience);
}
