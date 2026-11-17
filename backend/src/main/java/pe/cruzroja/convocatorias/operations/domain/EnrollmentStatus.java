package pe.cruzroja.convocatorias.operations.domain;

public enum EnrollmentStatus {
    PENDIENTE,
    CONFIRMADA,
    PENDIENTE_REEMPLAZO,
    RECHAZADA,
    CANCELADA,
    EXPIRADA;

    public boolean allowsNewRequest() {
        return this == PENDIENTE || this == EXPIRADA;
    }
}
