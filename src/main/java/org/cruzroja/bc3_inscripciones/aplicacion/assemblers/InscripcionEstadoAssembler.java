package org.cruzroja.bc3_inscripciones.aplicacion.assemblers;

import org.cruzroja.bc3_inscripciones.aplicacion.dto.InscripcionDTO;
import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;

/**
 * SRP — Responsabilidad Única.
 *
 * Extrae el mapeo entidad -> DTO a una clase dedicada. El servicio de
 * aplicación deja de tener una segunda razón de cambio (dejar de conocer
 * cómo se arma el DTO).
 */
public final class InscripcionEstadoAssembler {

    public InscripcionDTO aDTO(Inscripcion inscripcion) {
        return InscripcionDTO.desde(inscripcion);
    }
}
