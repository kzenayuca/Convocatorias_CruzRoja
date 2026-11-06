package pe.cruzroja.convocatorias.communications.application;

import java.util.*;
import pe.cruzroja.convocatorias.shared.application.*;
import pe.cruzroja.convocatorias.communications.domain.Announcement;

public class NoticesUseCases extends UseCaseSupport {
    private final NoticeRepository repository;
    public NoticesUseCases(NoticeRepository repository,ExecutionContext context,NotificationPublisher notifications) { super(context,notifications); this.repository=repository; }
    public List<Map<String,Object>> notices() { return repository.notices(actor().id()); }
    public void read(UUID id) { repository.read(id,actor().id(),now()); }
    public void publish(Announcement r) {
        require(chief(),"Solo jefatura publica comunicados."); repository.save(UUID.randomUUID(),r,actor().id());
        for(var user:repository.recipients(r.audience())) notifyUser(user,"COMUNICADO",r.title()+": "+r.body(),"/notifications");
    }
}
