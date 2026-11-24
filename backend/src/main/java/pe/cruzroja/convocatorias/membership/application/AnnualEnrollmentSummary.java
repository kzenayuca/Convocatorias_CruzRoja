package pe.cruzroja.convocatorias.membership.application;

import java.time.LocalDateTime;
import java.util.UUID;

public record AnnualEnrollmentSummary(
        int year, LocalDateTime registeredAt, UUID registeredBy, String source) {}
