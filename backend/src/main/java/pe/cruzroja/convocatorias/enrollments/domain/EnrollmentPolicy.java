package pe.cruzroja.convocatorias.enrollments.domain;

import pe.cruzroja.convocatorias.callouts.domain.CalloutStatus;

public class EnrollmentPolicy {
    public void validate(CalloutStatus status, boolean compatible, boolean hasCapacity,
                         boolean duplicate, boolean suspended) {
        if (status != CalloutStatus.PUBLICADA) throw new IllegalStateException("La convocatoria no está publicada");
        if (!compatible) throw new IllegalStateException("El nivel de formación no es compatible");
        if (!hasCapacity) throw new IllegalStateException("La convocatoria no tiene cupos disponibles");
        if (duplicate) throw new IllegalStateException("Ya existe una inscripción para esta convocatoria");
        if (suspended) throw new IllegalStateException("El voluntario tiene una suspensión vigente");
    }
}
