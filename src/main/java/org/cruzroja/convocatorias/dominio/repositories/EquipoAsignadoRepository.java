package org.cruzroja.convocatorias.dominio.repositories;

import org.cruzroja.convocatorias.dominio.entities.EquipoAsignado;
import java.io.IOException;
import java.util.List;
import java.util.Optional;

public interface EquipoAsignadoRepository {
    List<EquipoAsignado> listar() throws IOException;
    Optional<EquipoAsignado> buscar(long id) throws IOException;
    void agregar(EquipoAsignado integrante) throws IOException;
    void eliminar(long id) throws IOException;
}
