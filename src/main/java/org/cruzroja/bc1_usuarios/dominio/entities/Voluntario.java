package org.cruzroja.bc1_usuarios.dominio.entities;

import org.cruzroja.bc1_usuarios.dominio.valueobjects.Perfil;

public record Voluntario(
        String dni,
        String nombreCompleto,
        Perfil perfil,
        String disponibilidad) {

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
