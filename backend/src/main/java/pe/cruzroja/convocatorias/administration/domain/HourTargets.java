package pe.cruzroja.convocatorias.administration.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.math.BigDecimal;

/** Política institucional: dos metas generales para voluntarios, nunca para jefatura. */
public record HourTargets(BigDecimal trainingHours, BigDecimal graduateHours) {
    public HourTargets {
        check(trainingHours);
        check(graduateHours);
    }

    private static void check(BigDecimal value) {
        valid(
                value != null
                        && value.signum() > 0
                        && value.compareTo(new BigDecimal("100000")) <= 0
                        && value.scale() <= 2,
                "Meta de horas no válida (0.01 a 100000, hasta dos decimales).");
    }
}
