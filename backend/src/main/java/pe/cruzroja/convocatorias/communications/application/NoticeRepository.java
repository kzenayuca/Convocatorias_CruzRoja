package pe.cruzroja.convocatorias.communications.application;

import java.time.LocalDateTime;
import java.util.*;
import pe.cruzroja.convocatorias.communications.domain.Announcement;

public interface NoticeRepository {
    List<Map<String,Object>> notices(UUID user);
    void read(UUID notice,UUID user,LocalDateTime at);
    void save(UUID id,Announcement announcement,UUID actor);
    List<UUID> recipients(String audience);
}
