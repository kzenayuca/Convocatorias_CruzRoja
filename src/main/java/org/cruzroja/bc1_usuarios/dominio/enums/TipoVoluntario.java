package org.cruzroja.bc1_usuarios.dominio.enums;

public enum TipoVoluntario {
    EN_FORMACION,
    GRADUADO;

    public static TipoVoluntario desdeTexto(String valor) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El nivel de formación es obligatorio");
        }
        return valueOf(valor.trim().toUpperCase().replace(' ', '_'));
    }
}
