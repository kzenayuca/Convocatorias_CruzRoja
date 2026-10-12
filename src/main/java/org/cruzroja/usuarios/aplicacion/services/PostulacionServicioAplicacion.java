package org.cruzroja.usuarios.aplicacion.services;

import org.cruzroja.usuarios.aplicacion.dto.PostulacionDTO;
import org.cruzroja.usuarios.aplicacion.dto.RegistrarPostulacionComando;
import org.cruzroja.usuarios.aplicacion.interfaces.PostulacionService;
import org.cruzroja.usuarios.dominio.entities.Postulacion;
import org.cruzroja.usuarios.dominio.entities.Voluntario;
import org.cruzroja.usuarios.dominio.enums.TipoVoluntario;
import org.cruzroja.usuarios.dominio.repository.PostulacionRepository;
import org.cruzroja.usuarios.dominio.valueobjects.Perfil;
import org.cruzroja.bc2_convocatorias.dominio.repositories.IConvocatoriaRepositorio;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class PostulacionServicioAplicacion implements PostulacionService {
    private final PostulacionRepository repositorio;
    private final IConvocatoriaRepositorio convocatorias;

    public PostulacionServicioAplicacion(
            PostulacionRepository repositorio,
            IConvocatoriaRepositorio convocatorias) {
        this.repositorio = repositorio;
        this.convocatorias = convocatorias;
    }

    @Override
    public List<PostulacionDTO> listar() throws IOException {
        return repositorio.listarTodas().stream().map(PostulacionDTO::desde).toList();
    }

    @Override
    public PostulacionDTO registrar(RegistrarPostulacionComando comando) throws IOException {
        validarComando(comando);
        convocatorias.buscar(comando.codigoConvocatoria()).orElseThrow(() ->
                new IllegalArgumentException("La convocatoria seleccionada no existe"));
        if (repositorio.existe(
                comando.dni().trim(), comando.codigoConvocatoria().trim())) {
            throw new IllegalStateException(
                    "Este voluntario ya está postulando a esta convocatoria");
        }
        List<String> habilidades = Arrays.stream(comando.habilidades().split(","))
                .map(String::trim).filter(h -> !h.isBlank()).toList();
        Perfil perfil = new Perfil(
                TipoVoluntario.desdeTexto(comando.nivelFormacion()),
                comando.especialidad(), habilidades);
        Voluntario voluntario = new Voluntario(
                comando.dni(), comando.nombreCompleto(), perfil, comando.disponibilidad());
        Postulacion postulacion = new Postulacion(
                repositorio.siguienteId(), voluntario, comando.codigoConvocatoria());
        repositorio.guardar(postulacion);
        return PostulacionDTO.desde(postulacion);
    }

    private static void validarComando(RegistrarPostulacionComando comando) {
        if (comando == null) throw new IllegalArgumentException("La solicitud es obligatoria");
        if (comando.habilidades() == null) {
            throw new IllegalArgumentException("Las habilidades son obligatorias");
        }
    }
}
