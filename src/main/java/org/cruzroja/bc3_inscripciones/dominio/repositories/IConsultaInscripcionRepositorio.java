package org.cruzroja.bc3_inscripciones.dominio.repositories;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;

import java.io.IOException;
import java.util.List;

/**
 * ISP — Segregación de Interfaces.
 *
 * Puerto de SOLO lectura para la funcionalidad de consulta (HF.3.2.1).
 * Un cliente que solo necesita consultar no depende de los métodos de
 * escritura (guardar, actualizar, eliminar) que no usa.
 */
public interface IConsultaInscripcionRepositorio {
    List<Inscripcion> listarTodas() throws IOException;
}
