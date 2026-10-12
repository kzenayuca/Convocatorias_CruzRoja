package org.cruzroja.usuarios.presentacion.requests;

import org.cruzroja.usuarios.aplicacion.dto.ActualizarPerfilComando;
import java.util.List;

public record PerfilRequest(
        String nombre,
        String nivelFormacion,
        String especialidad,
        List<String> habilidades) {

    public ActualizarPerfilComando aComando() {
        return new ActualizarPerfilComando(nombre, nivelFormacion, especialidad, habilidades);
    }
}
