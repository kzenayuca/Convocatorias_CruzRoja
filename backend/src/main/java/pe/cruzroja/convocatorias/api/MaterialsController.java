package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/** Solicitudes al almacén externo: no reserva ni modifica stock institucional. */
@RestController
@RequestMapping("/api/v1")
public class MaterialsController {
    private final JdbcTemplate db;
    private final CalloutWorkflow workflow;
    public MaterialsController(JdbcTemplate db,CalloutWorkflow workflow) { this.db=db; this.workflow=workflow; }
    public record Material(UUID materialId,@NotBlank @Size(max=160) String name,
                           @Min(1) @Max(10000) int quantity,@NotBlank @Size(max=3000) String details) { }
    public record Handover(@NotBlank String action,@Min(0) @Max(10000) int quantity,
                           @NotBlank @Size(max=3000) String observations) { }
    @GetMapping("/material-catalog") public List<Map<String,Object>> catalog() {
        return db.queryForList("SELECT id,name,category FROM materials ORDER BY name");
    }
    @GetMapping("/callouts/{id}/materials") public List<Map<String,Object>> list(@PathVariable UUID id) {
        workflow.operationPermission(one(db,"SELECT * FROM callouts WHERE id=?",id));
        return db.queryForList("""
            SELECT m.*,r.name AS received_name,d.name AS returned_name FROM material_requests m
            LEFT JOIN users r ON r.id=m.received_by LEFT JOIN users d ON d.id=m.returned_by
            WHERE m.callout_id=? ORDER BY m.created_at
            """,id);
    }
    @PostMapping("/callouts/{id}/materials") @Transactional
    public void request(@PathVariable UUID id,@Valid @RequestBody Material r) {
        var c=workflow.locked(id); workflow.operationPermission(c);
        valid(time(c,"end_at").isAfter(now()) && !text(c,"status").equals("CANCELADA"),"El servicio ya terminó o se canceló.");
        String name=r.materialId()==null?r.name():text(one(db,"SELECT name FROM materials WHERE id=?",r.materialId()),"name");
        db.update("INSERT INTO material_requests(id,callout_id,requested_by,material_id,name,quantity,details) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),id,actor().id(),r.materialId(),name,r.quantity(),r.details());
    }
    @PostMapping("/materials/{id}/handover") @Transactional
    public void handover(@PathVariable UUID id,@Valid @RequestBody Handover r) {
        var initial=one(db,"SELECT callout_id FROM material_requests WHERE id=?",id);
        var c=workflow.locked(id(initial,"callout_id")); workflow.operationPermission(c);
        var m=one(db,"SELECT * FROM material_requests WHERE id=? FOR UPDATE",id);
        if(r.action().equals("RECEPCION")) {
            valid(m.get("received_at")==null && r.quantity()>0 && r.quantity()<=((Number)m.get("quantity")).intValue(),"La recepción ya se registró o la cantidad supera lo solicitado.");
            db.update("UPDATE material_requests SET received_quantity=?,received_by=?,received_at=?,condition_note=? WHERE id=?",r.quantity(),actor().id(),now(),r.observations(),id);
        } else {
            valid(r.action().equals("DEVOLUCION") && m.get("received_at")!=null && m.get("returned_at")==null,"Registra primero la recepción; la devolución no puede repetirse.");
            valid(r.quantity()<=((Number)m.get("received_quantity")).intValue(),"No puedes devolver más unidades de las recibidas.");
            db.update("UPDATE material_requests SET returned_quantity=?,returned_by=?,returned_at=?,condition_note=? WHERE id=?",r.quantity(),actor().id(),now(),r.observations(),id);
            db.update("UPDATE callouts SET observations=observations || ? WHERE id=?","\nMaterial "+m.get("name")+": devuelto "+r.quantity()+" de "+m.get("received_quantity")+". "+r.observations(),c.get("id"));
        }
    }
    @GetMapping("/callouts/{id}/materials/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable UUID id) throws java.io.IOException {
        var rows=list(id); var c=one(db,"SELECT title,service_code,location,start_at FROM callouts WHERE id=?",id);
        var lines=new ArrayList<String>(); lines.add("Código: "+Objects.toString(c.get("service_code"),"Pendiente"));
        lines.add("Lugar: "+c.get("location")+" | Inicio: "+c.get("start_at")); lines.add("");
        for(var m:rows) {
            lines.add(m.get("quantity")+" x "+m.get("name")); lines.add("Detalle: "+m.get("details"));
            lines.add("Recibido: "+m.get("received_quantity")+" | Devuelto: "+m.get("returned_quantity"));
            lines.add("Recibe: "+Objects.toString(m.get("received_name"),"Pendiente")+" | Devuelve: "+Objects.toString(m.get("returned_name"),"Pendiente"));
            if(m.get("condition_note")!=null) lines.add("Observaciones: "+m.get("condition_note")); lines.add("");
        }
        if(rows.isEmpty()) lines.add("Sin materiales solicitados.");
        return PdfDocument.download("materiales-"+id+".pdf","Solicitud de almacén - "+c.get("title"),lines);
    }
}
