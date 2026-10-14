package org.cruzroja.convocatorias.aplicacion.services;

import org.cruzroja.convocatorias.aplicacion.dto.IntegranteEquipoDTO;
import org.cruzroja.convocatorias.aplicacion.dto.AgregarIntegranteComando;
import org.cruzroja.convocatorias.dominio.entities.EquipoAsignado;
import org.cruzroja.convocatorias.dominio.repositories.ConvocatoriaRepository;
import org.cruzroja.convocatorias.dominio.repositories.EquipoAsignadoRepository;
import org.cruzroja.convocatorias.dominio.events.EquipoAsignadoEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public final class EquipoAsignadoServicioAplicacion {
    private final EquipoAsignadoRepository repositorio;
    private final ConvocatoriaRepository convocatorias;
    private final ApplicationEventPublisher eventos;

    public EquipoAsignadoServicioAplicacion(
            EquipoAsignadoRepository repositorio,
            ConvocatoriaRepository convocatorias,
            ApplicationEventPublisher eventos) {
        this.repositorio = repositorio;
        this.convocatorias = convocatorias;
        this.eventos = eventos;
    }

    public List<IntegranteEquipoDTO> listarPorConvocatoria(String codigo) throws IOException {
        validarConvocatoria(codigo);
        List<EquipoAsignado> todos = repositorio.listar();
        return todos.stream()
                .filter(i -> i.getConvocatoriaCodigo().equalsIgnoreCase(codigo))
                .map(i -> convertir(i, todos))
                .toList();
    }

    public IntegranteEquipoDTO agregar(
            String codigo, AgregarIntegranteComando comando) throws IOException {
        validarConvocatoria(codigo);
        EquipoAsignado.Perfil perfil = convertirPerfil(comando.perfil());
        EquipoAsignado.EstadoParticipacion estado =
                convertirEstado(comando.estadoParticipacion());
        List<EquipoAsignado> todos = repositorio.listar();
        validarMentor(codigo, perfil, comando.mentorId(), todos);
        long id = todos.stream().mapToLong(EquipoAsignado::getId).max().orElse(0) + 1;
        EquipoAsignado integrante = new EquipoAsignado(
                id, codigo, comando.nombreVoluntario(), perfil,
                comando.habilidadEspecialidad(), estado, comando.mentorId());
        repositorio.agregar(integrante);
        eventos.publishEvent(new EquipoAsignadoEvent(
                integrante.getId(), integrante.getConvocatoriaCodigo(),
                integrante.getNombreVoluntario(), integrante.getPerfil().name(),
                integrante.getMentorId()));
        todos = repositorio.listar();
        return convertir(integrante, todos);
    }

    public void retirar(String codigo, long id) throws IOException {
        validarConvocatoria(codigo);
        List<EquipoAsignado> todos = repositorio.listar();
        EquipoAsignado integrante = todos.stream()
                .filter(i -> i.getId() == id && i.getConvocatoriaCodigo().equals(codigo))
                .findFirst().orElseThrow(() -> new NoSuchElementException("Integrante no encontrado"));
        boolean tieneOrientandos = todos.stream().anyMatch(i -> java.util.Objects.equals(i.getMentorId(), id));
        if (integrante.getPerfil() == EquipoAsignado.Perfil.GRADUADO && tieneOrientandos) {
            throw new IllegalStateException(
                    "No se puede retirar al graduado mientras tenga voluntarios por orientar");
        }
        repositorio.eliminar(id);
    }

    public List<IntegranteEquipoDTO> listarOrientandos(
            String codigo, long mentorId) throws IOException {
        List<EquipoAsignado> todos = repositorio.listar();
        EquipoAsignado mentor = todos.stream()
                .filter(i -> i.getId() == mentorId)
                .filter(i -> i.getConvocatoriaCodigo().equals(codigo))
                .filter(i -> i.getPerfil() == EquipoAsignado.Perfil.GRADUADO)
                .findFirst().orElseThrow(
                        () -> new IllegalArgumentException("El mentor debe ser un voluntario graduado"));
        return todos.stream()
                .filter(i -> java.util.Objects.equals(i.getMentorId(), mentor.getId()))
                .map(i -> convertir(i, todos))
                .toList();
    }

    private void validarConvocatoria(String codigo) {
        if (convocatorias.buscar(codigo).isEmpty()) {
            throw new NoSuchElementException("No existe la convocatoria " + codigo);
        }
    }

    private static void validarMentor(
            String codigo, EquipoAsignado.Perfil perfil, Long mentorId,
            List<EquipoAsignado> todos) {
        if (perfil == EquipoAsignado.Perfil.GRADUADO) return;
        if (mentorId == null) return;
        boolean valido = todos.stream()
                .anyMatch(i -> i.getId() == mentorId
                        && i.getConvocatoriaCodigo().equals(codigo)
                        && i.getPerfil() == EquipoAsignado.Perfil.GRADUADO
                        && i.getEstado() == EquipoAsignado.EstadoParticipacion.ACTIVO);
        if (!valido) {
            throw new IllegalArgumentException(
                    "El mentor debe ser un graduado activo de la misma convocatoria");
        }
    }

    private static EquipoAsignado.Perfil convertirPerfil(String perfil) {
        try {
            return EquipoAsignado.Perfil.valueOf(perfil.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Use GRADUADO o EN_FORMACION");
        }
    }

    private static EquipoAsignado.EstadoParticipacion convertirEstado(String estado) {
        try {
            return EquipoAsignado.EstadoParticipacion.valueOf(estado.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Use ACTIVO, PENDIENTE o RETIRADO");
        }
    }

    private static IntegranteEquipoDTO convertir(
            EquipoAsignado integrante, List<EquipoAsignado> todos) {
        String mentor = integrante.getMentorId() == null ? null : todos.stream()
                .filter(i -> i.getId() == integrante.getMentorId())
                .map(EquipoAsignado::getNombreVoluntario).findFirst().orElse("No disponible");
        return new IntegranteEquipoDTO(
                integrante.getId(), integrante.getConvocatoriaCodigo(),
                integrante.getNombreVoluntario(), integrante.getPerfil().name(),
                integrante.getHabilidadEspecialidad(), integrante.getEstado().name(),
                integrante.getMentorId(), mentor);
    }
}
