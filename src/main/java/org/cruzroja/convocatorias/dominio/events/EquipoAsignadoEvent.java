package org.cruzroja.convocatorias.dominio.events;

import org.cruzroja.shared.domain.DomainEvent;
import java.time.LocalDateTime;

/** Se emite cuando un voluntario es asignado al equipo de una convocatoria. */
public record EquipoAsignadoEvent(
        long integranteId,
        String codigoConvocatoria,
        String nombreVoluntario,
        String perfil,
        Long mentorId,
        LocalDateTime ocurridoEn) implements DomainEvent {

    public EquipoAsignadoEvent(
            long integranteId, String codigoConvocatoria, String nombreVoluntario,
            String perfil, Long mentorId) {
        this(integranteId, codigoConvocatoria, nombreVoluntario, perfil, mentorId,
                LocalDateTime.now());
    }
}
