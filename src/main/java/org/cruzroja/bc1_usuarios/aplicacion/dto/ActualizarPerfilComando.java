package org.cruzroja.bc1_usuarios.aplicacion.dto;

import java.util.List;

/** Datos independientes de HTTP necesarios para actualizar un perfil. */
public record ActualizarPerfilComando(
        String nombre,
        String nivelFormacion,
        String especialidad,
        List<String> habilidades) {
}
