package org.cruzroja.bc3_inscripciones.dominio.specifications;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;
import org.cruzroja.bc3_inscripciones.dominio.enums.EstadoInscripcion;

/**
 * OCP — nuevo criterio agregado sin tocar el servicio de consulta.
 * Selecciona las inscripciones que están en un estado determinado.
 */
public final class CriterioPorEstado implements CriterioInscripcion {

    private final EstadoInscripcion estado;

    public CriterioPorEstado(EstadoInscripcion estado) {
        if (estado == null) {
            throw new IllegalArgumentException("El estado es obligatorio");
        }
        this.estado = estado;
    }

    @Override
    public boolean cumple(Inscripcion inscripcion) {
        return inscripcion.getEstado() == estado;
    }
}
