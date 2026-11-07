package pe.cruzroja.convocatorias.communications.domain;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.util.Set;

public record Announcement(String title, String body, String audience) {
    public Announcement {
        required(title, 150, "Título");
        required(body, 5000, "Mensaje");
        valid(Set.of("TODOS", "VOLUNTARIO", "JEFATURA").contains(audience), "Audiencia no válida.");
    }
}
