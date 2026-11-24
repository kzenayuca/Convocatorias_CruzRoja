package pe.cruzroja.convocatorias.membership.application;

import java.util.List;

public record AnnualEnrollmentDetail(
        boolean applicable, int currentYear, List<AnnualEnrollmentSummary> enrollments) {}
