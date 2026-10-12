package org.cruzroja.usuarios.infraestructura.persistence;

import org.cruzroja.usuarios.aplicacion.dto.VoluntarioDTO;
import org.cruzroja.usuarios.dominio.repository.PerfilRepository;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public final class VoluntarioRepositorioImpl implements PerfilRepository {
    private final Path archivo = Path.of("src", "main", "resources", "database", "perfiles.tsv");

    public VoluntarioRepositorioImpl() throws IOException {
        Files.createDirectories(archivo.getParent());
        if (!Files.exists(archivo) || Files.size(archivo) == 0) {
            guardarTodos(datosIniciales());
        }
    }

    @Override
    public synchronized List<VoluntarioDTO> listar() throws IOException {
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        List<VoluntarioDTO> perfiles = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            if (!lineas.get(i).isBlank()) perfiles.add(desdeFila(lineas.get(i)));
        }
        return perfiles;
    }

    @Override
    public Optional<VoluntarioDTO> buscar(String usuario) throws IOException {
        return listar().stream().filter(p -> p.usuario().equals(usuario)).findFirst();
    }

    @Override
    public synchronized void guardar(VoluntarioDTO perfil) throws IOException {
        List<VoluntarioDTO> perfiles = listar();
        perfiles.removeIf(p -> p.usuario().equals(perfil.usuario()));
        perfiles.add(perfil);
        guardarTodos(perfiles);
    }

    private void guardarTodos(List<VoluntarioDTO> perfiles) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add("usuario\tnombre\tnivelFormacion\tespecialidad\thabilidades");
        for (VoluntarioDTO p : perfiles) {
            lineas.add(String.join("\t", limpiar(p.usuario()), limpiar(p.nombre()),
                    p.nivelFormacion(), limpiar(p.especialidad()),
                    limpiar(String.join("|", p.habilidades()))));
        }
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    private static VoluntarioDTO desdeFila(String fila) {
        String[] c = fila.split("\t", -1);
        return new VoluntarioDTO(c[0], c[1], c[2], c[3],
                c[4].isBlank() ? List.of() : Arrays.asList(c[4].split("\\|")));
    }

    private static String limpiar(String valor) {
        return valor.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static List<VoluntarioDTO> datosIniciales() {
        return List.of(
                new VoluntarioDTO("voluntario", "Ana Torres", "EN_FORMACION",
                        "Apoyo comunitario", List.of("Comunicación", "Trabajo en equipo")),
                new VoluntarioDTO("graduado", "Rosa Mendoza", "GRADUADO",
                        "Enfermería", List.of("RCP", "Primeros auxilios")),
                new VoluntarioDTO("reclutador", "Elena Salazar", "GRADUADO",
                        "Gestión de voluntariado", List.of("Selección", "Liderazgo")));
    }
}
