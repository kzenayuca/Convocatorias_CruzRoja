package org.cruzroja.bc1_usuarios.aplicacion.services;

import org.cruzroja.bc1_usuarios.aplicacion.dto.VoluntarioDTO;
import org.cruzroja.bc1_usuarios.aplicacion.dto.ActualizarPerfilComando;
import org.cruzroja.bc1_usuarios.aplicacion.interfaces.IPerfilRepositorio;
import org.cruzroja.bc1_usuarios.aplicacion.interfaces.IVoluntarioServicio;
import java.io.IOException;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public final class VoluntarioServicioAplicacion implements IVoluntarioServicio {
    private final IPerfilRepositorio repositorio;

    public VoluntarioServicioAplicacion(IPerfilRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public VoluntarioDTO obtener(String usuario) throws IOException {
        return repositorio.buscar(usuario).orElseGet(() ->
                new VoluntarioDTO(usuario, usuario, "EN_FORMACION",
                        "Por definir", List.of()));
    }

    @Override
    public VoluntarioDTO actualizar(
            String usuario, ActualizarPerfilComando comando) throws IOException {
        if (comando == null || comando.nombre() == null || comando.nombre().isBlank()
                || comando.nivelFormacion() == null || comando.nivelFormacion().isBlank()
                || comando.especialidad() == null || comando.especialidad().isBlank()
                || comando.habilidades() == null || comando.habilidades().isEmpty()) {
            throw new IllegalArgumentException("Complete todos los datos obligatorios del perfil");
        }
        VoluntarioDTO perfil = new VoluntarioDTO(
                usuario, comando.nombre().trim(), comando.nivelFormacion().trim().toUpperCase(),
                comando.especialidad().trim(), comando.habilidades().stream()
                        .map(String::trim).filter(h -> !h.isBlank()).toList());
        repositorio.guardar(perfil);
        return perfil;
    }

    @Override
    public List<VoluntarioDTO> filtrarPorRequisitos(List<String> requisitos)
            throws IOException {
        if (requisitos == null || requisitos.isEmpty()) return repositorio.listar();
        return repositorio.listar().stream().filter(p ->
                p.habilidades().stream().anyMatch(h -> requisitos.stream()
                        .anyMatch(r -> contiene(h, r)))
                || requisitos.stream().anyMatch(r -> contiene(p.especialidad(), r))).toList();
    }

    private static boolean contiene(String uno, String otro) {
        String a = uno.toLowerCase();
        String b = otro.toLowerCase();
        return a.contains(b) || b.contains(a);
    }
}
