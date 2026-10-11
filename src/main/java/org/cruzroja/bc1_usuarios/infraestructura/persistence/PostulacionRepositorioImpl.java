package org.cruzroja.bc1_usuarios.infraestructura.persistence;

import org.cruzroja.bc1_usuarios.dominio.entities.Postulacion;
import org.cruzroja.bc1_usuarios.dominio.entities.Voluntario;
import org.cruzroja.bc1_usuarios.dominio.enums.EstadoPostulacion;
import org.cruzroja.bc1_usuarios.dominio.enums.TipoVoluntario;
import org.cruzroja.bc1_usuarios.dominio.repositories.IPostulacionRepositorio;
import org.cruzroja.bc1_usuarios.dominio.valueobjects.Perfil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Repository;

@Repository
public final class PostulacionRepositorioImpl implements IPostulacionRepositorio {
    private static final String CABECERA =
            "id\tdni\tnombre\tnivelFormacion\tespecialidad\thabilidades"
                    + "\tdisponibilidad\tcodigoConvocatoria\tfechaRegistro\testado";
    private final Path archivo = Path.of("src", "main", "resources", "database", "postulaciones.tsv");
    private final ReentrantReadWriteLock bloqueo = new ReentrantReadWriteLock();

    public PostulacionRepositorioImpl() throws IOException {
        Files.createDirectories(archivo.getParent());
        if (!Files.exists(archivo) || Files.size(archivo) == 0) {
            Files.writeString(archivo, CABECERA + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        }
    }

    @Override
    public List<Postulacion> listarTodas() throws IOException {
        bloqueo.readLock().lock();
        try {
            List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
            List<Postulacion> resultado = new ArrayList<>();
            for (int i = 1; i < lineas.size(); i++) {
                if (!lineas.get(i).isBlank()) resultado.add(desdeFila(lineas.get(i)));
            }
            return resultado;
        } finally {
            bloqueo.readLock().unlock();
        }
    }

    @Override
    public boolean existe(String dni, String codigoConvocatoria) throws IOException {
        return listarTodas().stream().anyMatch(p ->
                p.getVoluntario().dni().equals(dni)
                        && p.getCodigoConvocatoria().equals(codigoConvocatoria));
    }

    @Override
    public long siguienteId() throws IOException {
        return listarTodas().stream().mapToLong(Postulacion::getId).max().orElse(0) + 1;
    }

    @Override
    public void guardar(Postulacion postulacion) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
            lineas.add(aFila(postulacion));
            Files.write(archivo, lineas, StandardCharsets.UTF_8);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    private static String aFila(Postulacion p) {
        Voluntario v = p.getVoluntario();
        return String.join("\t",
                Long.toString(p.getId()), limpiar(v.dni()), limpiar(v.nombreCompleto()),
                v.perfil().nivelFormacion().name(), limpiar(v.perfil().especialidad()),
                limpiar(String.join(",", v.perfil().habilidades())),
                limpiar(v.disponibilidad()), limpiar(p.getCodigoConvocatoria()),
                p.getFechaRegistro().toString(), p.getEstado().name());
    }

    private static Postulacion desdeFila(String fila) {
        String[] c = fila.split("\t", -1);
        if (c.length != 10) throw new IllegalStateException("Fila de postulación inválida");
        List<String> habilidades = c[5].isBlank() ? List.of()
                : Arrays.stream(c[5].split(",")).map(String::trim).toList();
        Perfil perfil = new Perfil(TipoVoluntario.valueOf(c[3]), c[4], habilidades);
        Voluntario voluntario = new Voluntario(c[1], c[2], perfil, c[6]);
        return new Postulacion(Long.parseLong(c[0]), voluntario, c[7],
                LocalDateTime.parse(c[8]), EstadoPostulacion.valueOf(c[9]));
    }

    private static String limpiar(String valor) {
        return valor.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
