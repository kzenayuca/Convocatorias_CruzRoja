package org.cruzroja.bc3_inscripciones.dominio.specifications;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;

/**
 * OCP — Abierto/Cerrado (patrón Specification).
 *
 * Contrato para filtrar inscripciones. Se agregan nuevos filtros creando
 * nuevas clases que implementen esta interfaz, sin modificar el servicio
 * que las consume.
 */
@FunctionalInterface
public interface CriterioInscripcion {
    boolean cumple(Inscripcion inscripcion);
}
