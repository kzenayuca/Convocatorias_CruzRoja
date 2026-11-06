package pe.cruzroja.convocatorias.operations.domain;

import java.time.LocalDateTime;
import java.util.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** El correlativo avanza por creación dentro de categoría/año, nunca por mes. */
public record ServiceCode(String category,LocalDateTime createdAt,int sequence) {
    private static final List<String> MONTHS=List.of("ENE","FEB","MAR","ABR","MAY","JUN","JUL","AGO","SEP","OCT","NOV","DIC");
    public ServiceCode { valid(Set.of("E","I","G").contains(category),"Selecciona E (Externo), I (Interno) o G (Guardia)."); Objects.requireNonNull(createdAt); valid(sequence>0,"Correlativo no válido."); }
    public String value() { return "%s-%s-%d-%03d".formatted(category,MONTHS.get(createdAt.getMonthValue()-1),createdAt.getYear(),sequence); }
}
