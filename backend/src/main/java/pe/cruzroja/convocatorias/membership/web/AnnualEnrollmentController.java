package pe.cruzroja.convocatorias.membership.web;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.membership.application.AnnualEnrollmentUseCases;
import pe.cruzroja.convocatorias.shared.application.ExecutionContext;

@RestController
@RequestMapping("/api/v1")
public class AnnualEnrollmentController {
    private final AnnualEnrollmentUseCases useCases;
    private final ExecutionContext context;
    public AnnualEnrollmentController(AnnualEnrollmentUseCases useCases,ExecutionContext context) { this.useCases=useCases; this.context=context; }
    @GetMapping("/profile/annual-enrollments") public Map<String,Object> mine() { return useCases.mine(); }
    @PostMapping("/profile/annual-enrollments") public void register(@RequestBody AnnualEnrollmentUseCases.Request r) { useCases.register(context.actor().id(),r); }
    @GetMapping("/users/{id}/annual-enrollments") public Map<String,Object> detail(@PathVariable UUID id) { return useCases.detail(id); }
    @PostMapping("/users/{id}/annual-enrollments") public void registerUser(@PathVariable UUID id,@RequestBody AnnualEnrollmentUseCases.Request r) { useCases.register(id,r); }
}
