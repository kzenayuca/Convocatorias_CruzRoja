package org.cruzroja.bc2_convocatorias.aplicacion.services;

import org.cruzroja.bc2_convocatorias.aplicacion.dto.ConvocatoriaDTO;
import org.cruzroja.bc2_convocatorias.aplicacion.interfaces.IConvocatoriaServicio;
import org.cruzroja.bc2_convocatorias.dominio.entities.Convocatoria;
import org.cruzroja.bc2_convocatorias.dominio.repositories.IConvocatoriaRepositorio;
import org.cruzroja.bc3_inscripciones.dominio.enums.EstadoInscripcion;
import org.cruzroja.bc3_inscripciones.dominio.repositories.IInscripcionRepositorio;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import org.cruzroja.bc2_convocatorias.dominio.valueobjects.Horario;
import org.cruzroja.bc2_convocatorias.dominio.valueobjects.Ubicacion;
import org.cruzroja.bc2_convocatorias.aplicacion.dto.GuardarConvocatoriaComando;
import java.time.LocalDate;

@Service
public final class ConvocatoriaServicioAplicacion implements IConvocatoriaServicio {
    private final IConvocatoriaRepositorio repositorio;
    private final IInscripcionRepositorio inscripciones;

    public ConvocatoriaServicioAplicacion(
            IConvocatoriaRepositorio repositorio,
            IInscripcionRepositorio inscripciones) {
        this.repositorio = repositorio;
        this.inscripciones = inscripciones;
    }

    public List<ConvocatoriaDTO> listarVerificacionMinimos() throws IOException {
        List<ConvocatoriaDTO> resultado = new java.util.ArrayList<>();
        for (Convocatoria convocatoria : repositorio.listar()) {
            resultado.add(convertir(convocatoria));
        }
        return resultado;
    }

    public ConvocatoriaDTO definirMinimo(String codigo, int minimo) throws IOException {
        Convocatoria convocatoria = buscar(codigo);
        convocatoria.definirMinimo(minimo);
        repositorio.guardarCambios();
        return convertir(convocatoria);
    }

    public ConvocatoriaDTO obtener(String codigo) throws IOException {
        return convertir(buscar(codigo));
    }

    public ConvocatoriaDTO definirHorario(
            String codigo, String horaInicio, String horaFin) throws IOException {
        Convocatoria convocatoria = buscar(codigo);
        convocatoria.definirHorario(Horario.desdeTexto(horaInicio, horaFin));
        repositorio.guardarCambios();
        return convertir(convocatoria);
    }

    public ConvocatoriaDTO confirmar(String codigo) throws IOException {
        Convocatoria convocatoria = buscar(codigo);
        convocatoria.confirmar((int) contarInscritos(codigo));
        repositorio.guardarCambios();
        return convertir(convocatoria);
    }

    public ConvocatoriaDTO publicar(GuardarConvocatoriaComando comando) throws IOException {
        validar(comando, true);
        Convocatoria convocatoria = new Convocatoria(
                comando.codigo().trim().toLowerCase().replace(' ', '-'),
                comando.titulo(), comando.minimoParticipantes(), false,
                LocalDate.parse(comando.fecha()), new Ubicacion(comando.ubicacion()),
                Horario.desdeTexto(comando.horaInicio(), comando.horaFin()),
                comando.requisitos());
        repositorio.agregar(convocatoria);
        return convertir(convocatoria);
    }

    public ConvocatoriaDTO editar(
            String codigo, GuardarConvocatoriaComando comando) throws IOException {
        validar(comando, false);
        Convocatoria convocatoria = buscar(codigo);
        convocatoria.editar(
                comando.titulo(), LocalDate.parse(comando.fecha()),
                new Ubicacion(comando.ubicacion()),
                Horario.desdeTexto(comando.horaInicio(), comando.horaFin()),
                comando.requisitos(), comando.minimoParticipantes());
        repositorio.guardarCambios();
        return convertir(convocatoria);
    }

    public void eliminar(String codigo) throws IOException {
        Convocatoria convocatoria = buscar(codigo);
        if (convocatoria.isConfirmada()) {
            throw new IllegalStateException(
                    "No se puede eliminar una convocatoria confirmada");
        }
        if (contarInscritos(codigo) > 0) {
            throw new IllegalStateException(
                    "No se puede eliminar porque tiene voluntarios inscritos");
        }
        repositorio.eliminar(codigo);
    }

    public List<ConvocatoriaDTO> filtrarPorPerfil(String perfil) throws IOException {
        String filtro = perfil == null ? "" : perfil.trim().toLowerCase();
        return listarVerificacionMinimos().stream()
                .filter(c -> filtro.isBlank() || c.requisitos().stream()
                        .anyMatch(r -> r.toLowerCase().contains(filtro)))
                .toList();
    }

    private static void validar(GuardarConvocatoriaComando comando, boolean exigirCodigo) {
        if (comando == null) throw new IllegalArgumentException("Los datos son obligatorios");
        if (exigirCodigo && (comando.codigo() == null || comando.codigo().isBlank())) {
            throw new IllegalArgumentException("El código es obligatorio");
        }
        if (comando.titulo() == null || comando.titulo().isBlank()
                || comando.fecha() == null || comando.fecha().isBlank()
                || comando.ubicacion() == null || comando.ubicacion().isBlank()
                || comando.horaInicio() == null || comando.horaInicio().isBlank()
                || comando.horaFin() == null || comando.horaFin().isBlank()) {
            throw new IllegalArgumentException("Complete todos los campos obligatorios");
        }
    }

    private Convocatoria buscar(String codigo) {
        return repositorio.buscar(codigo).orElseThrow(
                () -> new NoSuchElementException("No existe la convocatoria " + codigo));
    }

    private long contarInscritos(String codigo) throws IOException {
        return inscripciones.listarTodas().stream()
                .filter(i -> i.getCodigoConvocatoria().equalsIgnoreCase(codigo))
                .filter(i -> i.getEstado() != EstadoInscripcion.CANCELADA)
                .filter(i -> i.getEstado() != EstadoInscripcion.RECHAZADA)
                .count();
    }

    private ConvocatoriaDTO convertir(Convocatoria convocatoria) throws IOException {
        long actuales = contarInscritos(convocatoria.getCodigo());
        int faltantes = convocatoria.faltantesPara((int) actuales);
        return new ConvocatoriaDTO(
                convocatoria.getCodigo(), convocatoria.getNombre(),
                convocatoria.getMinimoParticipantes(), actuales, faltantes == 0,
                faltantes, convocatoria.estadoPara((int) actuales).name(),
                faltantes == 0 ? "Mínimo de participantes alcanzado"
                        : "Aún faltan " + faltantes + " participante(s)",
                faltantes == 0 && !convocatoria.isConfirmada(),
                convocatoria.getFechaServicio() == null
                        ? "Fecha por definir" : convocatoria.getFechaServicio().toString(),
                convocatoria.getUbicacion().lugar(),
                convocatoria.tieneHorarioDefinido()
                        ? convocatoria.getHorario().getHoraInicioTexto() : null,
                convocatoria.tieneHorarioDefinido()
                        ? convocatoria.getHorario().getHoraFinTexto() : null,
                convocatoria.tieneHorarioDefinido()
                        ? convocatoria.getHorario().duracionLegible() : null,
                convocatoria.tieneHorarioDefinido(),
                convocatoria.tieneHorarioDefinido()
                        ? "Horario definido" : "El horario todavía no ha sido definido",
                convocatoria.getRequisitos());
    }
}
