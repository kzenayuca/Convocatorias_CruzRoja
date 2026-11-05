package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Permisos, cupos y transiciones compartidos. Los cambios se invocan dentro de una transacción. */
@Service
public class CalloutWorkflow {
    public static final List<String> POSITIONS = List.of("RESPONSABLE", "SOCORRISTA", "RADIOPERADOR", "PILOTO");
    private final JdbcTemplate db;
    public CalloutWorkflow(JdbcTemplate db) { this.db=db; }
    public Map<String,Object> locked(UUID id) { return one(db,"SELECT * FROM callouts WHERE id=? FOR UPDATE",id); }
    public boolean canEdit(Map<String,Object> c) {
        return chief() || manager() && actor().id().equals(c.get("created_by"));
    }
    public boolean canOperate(Map<String,Object> c) {
        return chief() || actor().graduated() && actor().id().equals(c.get("responsible_id"));
    }
    public void editPermission(Map<String,Object> c) { require(canEdit(c),"Solo jefatura o el reclutador creador pueden gestionar la convocatoria."); }
    public void operationPermission(Map<String,Object> c) { require(canOperate(c),"Solo jefatura o el encargado asignado pueden operar el servicio."); }
    public void teamPermission(Map<String,Object> c) { require(canEdit(c)||canOperate(c),"No puedes consultar este equipo."); }

    public int capacity(Map<String,Object> c,String position) {
        return switch(position) {
            case "RESPONSABLE" -> "SERVICIO".equals(c.get("kind")) ? 1 : 0;
            case "SOCORRISTA" -> ((Number)c.get("socorrista_slots")).intValue();
            case "RADIOPERADOR" -> ((Number)c.get("radio_slots")).intValue();
            case "PILOTO" -> ((Number)c.get("pilot_slots")).intValue();
            default -> 0;
        };
    }
    public int occupied(UUID callout,String position) {
        return db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND position=? AND status='CONFIRMADA'",Integer.class,callout,position);
    }
    public List<Map<String,Object>> positions(Map<String,Object> c) {
        return POSITIONS.stream().map(p -> Map.<String,Object>of("position",p,"required",capacity(c,p),"filled",occupied(id(c,"id"),p))).toList();
    }
    public boolean complete(Map<String,Object> c) {
        return POSITIONS.stream().allMatch(p -> occupied(id(c,"id"),p)>=capacity(c,p));
    }
    public boolean qualified(Map<String,Object> c,UUID user,String position) {
        if(!POSITIONS.contains(position) || capacity(c,position)==0) return false;
        var p=one(db,"SELECT u.status,u.graduated,p.training_level,p.radio_qualified,p.pilot_qualified FROM users u JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.id=?",user);
        var levels=List.of("BASICO","INTERMEDIO","AVANZADO");
        if(!"ACTIVO".equals(p.get("status")) || levels.indexOf(p.get("training_level"))<levels.indexOf(c.get("minimum_level"))) return false;
        if(!position.equals("SOCORRISTA") && !Boolean.TRUE.equals(p.get("graduated"))) return false;
        return switch(position) {
            case "RESPONSABLE" -> Boolean.TRUE.equals(p.get("graduated"));
            case "RADIOPERADOR" -> Boolean.TRUE.equals(p.get("radio_qualified"));
            case "PILOTO" -> Boolean.TRUE.equals(p.get("pilot_qualified"));
            default -> true;
        };
    }
    public boolean overlap(Map<String,Object> c,UUID user) {
        return db.queryForObject("""
            SELECT count(*) FROM enrollments e JOIN callouts other ON other.id=e.callout_id
            WHERE e.volunteer_id=? AND e.status='CONFIRMADA' AND other.status<>'CANCELADA'
            AND other.id<>? AND other.start_at<? AND other.end_at>?
            """,Integer.class,user,c.get("id"),time(c,"end_at"),time(c,"start_at"))>0;
    }
    public boolean compatible(Map<String,Object> c,UUID user) {
        return !overlap(c,user) && POSITIONS.stream().anyMatch(p -> qualified(c,user,p));
    }
    public void synchronize(Map<String,Object> c) {
        if(!Set.of("ABIERTA","CERRADA").contains(c.get("status")) || c.get("opened_at")!=null) return;
        String next=complete(c)?"CERRADA":"ABIERTA";
        if(!next.equals(c.get("status"))) {
            db.update("UPDATE callouts SET status=? WHERE id=?",next,c.get("id"));
            announce(c,"Estado del servicio: "+next);
            c.put("status",next);
        }
    }
    public void announce(Map<String,Object> c,String message) {
        var recipients=new LinkedHashSet<UUID>();
        for(var e:db.queryForList("SELECT DISTINCT volunteer_id FROM enrollments WHERE callout_id=?",c.get("id")))
            recipients.add(id(e,"volunteer_id"));
        if(c.get("created_by")!=null) recipients.add(id(c,"created_by"));
        if(c.get("responsible_id")!=null) recipients.add(id(c,"responsible_id"));
        for(var user:recipients) Store.notify(db,user,"CONVOCATORIA",c.get("title")+": "+message,"/services");
    }
    public void cancel(Map<String,Object> c,String reason) {
        db.update("UPDATE callouts SET status='CANCELADA',observations=? WHERE id=?",reason,c.get("id"));
        db.update("UPDATE enrollments SET status='CANCELADA',comment=? WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE','PENDIENTE_REEMPLAZO')",reason,c.get("id"));
        announce(c,"Cancelada. "+reason);
    }
    public void assignResponsible(Map<String,Object> c) {
        var users=db.queryForList("SELECT volunteer_id FROM enrollments WHERE callout_id=? AND position='RESPONSABLE' AND status='CONFIRMADA'",c.get("id"));
        db.update("UPDATE callouts SET responsible_id=? WHERE id=?",users.isEmpty()?null:users.get(0).get("volunteer_id"),c.get("id"));
    }
}
