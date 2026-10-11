package org.cruzroja.bc1_usuarios.aplicacion.interfaces;

import org.cruzroja.bc1_usuarios.aplicacion.dto.ActualizarPerfilComando;
import org.cruzroja.bc1_usuarios.aplicacion.dto.VoluntarioDTO;
import java.io.IOException;
import java.util.List;

public interface IVoluntarioServicio {
    VoluntarioDTO obtener(String usuario) throws IOException;
    VoluntarioDTO actualizar(String usuario, ActualizarPerfilComando comando)
            throws IOException;
    List<VoluntarioDTO> filtrarPorRequisitos(List<String> requisitos) throws IOException;
}
