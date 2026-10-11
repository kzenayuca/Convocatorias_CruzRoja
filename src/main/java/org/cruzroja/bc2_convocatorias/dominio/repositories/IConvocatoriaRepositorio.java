package org.cruzroja.bc2_convocatorias.dominio.repositories;

import org.cruzroja.bc2_convocatorias.dominio.entities.Convocatoria;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

/** Puerto de persistencia requerido por los casos de uso de convocatorias. */
public interface IConvocatoriaRepositorio {
    List<Convocatoria> listar();
    Optional<Convocatoria> buscar(String codigo);
    void agregar(Convocatoria convocatoria) throws IOException;
    void eliminar(String codigo) throws IOException;
    void guardarCambios() throws IOException;
}
