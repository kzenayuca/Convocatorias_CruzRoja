package pe.cruzroja.convocatorias.communications.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;
import pe.cruzroja.convocatorias.communications.application.NoticeRepository;
import pe.cruzroja.convocatorias.communications.domain.Announcement;

@Repository
public class JdbcNoticeRepository extends JdbcSupport implements NoticeRepository {
    public JdbcNoticeRepository(JdbcTemplate db) { super(db); }
    public List<Map<String,Object>> notices(UUID user) { return rows("SELECT * FROM notifications WHERE recipient_id=? ORDER BY is_read,id",user); }
    public void read(UUID id,UUID user,LocalDateTime at) { db.update("UPDATE notifications SET is_read=true,read_at=COALESCE(read_at,?) WHERE id=? AND recipient_id=?",at,id,user); }
    public void save(UUID id,Announcement a,UUID user) { db.update("INSERT INTO announcements(id,title,body,audience,published_by) VALUES (?,?,?,?,?)",id,a.title(),a.body(),a.audience(),user); }
    public List<UUID> recipients(String audience) { return db.queryForList("SELECT id FROM users WHERE status='ACTIVO' AND (?='TODOS' OR role=?)",UUID.class,audience,audience); }
}
