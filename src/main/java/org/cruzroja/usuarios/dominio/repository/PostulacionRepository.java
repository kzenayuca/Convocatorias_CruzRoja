package org.cruzroja.usuarios.dominio.repository;

import org.cruzroja.usuarios.dominio.entities.Postulacion;
import java.io.IOException;
import java.util.List;

public interface PostulacionRepository {
    List<Postulacion> listarTodas() throws IOException;
    boolean existe(String dni, String codigoConvocatoria) throws IOException;
    long siguienteId() throws IOException;
    void guardar(Postulacion postulacion) throws IOException;
}
