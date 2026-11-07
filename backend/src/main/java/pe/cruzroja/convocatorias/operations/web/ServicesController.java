package pe.cruzroja.convocatorias.operations.web;

import java.time.LocalDate;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases.*;
import pe.cruzroja.convocatorias.shared.web.PdfDocument;

@RestController
@RequestMapping("/api/v1")
public class ServicesController {
    private final ServicesUseCases useCases;

    public ServicesController(ServicesUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping("/callouts")
    public List<Map<String, Object>> callouts(
            @RequestParam(defaultValue = "false") boolean history) {
        return useCases.callouts(history);
    }

    @GetMapping("/callouts/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return useCases.detail(id);
    }

    @PostMapping("/callouts")
    public Map<String, UUID> create(@RequestBody Callout r) {
        return useCases.create(r);
    }

    @PutMapping("/callouts/{id}")
    public void edit(@PathVariable UUID id, @RequestBody Callout r) {
        useCases.edit(id, r);
    }

    @PostMapping("/callouts/{id}/status")
    public void status(@PathVariable UUID id, @RequestBody Status r) {
        useCases.status(id, r);
    }

    @GetMapping("/enrollments")
    public List<Map<String, Object>> mine() {
        return useCases.mine();
    }

    @GetMapping("/callouts/{id}/enrollments")
    public List<Map<String, Object>> applicants(@PathVariable UUID id) {
        return useCases.applicants(id);
    }

    @PostMapping("/enrollments/{id}/review")
    public void review(@PathVariable UUID id, @RequestBody Review r) {
        useCases.review(id, r);
    }

    @PostMapping("/callouts/{id}/operation")
    public void operation(@PathVariable UUID id, @RequestBody Operation r) {
        useCases.operation(id, r);
    }

    @PostMapping("/callouts/{id}/photo")
    public void photo(@PathVariable UUID id, @RequestParam MultipartFile file)
            throws java.io.IOException {
        if (file.getSize() > 10 * 1024 * 1024)
            throw new org.springframework.web.multipart.MaxUploadSizeExceededException(
                    10 * 1024 * 1024);
        useCases.photo(id, file.getBytes());
    }

    @GetMapping("/callouts/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id) {
        var p = useCases.photo(id);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .contentType(MediaType.parseMediaType(p.type()))
                .body(p.bytes());
    }

    @GetMapping("/service-codes")
    public List<Map<String, Object>> codes() {
        return useCases.codes();
    }

    @GetMapping("/callouts/{id}/responsible-candidates")
    public List<Map<String, Object>> candidates(@PathVariable UUID id) {
        return useCases.responsibleCandidates(id);
    }

    @PutMapping("/callouts/{id}/responsible")
    public void responsible(@PathVariable UUID id, @RequestBody Responsible r) {
        useCases.responsible(id, r);
    }

    @PostMapping("/callouts/{id}/responsible/change")
    public void changeResponsible(@PathVariable UUID id, @RequestBody ResponsibleChange r) {
        useCases.changeResponsible(id, r);
    }

    @GetMapping("/callouts/{id}/responsible/history")
    public List<Map<String, Object>> history(@PathVariable UUID id) {
        return useCases.responsibleHistory(id);
    }

    @GetMapping("/schedule")
    public List<Map<String, Object>> schedule(@RequestParam LocalDate date) {
        return useCases.schedule(date);
    }

    @GetMapping("/schedule/pdf")
    public ResponseEntity<byte[]> pdf(@RequestParam LocalDate date) throws java.io.IOException {
        return PdfDocument.download(useCases.schedulePdf(date));
    }

    @GetMapping("/profile/photos")
    public List<Map<String, Object>> photos() {
        return useCases.myPhotos();
    }
}
