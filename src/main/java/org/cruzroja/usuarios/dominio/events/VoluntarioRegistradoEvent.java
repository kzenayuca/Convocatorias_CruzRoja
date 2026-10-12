package org.cruzroja.usuarios.dominio.events;

import org.cruzroja.shared.domain.DomainEvent;
import java.time.LocalDateTime;

public record VoluntarioRegistradoEvent(
        String dni,
        String nombreCompleto,
        LocalDateTime ocurridoEn) implements DomainEvent {

    public VoluntarioRegistradoEvent(String dni, String nombreCompleto) {
        this(dni, nombreCompleto, LocalDateTime.now());
    }
}
