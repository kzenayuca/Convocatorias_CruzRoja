package pe.cruzroja.convocatorias.operations.infrastructure;

import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import pe.cruzroja.convocatorias.operations.application.MaterialRepository;
import pe.cruzroja.convocatorias.operations.application.MaterialsUseCases;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.CalloutEntity;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.MaterialCatalogEntity;
import pe.cruzroja.convocatorias.operations.infrastructure.persistence.MaterialRequestEntity;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;
import pe.cruzroja.convocatorias.shared.application.NotFound;
import pe.cruzroja.convocatorias.shared.infrastructure.ManagedEntities;

@Repository
@Transactional
public class JpaMaterialRepository implements MaterialRepository {
    private final ManagedEntities entities;
    private final ExecutionContext context;
    private final EntityManager persistence;

    public JpaMaterialRepository(
            EntityManager persistence, ManagedEntities entities, ExecutionContext context) {
        this.persistence = persistence;
        this.entities = entities;
        this.context = context;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> catalog() {
        return persistence
                .createQuery(
                        "select m from MaterialCatalogEntity m order by m.name",
                        MaterialCatalogEntity.class)
                .getResultList()
                .stream()
                .map(MaterialCatalogEntity::projection)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public String name(UUID id) {
        var entity = persistence.find(MaterialCatalogEntity.class, id);
        if (entity == null) throw new NotFound();
        return entity.name();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(UUID id) {
        var requests =
                persistence
                        .createQuery(
                                """
                select m from MaterialRequestEntity m where m.calloutId = :callout order by m.createdAt
                """,
                                MaterialRequestEntity.class)
                        .setParameter("callout", id)
                        .getResultList();
        var actors = new HashSet<UUID>();
        for (var request : requests) {
            if (request.receivedBy() != null) actors.add(request.receivedBy());
            if (request.returnedBy() != null) actors.add(request.returnedBy());
        }
        var names = new HashMap<UUID, String>();
        if (!actors.isEmpty()) {
            persistence
                    .createQuery(
                            "select a.id, a.name from MaterialActorEntity a where a.id in :ids",
                            Object[].class)
                    .setParameter("ids", actors)
                    .getResultList()
                    .forEach(row -> names.put((UUID) row[0], (String) row[1]));
        }
        var result = new ArrayList<Map<String, Object>>();
        for (var request : requests) {
            var row = request.projection();
            row.put("received_name", names.get(request.receivedBy()));
            row.put("returned_name", names.get(request.returnedBy()));
            result.add(row);
        }
        return result;
    }

    @Override
    public Map<String, Object> find(UUID id, boolean lock) {
        var entity =
                lock
                        ? entities.lock(MaterialRequestEntity.class, id)
                        : persistence.find(MaterialRequestEntity.class, id);
        if (entity == null) throw new NotFound();
        return entity.projection();
    }

    @Override
    public void request(UUID id, UUID actor, MaterialsUseCases.Material request, String name) {
        entities.insert(MaterialRequestEntity.create(id, actor, request, name, context.now()));
    }

    @Override
    public void handover(
            UUID id,
            UUID callout,
            MaterialsUseCases.Handover request,
            UUID actor,
            LocalDateTime at,
            String note)
            throws IllegalArgumentException {
        entities.lock(MaterialRequestEntity.class, id).storeHandover(request, actor, at);
        entities.flush();
        if ("DEVOLUCION".equals(request.action())) {
            entities.lock(CalloutEntity.class, callout).appendObservation(note);
            entities.flush();
        }
    }
}
