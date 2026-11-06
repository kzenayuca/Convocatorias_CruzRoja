package pe.cruzroja.convocatorias.operations.infrastructure;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import pe.cruzroja.convocatorias.shared.infrastructure.JdbcSupport;
import pe.cruzroja.convocatorias.operations.application.*;

@Repository
public class JdbcMaterialRepository extends JdbcSupport implements MaterialRepository {
    public JdbcMaterialRepository(JdbcTemplate db) { super(db); }
    public List<Map<String,Object>> catalog() { return rows("SELECT id,name,category FROM materials ORDER BY name"); }
    public List<Map<String,Object>> list(UUID id) { return rows("SELECT m.*,r.name AS received_name,d.name AS returned_name FROM material_requests m LEFT JOIN users r ON r.id=m.received_by LEFT JOIN users d ON d.id=m.returned_by WHERE m.callout_id=? ORDER BY m.created_at",id); }
    public String name(UUID id) { return (String)row("SELECT name FROM materials WHERE id=?",id).get("name"); }
    public void request(UUID id,UUID actor,MaterialsUseCases.Material r,String name) { db.update("INSERT INTO material_requests(id,callout_id,requested_by,material_id,name,quantity,details) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),id,actor,r.materialId(),name,r.quantity(),r.details()); }
    public Map<String,Object> find(UUID id,boolean lock) { return row("SELECT * FROM material_requests WHERE id=?"+(lock?" FOR UPDATE":""),id); }
    public void handover(UUID id,UUID callout,MaterialsUseCases.Handover r,UUID actor,LocalDateTime at,String note) {
        if(r.action().equals("RECEPCION")) db.update("UPDATE material_requests SET received_quantity=?,received_by=?,received_at=?,condition_note=? WHERE id=?",r.quantity(),actor,at,r.observations(),id);
        else { db.update("UPDATE material_requests SET returned_quantity=?,returned_by=?,returned_at=?,condition_note=? WHERE id=?",r.quantity(),actor,at,r.observations(),id); db.update("UPDATE callouts SET observations=observations || ? WHERE id=?",note,callout); }
    }
}
