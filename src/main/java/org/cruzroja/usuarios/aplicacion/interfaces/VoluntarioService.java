package org.cruzroja.usuarios.aplicacion.interfaces;

import org.cruzroja.usuarios.aplicacion.dto.ActualizarPerfilComando;
import org.cruzroja.usuarios.aplicacion.dto.VoluntarioDTO;
import java.io.IOException;
import java.util.List;

public interface VoluntarioService {
    VoluntarioDTO obtener(String usuario) throws IOException;
    VoluntarioDTO actualizar(String usuario, ActualizarPerfilComando comando) throws IOException;
    List<VoluntarioDTO> filtrarPorRequisitos(List<String> requisitos) throws IOException;
}
