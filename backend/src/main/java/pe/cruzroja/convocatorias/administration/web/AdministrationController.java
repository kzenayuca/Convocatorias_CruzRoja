package pe.cruzroja.convocatorias.administration.web;

import java.util.Map;
import org.springframework.web.bind.annotation.*;
import pe.cruzroja.convocatorias.administration.application.AdministrationUseCases;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;

@RestController
@RequestMapping("/api/v1/administration")
public class AdministrationController {
    private final AdministrationUseCases useCases;

    public AdministrationController(AdministrationUseCases useCases) {
        this.useCases = useCases;
    }

    @GetMapping
    public Map<String, Object> settings() {
        return useCases.settings();
    }

    @PutMapping
    public void update(@RequestBody HourTargets targets) {
        useCases.update(targets);
    }
}
