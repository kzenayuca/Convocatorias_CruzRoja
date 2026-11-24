package pe.cruzroja.convocatorias.membership.application;

import java.time.LocalDateTime;
import java.util.UUID;

public record PendingProfileChange(
        UUID userId,
        String name,
        String surnames,
        String email,
        String document,
        String occupation,
        LocalDateTime submittedAt,
        UUID requestId) {}
