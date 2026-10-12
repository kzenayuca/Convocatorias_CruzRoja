package org.cruzroja.usuarios.aplicacion.dto;

import java.util.List;

public record VoluntarioDTO(
        String usuario,
        String nombre,
        String nivelFormacion,
        String especialidad,
        List<String> habilidades) {
}
