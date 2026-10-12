package org.cruzroja.usuarios.aplicacion.interfaces;

import org.cruzroja.usuarios.aplicacion.dto.PostulacionDTO;
import org.cruzroja.usuarios.aplicacion.dto.RegistrarPostulacionComando;
import java.io.IOException;
import java.util.List;

public interface PostulacionService {
    List<PostulacionDTO> listar() throws IOException;
    PostulacionDTO registrar(RegistrarPostulacionComando comando) throws IOException;
}
