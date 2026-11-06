package pe.cruzroja.convocatorias.operations.application;

import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.MaterialRequest;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.application.Projection.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class MaterialsUseCases extends UseCaseSupport {
    private final MaterialRepository repository;
    private final CalloutRepository callouts;
    private final CalloutWorkflow workflow;
    public MaterialsUseCases(MaterialRepository repository,CalloutRepository callouts,CalloutWorkflow workflow,ExecutionContext context,NotificationPublisher notifications) { super(context,notifications); this.repository=repository; this.callouts=callouts; this.workflow=workflow; }
    public record Material(UUID materialId,String name,int quantity,String details) { public Material { required(name,160,"Material"); required(details,3000,"Especificaciones"); valid(quantity>=1 && quantity<=10000,"Cantidad no válida."); } }
    public record Handover(String action,int quantity,String observations) { public Handover { required(action,30,"Movimiento"); required(observations,3000,"Observaciones"); valid(quantity>=0 && quantity<=10000,"Cantidad no válida."); } }
    public List<Map<String,Object>> catalog() { return repository.catalog(); }
    public List<Map<String,Object>> list(UUID id) { workflow.operationPermission(callouts.snapshot(id,false)); return repository.list(id); }
    public void request(UUID id,Material r) { var c=workflow.locked(id); workflow.operationPermission(c); valid(time(c,"end_at").isAfter(now()) && !text(c,"status").equals("CANCELADA"),"El servicio ya terminó o se canceló."); repository.request(id,actor().id(),r,r.materialId()==null?r.name():repository.name(r.materialId())); }
    public void handover(UUID id,Handover r) {
        var initial=repository.find(id,false); var c=workflow.locked(id(initial,"callout_id")); workflow.operationPermission(c); var m=repository.find(id,true);
        new MaterialRequest(id,((Number)m.get("quantity")).intValue(),((Number)m.get("received_quantity")).intValue(),m.get("received_at")!=null,m.get("returned_at")!=null).requireHandover(r.action(),r.quantity());
        repository.handover(id,id(c,"id"),r,actor().id(),now(),"\nMaterial "+m.get("name")+": devuelto "+r.quantity()+" de "+m.get("received_quantity")+". "+r.observations());
    }
    public Document pdf(UUID id) {
        var rows=list(id); var c=callouts.view(id); var lines=new ArrayList<String>(); lines.add("Código: "+Objects.toString(c.get("service_code"),"Pendiente")); lines.add("Lugar: "+c.get("location")+" | Inicio: "+c.get("start_at")); lines.add("");
        for(var m:rows) { lines.add(m.get("quantity")+" x "+m.get("name")); lines.add("Detalle: "+m.get("details")); lines.add("Recibido: "+m.get("received_quantity")+" | Devuelto: "+m.get("returned_quantity")); lines.add("Recibe: "+Objects.toString(m.get("received_name"),"Pendiente")+" | Devuelve: "+Objects.toString(m.get("returned_name"),"Pendiente")); if(m.get("condition_note")!=null) lines.add("Observaciones: "+m.get("condition_note")); lines.add(""); }
        if(rows.isEmpty()) lines.add("Sin materiales solicitados."); return new Document("materiales-"+id+".pdf","Solicitud de almacén - "+c.get("title"),lines);
    }
}
