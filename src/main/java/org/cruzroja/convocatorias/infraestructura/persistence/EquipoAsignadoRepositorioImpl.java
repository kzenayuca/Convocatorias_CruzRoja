package org.cruzroja.convocatorias.infraestructura.persistence;

import org.cruzroja.convocatorias.dominio.entities.EquipoAsignado;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.cruzroja.convocatorias.dominio.repositories.EquipoAsignadoRepository;

@Repository
public final class EquipoAsignadoRepositorioImpl implements EquipoAsignadoRepository {
    private final Path archivo = Path.of("src", "main", "resources", "database", "equipos.tsv");
    private final ReentrantReadWriteLock bloqueo = new ReentrantReadWriteLock();

    public EquipoAsignadoRepositorioImpl() throws IOException {
        if (!Files.exists(archivo) || Files.size(archivo) == 0) {
            guardarTodos(datosIniciales());
        }
    }

    @Override
    public List<EquipoAsignado> listar() throws IOException {
        bloqueo.readLock().lock();
        try {
            List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
            List<EquipoAsignado> resultado = new ArrayList<>();
            for (int i = 1; i < lineas.size(); i++) {
                if (!lineas.get(i).isBlank()) resultado.add(desdeFila(lineas.get(i)));
            }
            return resultado;
        } finally {
            bloqueo.readLock().unlock();
        }
    }

    @Override
    public Optional<EquipoAsignado> buscar(long id) throws IOException {
        return listar().stream().filter(i -> i.getId() == id).findFirst();
    }

    @Override
    public void agregar(EquipoAsignado integrante) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<EquipoAsignado> todos = leerSinBloqueo();
            todos.add(integrante);
            guardarTodos(todos);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    @Override
    public void eliminar(long id) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<EquipoAsignado> todos = leerSinBloqueo();
            if (!todos.removeIf(i -> i.getId() == id)) {
                throw new IllegalArgumentException("No existe el integrante " + id);
            }
            guardarTodos(todos);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    private List<EquipoAsignado> leerSinBloqueo() throws IOException {
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        List<EquipoAsignado> resultado = new ArrayList<>();
        for (int i = 1; i < lineas.size(); i++) {
            if (!lineas.get(i).isBlank()) resultado.add(desdeFila(lineas.get(i)));
        }
        return resultado;
    }

    private void guardarTodos(List<EquipoAsignado> integrantes) throws IOException {
        Files.createDirectories(archivo.getParent());
        List<String> lineas = new ArrayList<>();
        lineas.add("id\tconvocatoria\tnombre\tperfil\thabilidad\testado\tmentorId");
        for (EquipoAsignado i : integrantes) {
            lineas.add(String.join("\t", Long.toString(i.getId()), limpiar(i.getConvocatoriaCodigo()),
                    limpiar(i.getNombreVoluntario()), i.getPerfil().name(),
                    limpiar(i.getHabilidadEspecialidad()), i.getEstado().name(),
                    i.getMentorId() == null ? "" : i.getMentorId().toString()));
        }
        Files.write(archivo, lineas, StandardCharsets.UTF_8);
    }

    private EquipoAsignado desdeFila(String fila) {
        String[] c = fila.split("\t", -1);
        return new EquipoAsignado(Long.parseLong(c[0]), c[1], c[2],
                EquipoAsignado.Perfil.valueOf(c[3]), c[4],
                EquipoAsignado.EstadoParticipacion.valueOf(c[5]),
                c[6].isBlank() ? null : Long.parseLong(c[6]));
    }

    private static String limpiar(String valor) {
        return valor.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static List<EquipoAsignado> datosIniciales() {
        return List.of(
                new EquipoAsignado(1, "donacion", "Rosa Mendoza",
                        EquipoAsignado.Perfil.GRADUADO, "Enfermería",
                        EquipoAsignado.EstadoParticipacion.ACTIVO, null),
                new EquipoAsignado(2, "donacion", "Miguel Torres",
                        EquipoAsignado.Perfil.EN_FORMACION, "Toma de signos vitales",
                        EquipoAsignado.EstadoParticipacion.ACTIVO, 1L),
                new EquipoAsignado(3, "medica", "Lucía Vargas",
                        EquipoAsignado.Perfil.GRADUADO, "Medicina general",
                        EquipoAsignado.EstadoParticipacion.ACTIVO, null),
                new EquipoAsignado(4, "medica", "José Paredes",
                        EquipoAsignado.Perfil.EN_FORMACION, "Primeros auxilios",
                        EquipoAsignado.EstadoParticipacion.PENDIENTE, 3L));
    }
}
