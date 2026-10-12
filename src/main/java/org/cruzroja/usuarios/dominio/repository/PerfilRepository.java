package org.cruzroja.usuarios.dominio.repository;

import org.cruzroja.usuarios.aplicacion.dto.VoluntarioDTO;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia de perfiles de voluntarios. */
public interface PerfilRepository {
    List<VoluntarioDTO> listar() throws IOException;
    Optional<VoluntarioDTO> buscar(String usuario) throws IOException;
    void guardar(VoluntarioDTO perfil) throws IOException;
}
