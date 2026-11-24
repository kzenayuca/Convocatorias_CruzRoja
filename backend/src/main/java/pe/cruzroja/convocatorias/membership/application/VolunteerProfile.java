package pe.cruzroja.convocatorias.membership.application;

import java.util.UUID;

public record VolunteerProfile(
        UUID id,
        String name,
        String surnames,
        String email,
        String role,
        boolean graduated,
        String status,
        String occupation,
        String reviewComment,
        String document,
        String phone,
        String availability,
        Boolean radioQualified,
        Boolean pilotQualified) {}
