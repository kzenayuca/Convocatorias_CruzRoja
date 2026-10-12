package org.cruzroja.usuarios.dominio.entities;

import org.cruzroja.usuarios.dominio.valueobjects.Perfil;
import org.cruzroja.shared.domain.AggregateRoot;

public record Voluntario(
        String dni,
        String nombreCompleto,
        Perfil perfil,
        String disponibilidad) implements AggregateRoot {

    public Voluntario {
        dni = validar(dni, "DNI");
        nombreCompleto = validar(nombreCompleto, "nombre");
        if (perfil == null) throw new IllegalArgumentException("El perfil es obligatorio");
        disponibilidad = validar(disponibilidad, "disponibilidad");
    }

    private static String validar(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El " + campo + " es obligatorio");
        }
        return valor.trim();
    }
}
