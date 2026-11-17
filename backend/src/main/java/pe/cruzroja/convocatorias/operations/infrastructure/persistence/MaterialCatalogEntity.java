package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Optional legacy reference catalog, not a stock reservation aggregate. */
@Entity
@Immutable
@Table(name = "materials")
public class MaterialCatalogEntity {
    @Id private UUID id;
    private String name;
    private String category;

    protected MaterialCatalogEntity() {}

    public String name() {
        return name;
    }

    public Map<String, Object> projection() {
        var row = new LinkedHashMap<String, Object>();
        row.put("id", id);
        row.put("name", name);
        row.put("category", category);
        return row;
    }
}
