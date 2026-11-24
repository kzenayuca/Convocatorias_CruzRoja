package pe.cruzroja.convocatorias.membership.application;

import java.time.LocalDateTime;
import java.util.UUID;

public record ProfileChangeRequest(
        UUID id,
        UUID userId,
        String name,
        String surnames,
        String email,
        String document,
        String occupation,
        LocalDateTime submittedAt,
        String status,
        String resolutionComment,
        UUID resolvedBy,
        LocalDateTime resolvedAt,
        String resolvedName) {}
