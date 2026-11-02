package pe.cruzroja.convocatorias.justifications.domain;

import java.time.LocalDate;
import java.util.UUID;

public class Penalty {
    private final UUID volunteerId;
    private final LocalDate startsAt;
    private final LocalDate endsAt;
    private PenaltyStatus status = PenaltyStatus.ACTIVA;

    public Penalty(UUID volunteerId, LocalDate startsAt, LocalDate endsAt) {
        if (volunteerId == null || startsAt == null || endsAt == null || !endsAt.isAfter(startsAt)) {
            throw new IllegalArgumentException("Penalización inválida");
        }
        this.volunteerId = volunteerId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
    }

    public boolean isActive(LocalDate today) {
        if (status == PenaltyStatus.ACTIVA && today.isAfter(endsAt)) status = PenaltyStatus.CUMPLIDA;
        return status == PenaltyStatus.ACTIVA && !today.isBefore(startsAt) && !today.isAfter(endsAt);
    }

    public PenaltyStatus status() { return status; }
}
