package pe.cruzroja.convocatorias.callouts.api; import java.util.UUID; public record CalloutView(UUID id,String title,String status,String minimumLevel,int capacity,int enrolled) {}
