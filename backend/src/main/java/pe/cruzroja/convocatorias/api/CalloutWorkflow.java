package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import pe.cruzroja.convocatorias.domain.TeamCapacity;

/** Permisos, cupos y transiciones compartidos. Los cambios se invocan dentro de una transacción. */
@Service
public class CalloutWorkflow {
    public static final List<String> POSITIONS = List.of("RESPONSABLE", "SOCORRISTA", "GRADUADO", "FORMACION", "RADIOPERADOR", "PILOTO");
    private final JdbcTemplate db;
    public CalloutWorkflow(JdbcTemplate db) { this.db=db; }
    public static final String FIELDS="id,title,start_at,end_at,deadline,kind,status,created_by,responsible_id,opened_at,closed_at,total_volunteers,socorrista_slots,graduate_slots,trainee_slots,radio_slots,pilot_slots,service_code,requirements,photo_uploaded_at";
    public Map<String,Object> locked(UUID id) { return one(db,"SELECT "+FIELDS+" FROM callouts WHERE id=? FOR UPDATE",id); }
    public boolean canEdit(Map<String,Object> c) {
        return chief() || actor().graduated() && actor().role().equals("VOLUNTARIO") && actor().id().equals(c.get("responsible_id"))
            && db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND volunteer_id=? AND position='RESPONSABLE' AND status='CONFIRMADA'",Integer.class,c.get("id"),actor().id())==1;
    }
    public boolean canOperate(Map<String,Object> c) {
        return canEdit(c);
    }
    public void editPermission(Map<String,Object> c) { require(canEdit(c),"Solo jefatura o el responsable graduado asignado pueden gestionar esta convocatoria."); }
    public void operationPermission(Map<String,Object> c) { require(canOperate(c),"Solo jefatura o el encargado asignado pueden operar el servicio."); }
    public void teamPermission(Map<String,Object> c) { require(canEdit(c)||canOperate(c),"No puedes consultar este equipo."); }

    public int capacity(Map<String,Object> c,String position) {
        return switch(position) {
            case "RESPONSABLE" -> "SERVICIO".equals(c.get("kind")) ? 1 : 0;
            case "SOCORRISTA" -> total(c);
            case "GRADUADO" -> ((Number)c.get("graduate_slots")).intValue();
            case "FORMACION" -> total(c);
            case "RADIOPERADOR" -> ((Number)c.get("radio_slots")).intValue();
            case "PILOTO" -> ((Number)c.get("pilot_slots")).intValue();
            default -> 0;
        };
    }
    public int occupied(UUID callout,String position) {
        return db.queryForObject("SELECT count(*) FROM enrollments WHERE callout_id=? AND position=? AND status='CONFIRMADA'",Integer.class,callout,position);
    }
    public List<Map<String,Object>> positions(Map<String,Object> c) {
        var team=team(c,null);
        return List.of(
            Map.of("position","TOTAL","required",total(c),"filled",team[0]),
            Map.of("position","GRADUADO","required",capacity(c,"GRADUADO"),"filled",team[1]),
            Map.of("position","RADIOPERADOR","required",capacity(c,"RADIOPERADOR"),"filled",team[2]),
            Map.of("position","PILOTO","required",capacity(c,"PILOTO"),"filled",team[3]));
    }
    public int total(Map<String,Object> c) {
        if(c.get("total_volunteers")!=null) return ((Number)c.get("total_volunteers")).intValue();
        return ((Number)c.get("socorrista_slots")).intValue()+capacity(c,"GRADUADO")
            +((Number)c.get("trainee_slots")).intValue()+capacity(c,"RADIOPERADOR")+capacity(c,"PILOTO")+occupied(id(c,"id"),"RESPONSABLE");
    }
    private TeamCapacity limits(Map<String,Object> c) {
        return new TeamCapacity(total(c),capacity(c,"GRADUADO"),capacity(c,"RADIOPERADOR"),capacity(c,"PILOTO"));
    }
    private int[] team(Map<String,Object> c,UUID excluding) {
        var rows=db.queryForList("SELECT e.volunteer_id,e.position,u.graduated FROM enrollments e JOIN users u ON u.id=e.volunteer_id WHERE e.callout_id=? AND e.status='CONFIRMADA'",c.get("id"));
        int[] counts=new int[4];
        for(var row:rows) {
            if(Objects.equals(excluding,row.get("volunteer_id"))) continue;
            counts[0]++; if(Boolean.TRUE.equals(row.get("graduated"))) counts[1]++;
            if("RADIOPERADOR".equals(row.get("position"))) counts[2]++;
            if("PILOTO".equals(row.get("position"))) counts[3]++;
        }
        return counts;
    }
    public boolean fits(Map<String,Object> c) {
        var n=team(c,null); return limits(c).fits(n[0],n[1],n[2],n[3]);
    }
    public boolean canAdmit(Map<String,Object> c,UUID user,String position,UUID replacing) {
        var n=team(c,replacing);
        boolean graduated=Boolean.TRUE.equals(one(db,"SELECT graduated FROM users WHERE id=?",user).get("graduated"));
        return limits(c).fits(n[0]+1,n[1]+(graduated?1:0),n[2]+(position.equals("RADIOPERADOR")?1:0),n[3]+(position.equals("PILOTO")?1:0));
    }
    public boolean complete(Map<String,Object> c) {
        var n=team(c,null); return limits(c).complete(n[0],n[1],n[2],n[3]);
    }
    public boolean qualified(Map<String,Object> c,UUID user,String position) {
        if(!POSITIONS.contains(position) || (!Set.of("GRADUADO","FORMACION").contains(position) && capacity(c,position)==0)) return false;
        var p=one(db,"SELECT u.status,u.role,u.graduated,p.radio_qualified,p.pilot_qualified FROM users u JOIN volunteer_profiles p ON p.user_id=u.id WHERE u.id=?",user);
        if(!"ACTIVO".equals(p.get("status")) || !"VOLUNTARIO".equals(p.get("role"))) return false;
        if(!Set.of("SOCORRISTA","FORMACION").contains(position) && !Boolean.TRUE.equals(p.get("graduated"))) return false;
        return switch(position) {
            case "RESPONSABLE" -> Boolean.TRUE.equals(p.get("graduated")) && "VOLUNTARIO".equals(p.get("role"));
            case "FORMACION" -> !Boolean.TRUE.equals(p.get("graduated"));
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
        return !overlap(c,user) && POSITIONS.stream().filter(p -> !p.equals("RESPONSABLE")).anyMatch(p -> qualified(c,user,p));
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
        for(var user:recipients) Store.notify(db,user,"CONVOCATORIA",c.get("title")+": "+message,"/callouts/"+c.get("id"));
    }
    public void cancel(Map<String,Object> c,String reason) {
        db.update("UPDATE callouts SET status='CANCELADA',observations=? WHERE id=?",reason,c.get("id"));
        db.update("UPDATE enrollments SET status='CANCELADA',comment=? WHERE callout_id=? AND status IN ('CONFIRMADA','PENDIENTE','PENDIENTE_REEMPLAZO')",reason,c.get("id"));
        announce(c,"Cancelada. "+reason);
    }
    public void assignResponsible(Map<String,Object> c) {
        var users=db.queryForList("SELECT volunteer_id FROM enrollments WHERE callout_id=? AND position='RESPONSABLE' AND status='CONFIRMADA'",c.get("id"));
        Object next=users.isEmpty()?null:users.get(0).get("volunteer_id");
        if(!Objects.equals(c.get("responsible_id"),next)) db.update("INSERT INTO responsible_history(id,callout_id,previous_user_id,next_user_id,changed_by,changed_at,reason) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),c.get("id"),c.get("responsible_id"),next,actor().id(),now(),"Actualización por revisión de inscripción, cuenta o habilitaciones");
        db.update("UPDATE callouts SET responsible_id=? WHERE id=?",next,c.get("id"));
    }
}
