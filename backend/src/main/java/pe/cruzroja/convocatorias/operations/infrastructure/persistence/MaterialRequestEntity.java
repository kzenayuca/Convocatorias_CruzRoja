package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.application.MaterialsUseCases;

@Entity
@Table(name = "material_requests")
public class MaterialRequestEntity {
    @Id private UUID id;
    private UUID calloutId;
    private UUID requestedBy;
    private UUID materialId;
    private String name;
    private int quantity;

    @Column(columnDefinition = "text")
    private String details;

    private LocalDateTime createdAt;
    private int receivedQuantity;
    private int returnedQuantity;
    private UUID receivedBy;
    private UUID returnedBy;
    private LocalDateTime receivedAt;
    private LocalDateTime returnedAt;

    @Column(columnDefinition = "text")
    private String conditionNote;

    protected MaterialRequestEntity() {}

    public static MaterialRequestEntity create(
            UUID callout,
            UUID actor,
            MaterialsUseCases.Material request,
            String name,
            LocalDateTime at) {
        var entity = new MaterialRequestEntity();
        entity.id = UUID.randomUUID();
        entity.calloutId = callout;
        entity.requestedBy = actor;
        entity.materialId = request.materialId();
        entity.name = name;
        entity.quantity = request.quantity();
        entity.details = request.details();
        entity.createdAt = at;
        return entity;
    }

    public void storeHandover(MaterialsUseCases.Handover request, UUID actor, LocalDateTime at) {
        new pe.cruzroja.convocatorias.operations.domain.MaterialRequest(
                        id, quantity, receivedQuantity, receivedAt != null, returnedAt != null)
                .requireHandover(request.action(), request.quantity());
        conditionNote = request.observations();
        if ("RECEPCION".equals(request.action())) {
            receivedQuantity = request.quantity();
            receivedBy = actor;
            receivedAt = at;
        } else {
            returnedQuantity = request.quantity();
            returnedBy = actor;
            returnedAt = at;
        }
    }

    public UUID receivedBy() {
        return receivedBy;
    }

    public UUID returnedBy() {
        return returnedBy;
    }

    public Map<String, Object> projection() {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("callout_id", calloutId);
        row.put("requested_by", requestedBy);
        row.put("material_id", materialId);
        row.put("name", name);
        row.put("quantity", quantity);
        row.put("details", details);
        row.put("created_at", createdAt);
        row.put("received_quantity", receivedQuantity);
        row.put("returned_quantity", returnedQuantity);
        row.put("received_by", receivedBy);
        row.put("returned_by", returnedBy);
        row.put("received_at", receivedAt);
        row.put("returned_at", returnedAt);
        row.put("condition_note", conditionNote);
        return row;
    }
}
