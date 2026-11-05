package pe.cruzroja.convocatorias.api;

import static pe.cruzroja.convocatorias.api.Store.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/v1/administration")
public class AdministrationController {
    private final JdbcTemplate db;
    public AdministrationController(JdbcTemplate db) { this.db=db; }
    public record Settings(
        @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) java.math.BigDecimal trainingHours,
        @NotNull @DecimalMin("0.01") @DecimalMax("100000") @Digits(integer=6,fraction=2) java.math.BigDecimal graduateHours) { }
    @GetMapping public Map<String,Object> settings() {
        require(chief(),"Solo jefatura administra los requisitos institucionales.");
        return one(db,"""
            SELECT MAX(CASE WHEN condition='FORMACION' THEN hours END) AS training_hours,
                   MAX(CASE WHEN condition='GRADUADO' THEN hours END) AS graduate_hours,
                   MAX(updated_at) AS updated_at FROM volunteer_hour_targets
            """);
    }
    @PutMapping @Transactional public void update(@Valid @RequestBody Settings r) {
        require(chief(),"Solo jefatura configura las metas de horas.");
        db.update("UPDATE volunteer_hour_targets SET hours=?,updated_by=?,updated_at=? WHERE condition='FORMACION'",r.trainingHours(),actor().id(),now());
        db.update("UPDATE volunteer_hour_targets SET hours=?,updated_by=?,updated_at=? WHERE condition='GRADUADO'",r.graduateHours(),actor().id(),now());
    }
}
