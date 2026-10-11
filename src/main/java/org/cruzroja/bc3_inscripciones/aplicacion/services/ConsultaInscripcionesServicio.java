package org.cruzroja.bc3_inscripciones.aplicacion.services;

import org.cruzroja.bc3_inscripciones.aplicacion.assemblers.InscripcionEstadoAssembler;
import org.cruzroja.bc3_inscripciones.aplicacion.dto.InscripcionDTO;
import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;
import org.cruzroja.bc3_inscripciones.dominio.repositories.IConsultaInscripcionRepositorio;
import org.cruzroja.bc3_inscripciones.dominio.specifications.CriterioInscripcion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * DIP — Inversión de Dependencias.
 *
 * El servicio depende de abstracciones inyectadas por constructor
 * (el puerto de consulta y el ensamblador), no de implementaciones
 * concretas. Combina el criterio (OCP) y el ensamblador (SRP) para
 * devolver los DTO que consume la vista de estado de inscripción.
 */
public final class ConsultaInscripcionesServicio {

    private final IConsultaInscripcionRepositorio repositorio;
    private final InscripcionEstadoAssembler assembler;

    public ConsultaInscripcionesServicio(IConsultaInscripcionRepositorio repositorio,
                                         InscripcionEstadoAssembler assembler) {
        if (repositorio == null || assembler == null) {
            throw new IllegalArgumentException("Repositorio y assembler son obligatorios");
        }
        this.repositorio = repositorio;
        this.assembler = assembler;
    }

    public List<InscripcionDTO> consultar(CriterioInscripcion criterio) throws IOException {
        if (criterio == null) {
            throw new IllegalArgumentException("El criterio es obligatorio");
        }
        List<InscripcionDTO> resultado = new ArrayList<>();
        for (Inscripcion inscripcion : repositorio.listarTodas()) {
            if (criterio.cumple(inscripcion)) {
                resultado.add(assembler.aDTO(inscripcion));
            }
        }
        return resultado;
    }
}
