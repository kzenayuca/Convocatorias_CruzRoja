package pe.cruzroja.convocatorias.communications.infrastructure;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import pe.cruzroja.convocatorias.shared.application.NotificationPublisher;

@Component
public class JdbcNotificationPublisher implements NotificationPublisher {
    private final JdbcTemplate db;
    public JdbcNotificationPublisher(JdbcTemplate db) { this.db=db; }
    public void publish(UUID user,String type,String message,String link) { db.update("INSERT INTO notifications(id,recipient_id,type,message,link) VALUES (?,?,?,?,?)",UUID.randomUUID(),user,type,message,link); }
    public void publishOnce(UUID user,String key,String type,String message,String link) { db.update("INSERT INTO notifications(id,recipient_id,event_key,type,message,link) VALUES (?,?,?,?,?,?) ON CONFLICT(recipient_id,event_key) WHERE event_key IS NOT NULL DO NOTHING",UUID.randomUUID(),user,key,type,message,link); }
}
