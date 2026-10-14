package org.cruzroja.convocatorias.dominio.events;

import org.cruzroja.shared.domain.DomainEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Se emite cuando una nueva convocatoria queda publicada. */
public record ConvocatoriaPublicadaEvent(
        String codigoConvocatoria,
        String nombre,
        LocalDate fechaServicio,
        int minimoParticipantes,
        LocalDateTime ocurridoEn) implements DomainEvent {

    public ConvocatoriaPublicadaEvent(
            String codigoConvocatoria, String nombre,
            LocalDate fechaServicio, int minimoParticipantes) {
        this(codigoConvocatoria, nombre, fechaServicio, minimoParticipantes, LocalDateTime.now());
    }
}
