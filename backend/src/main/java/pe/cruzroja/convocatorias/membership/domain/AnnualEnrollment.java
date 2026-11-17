package pe.cruzroja.convocatorias.membership.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.time.LocalDate;

/** Cada ciclo comprende enero-diciembre, sin reiniciar horas históricas. */
public record AnnualEnrollment(int year) {
    public AnnualEnrollment {
        valid(year >= 2000 && year <= 9999, "Año de inscripción no válido.");
    }

    public LocalDate startsOn() {
        return LocalDate.of(year, 1, 1);
    }

    public LocalDate endsOn() {
        return LocalDate.of(year, 12, 31);
    }
}
