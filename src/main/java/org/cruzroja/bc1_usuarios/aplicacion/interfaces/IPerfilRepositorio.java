package org.cruzroja.bc1_usuarios.aplicacion.interfaces;

import org.cruzroja.bc1_usuarios.aplicacion.dto.VoluntarioDTO;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de perfiles de voluntarios. */
public interface IPerfilRepositorio {
    List<VoluntarioDTO> listar() throws IOException;
    Optional<VoluntarioDTO> buscar(String usuario) throws IOException;
    void guardar(VoluntarioDTO perfil) throws IOException;
}
