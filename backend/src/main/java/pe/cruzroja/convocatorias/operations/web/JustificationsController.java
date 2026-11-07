package pe.cruzroja.convocatorias.operations.web;

import java.io.IOException;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.*;
import pe.cruzroja.convocatorias.operations.application.ReplacementUseCases;
import pe.cruzroja.convocatorias.operations.application.ReplacementUseCases.*;

@RestController
@RequestMapping("/api/v1")
public class JustificationsController {
    private final ReplacementUseCases useCases;

    public JustificationsController(ReplacementUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping(value = "/enrollments/{id}/cancel-request", consumes = "application/json")
    public Map<String, UUID> submit(@PathVariable UUID id, @RequestBody Request r) {
        return useCases.submit(id, r, null);
    }

    @PostMapping(value = "/enrollments/{id}/cancel-request", consumes = "multipart/form-data")
    public Map<String, UUID> submitFile(
            @PathVariable UUID id,
            @RequestParam String reason,
            @RequestParam(required = false) String proposedReplacementName,
            @RequestParam(required = false) MultipartFile file)
            throws IOException {
        return useCases.submit(id, new Request(reason, proposedReplacementName), bytes(file));
    }

    @GetMapping("/justifications/{id}")
    public Map<String, Object> detail(@PathVariable UUID id) {
        return useCases.detail(id);
    }

    @GetMapping("/justifications/{id}/replacement-candidates")
    public List<Map<String, Object>> candidates(@PathVariable UUID id) {
        return useCases.candidates(id);
    }

    @PostMapping("/justifications/{id}/replacement-resolution")
    public void resolve(@PathVariable UUID id, @RequestBody Resolution r) {
        useCases.resolve(id, r);
    }

    @GetMapping("/justifications/{id}/evidence")
    public ResponseEntity<byte[]> evidence(@PathVariable UUID id) {
        var file = useCases.evidence(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.type()))
                .header("Cache-Control", "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header(
                        "Content-Disposition",
                        "inline; filename=\"evidencia"
                                + (file.type().equals("image/png") ? ".png" : ".jpg")
                                + "\"")
                .body(file.bytes());
    }

    static byte[] bytes(MultipartFile file) throws IOException {
        if (file == null) return null;
        if (file.getSize() > 10 * 1024 * 1024)
            throw new MaxUploadSizeExceededException(10 * 1024 * 1024);
        return file.getBytes();
    }
}
