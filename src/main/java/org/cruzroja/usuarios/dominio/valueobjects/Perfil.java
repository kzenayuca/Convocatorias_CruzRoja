package org.cruzroja.usuarios.dominio.valueobjects;

import org.cruzroja.usuarios.dominio.enums.TipoVoluntario;
import java.util.List;

public record Perfil(
        TipoVoluntario nivelFormacion,
        String especialidad,
        List<String> habilidades) {

    public Perfil {
        if (nivelFormacion == null) {
            throw new IllegalArgumentException("El nivel de formación es obligatorio");
        }
        especialidad = validar(especialidad, "especialidad");
        habilidades = habilidades == null ? List.of() : List.copyOf(habilidades);
    }

    private static String validar(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("La " + campo + " es obligatoria");
        }
        return valor.trim();
    }
}
