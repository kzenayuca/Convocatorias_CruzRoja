package org.cruzroja.bc2_convocatorias.dominio.repositories;

import org.cruzroja.bc2_convocatorias.dominio.entities.EquipoAsignado;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface IEquipoAsignadoRepositorio {
    List<EquipoAsignado> listar() throws IOException;
    Optional<EquipoAsignado> buscar(long id) throws IOException;
    void agregar(EquipoAsignado integrante) throws IOException;
    void eliminar(long id) throws IOException;
}
