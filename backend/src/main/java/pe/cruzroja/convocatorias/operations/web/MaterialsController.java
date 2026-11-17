package pe.cruzroja.convocatorias.operations.web;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.operations.application.MaterialsUseCases;
import pe.cruzroja.convocatorias.operations.application.MaterialsUseCases.*;
import pe.cruzroja.convocatorias.shared.web.PdfDocument;

@RestController
@RequestMapping("/api/v1")
public class MaterialsController {
    private final MaterialsUseCases useCases;

    public MaterialsController(MaterialsUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping("/material-catalog")
    public List<Map<String, Object>> catalog() {
        return useCases.catalog();
    }

    @GetMapping("/callouts/{id}/materials")
    public List<Map<String, Object>> list(@PathVariable UUID id) {
        return useCases.list(id);
    }

    @PostMapping("/callouts/{id}/materials")
    public void request(@PathVariable UUID id, @RequestBody Material r) {
        useCases.request(id, r);
    }

    @PostMapping("/materials/{id}/handover")
    public void handover(@PathVariable UUID id, @RequestBody Handover r) {
        useCases.handover(id, r);
    }

    @GetMapping("/callouts/{id}/materials/pdf")
    public org.springframework.http.ResponseEntity<byte[]> pdf(@PathVariable UUID id)
            throws java.io.IOException {
        return PdfDocument.download(useCases.pdf(id));
    }
}
