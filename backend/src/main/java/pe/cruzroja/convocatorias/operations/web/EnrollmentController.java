package pe.cruzroja.convocatorias.operations.web;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.operations.application.EnrollmentUseCases;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases.Assignment;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases.EnrollmentRequest;

@RestController
@RequestMapping("/api/v1/callouts/{id}")
public class EnrollmentController {
    private final EnrollmentUseCases useCases;

    public EnrollmentController(EnrollmentUseCases useCases) {
        this.useCases = useCases;
    }

    @PostMapping("/enrollments")
    public void enroll(@PathVariable UUID id, @RequestBody EnrollmentRequest request) {
        useCases.enroll(id, request);
    }

    @PostMapping("/assignments")
    public void assign(@PathVariable UUID id, @RequestBody Assignment request) {
        useCases.assign(id, request);
    }

    @GetMapping("/assignment-candidates")
    public List<Map<String, Object>> candidates(
            @PathVariable UUID id, @RequestParam String position) {
        return useCases.candidates(id, position);
    }
}
