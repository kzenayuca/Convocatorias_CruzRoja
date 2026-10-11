package org.cruzroja.bc3_inscripciones.dominio.specifications;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;

/**
 * LSP — Sustitución de Liskov.
 *
 * Implementación que acepta cualquier inscripción. Puede sustituir a
 * cualquier otro CriterioInscripcion sin romper el contrato: el servicio
 * de consulta funciona igual reciba este criterio o uno más específico.
 */
public final class CriterioTodas implements CriterioInscripcion {

    @Override
    public boolean cumple(Inscripcion inscripcion) {
        return true;
    }
}
