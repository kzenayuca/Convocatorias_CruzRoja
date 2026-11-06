package pe.cruzroja.convocatorias.shared.application;

import java.util.UUID;

/** Identidad de la petición, no un principal de Spring Security. */
public record Actor(UUID id,String role,boolean graduated) {
    public boolean chief() { return "JEFATURA".equals(role); }
}
