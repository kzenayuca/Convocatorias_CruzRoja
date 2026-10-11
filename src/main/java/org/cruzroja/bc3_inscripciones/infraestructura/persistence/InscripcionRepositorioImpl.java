package org.cruzroja.bc3_inscripciones.infraestructura.persistence;

import org.cruzroja.bc3_inscripciones.dominio.entities.Inscripcion;
import org.cruzroja.bc3_inscripciones.dominio.enums.EstadoInscripcion;
import org.cruzroja.bc3_inscripciones.dominio.repositories.IInscripcionRepositorio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.stereotype.Repository;

/**
 * Persistent-Tables: almacena las inscripciones en una tabla TSV persistente.
 * No necesita librerías externas ni una base de datos instalada.
 */
@Repository
public final class InscripcionRepositorioImpl implements IInscripcionRepositorio {
    private static final String CABECERA = String.join("\t",
            "id",
            "nombre",
            "dni",
            "correo",
            "telefono",
            "convocatoria",
            "codigoConvocatoria",
            "fechaInscripcion",
            "estado",
            "experiencia"
    );

    private final Path archivo;
    private final ReentrantReadWriteLock bloqueo = new ReentrantReadWriteLock();

    public InscripcionRepositorioImpl() throws IOException {
        this(Path.of("src", "main", "resources", "database", "inscripciones.tsv"));
    }

    public InscripcionRepositorioImpl(Path archivo) throws IOException {
        this.archivo = archivo.toAbsolutePath().normalize();
        inicializarTabla();
    }

    @Override
    public List<Inscripcion> listarTodas() throws IOException {
        bloqueo.readLock().lock();
        try {
            return leerTabla();
        } finally {
            bloqueo.readLock().unlock();
        }
    }

    @Override
    public Optional<Inscripcion> buscarPorId(long id) throws IOException {
        return listarTodas()
                .stream()
                .filter(inscripcion -> inscripcion.getId() == id)
                .findFirst();
    }

    @Override
    public void actualizar(Inscripcion inscripcion) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<Inscripcion> inscripciones = leerTabla();
            boolean encontrada = false;

            for (int indice = 0; indice < inscripciones.size(); indice++) {
                if (inscripciones.get(indice).getId() == inscripcion.getId()) {
                    inscripciones.set(indice, inscripcion);
                    encontrada = true;
                    break;
                }
            }

            if (!encontrada) {
                throw new IllegalArgumentException(
                        "No se puede actualizar una inscripción inexistente"
                );
            }

            escribirTabla(inscripciones);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    @Override
    public void guardar(Inscripcion inscripcion) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<Inscripcion> inscripciones = leerTabla();
            inscripciones.add(inscripcion);
            escribirTabla(inscripciones);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    @Override
    public void eliminar(long id) throws IOException {
        bloqueo.writeLock().lock();
        try {
            List<Inscripcion> inscripciones = leerTabla();
            if (!inscripciones.removeIf(i -> i.getId() == id)) {
                throw new IllegalArgumentException("No existe una inscripción con id " + id);
            }
            escribirTabla(inscripciones);
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    private void inicializarTabla() throws IOException {
        bloqueo.writeLock().lock();
        try {
            Path directorio = archivo.getParent();
            if (directorio != null) {
                Files.createDirectories(directorio);
            }

            if (!Files.exists(archivo) || Files.size(archivo) == 0) {
                escribirTabla(datosIniciales());
            }
        } finally {
            bloqueo.writeLock().unlock();
        }
    }

    private List<Inscripcion> leerTabla() throws IOException {
        List<String> lineas = Files.readAllLines(archivo, StandardCharsets.UTF_8);
        List<Inscripcion> resultado = new ArrayList<>();

        for (int indice = 1; indice < lineas.size(); indice++) {
            String linea = lineas.get(indice);
            if (!linea.isBlank()) {
                resultado.add(desdeFila(linea));
            }
        }

        return resultado;
    }

    private void escribirTabla(List<Inscripcion> inscripciones) throws IOException {
        List<String> lineas = new ArrayList<>();
        lineas.add(CABECERA);
        inscripciones.stream().map(this::aFila).forEach(lineas::add);

        Path temporal = archivo.resolveSibling(archivo.getFileName() + ".tmp");
        Files.write(temporal, lineas, StandardCharsets.UTF_8);
        try {
            Files.move(
                    temporal,
                    archivo,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(
                    temporal,
                    archivo,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private String aFila(Inscripcion inscripcion) {
        return String.join("\t",
                Long.toString(inscripcion.getId()),
                limpiar(inscripcion.getNombre()),
                limpiar(inscripcion.getDni()),
                limpiar(inscripcion.getCorreo()),
                limpiar(inscripcion.getTelefono()),
                limpiar(inscripcion.getConvocatoria()),
                limpiar(inscripcion.getCodigoConvocatoria()),
                inscripcion.getFechaInscripcion().toString(),
                inscripcion.getEstado().name(),
                limpiar(inscripcion.getExperiencia())
        );
    }

    private Inscripcion desdeFila(String linea) {
        String[] columnas = linea.split("\t", -1);
        if (columnas.length != 10) {
            throw new IllegalStateException("Fila inválida en la tabla de inscripciones");
        }

        return new Inscripcion(
                Long.parseLong(columnas[0]),
                columnas[1],
                columnas[2],
                columnas[3],
                columnas[4],
                columnas[5],
                columnas[6],
                LocalDate.parse(columnas[7]),
                EstadoInscripcion.desdeTexto(columnas[8]),
                columnas[9]
        );
    }

    private static String limpiar(String valor) {
        return valor.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static List<Inscripcion> datosIniciales() {
        return List.of(
                crear(1, "Ana Torres", "74851236", "ana.torres@gmail.com", "987 654 321",
                        "Campaña de Donación de Sangre", "donacion", "2026-07-18",
                        EstadoInscripcion.PENDIENTE, "Apoyo en campañas comunitarias"),
                crear(2, "Luis Medina", "70643128", "luis.medina@gmail.com", "965 438 112",
                        "Apoyo en Emergencias", "emergencias", "2026-07-17",
                        EstadoInscripcion.APROBADA, "Voluntario en primeros auxilios"),
                crear(3, "Mariela Quispe", "72104589", "mariela.quispe@gmail.com", "953 290 681",
                        "Capacitación en Primeros Auxilios", "auxilios", "2026-07-17",
                        EstadoInscripcion.PENDIENTE, "Estudiante de enfermería"),
                crear(4, "Carlos Huamán", "73421895", "carlos.huaman@gmail.com", "944 763 210",
                        "Campaña de Donación de Sangre", "donacion", "2026-07-16",
                        EstadoInscripcion.RECHAZADA, "Sin experiencia previa"),
                crear(5, "Elena Ramos", "71896542", "elena.ramos@gmail.com", "979 145 830",
                        "Campaña Médica Comunitaria", "medica", "2026-07-15",
                        EstadoInscripcion.APROBADA, "Técnica en farmacia"),
                crear(6, "Jorge Salazar", "76214509", "jorge.salazar@gmail.com", "932 870 145",
                        "Apoyo en Emergencias", "emergencias", "2026-07-15",
                        EstadoInscripcion.CANCELADA, "Brigadista universitario"),
                crear(7, "Paola Flores", "70125843", "paola.flores@gmail.com", "981 362 709",
                        "Campaña Médica Comunitaria", "medica", "2026-07-14",
                        EstadoInscripcion.PENDIENTE, "Estudiante de medicina"),
                crear(8, "Diego Valdivia", "75963214", "diego.valdivia@gmail.com", "946 205 873",
                        "Capacitación en Primeros Auxilios", "auxilios", "2026-07-13",
                        EstadoInscripcion.APROBADA, "Bombero voluntario")
        );
    }

    private static Inscripcion crear(
            long id,
            String nombre,
            String dni,
            String correo,
            String telefono,
            String convocatoria,
            String codigo,
            String fecha,
            EstadoInscripcion estado,
            String experiencia) {

        return new Inscripcion(
                id,
                nombre,
                dni,
                correo,
                telefono,
                convocatoria,
                codigo,
                LocalDate.parse(fecha),
                estado,
                experiencia
        );
    }
}
