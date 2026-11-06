package pe.cruzroja.convocatorias.membership.domain;

import java.time.LocalDate;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Cada ciclo comprende enero-diciembre, sin reiniciar horas históricas. */
public record AnnualEnrollment(int year) {
    public AnnualEnrollment { valid(year>=2000 && year<=9999,"Año de inscripción no válido."); }
    public LocalDate startsOn() { return LocalDate.of(year,1,1); }
    public LocalDate endsOn() { return LocalDate.of(year,12,31); }
}
