package org.cruzroja.convocatorias.dominio.entities;

import org.cruzroja.convocatorias.dominio.enums.EstadoConvocatoria;
import org.cruzroja.convocatorias.dominio.valueobjects.Horario;
import org.cruzroja.convocatorias.dominio.valueobjects.Ubicacion;
import org.cruzroja.shared.domain.AggregateRoot;
import java.time.LocalDate;

/** Convocatoria con la regla de cantidad mínima de participantes. */
public final class Convocatoria implements AggregateRoot {
    private final String codigo;
    private String nombre;
    private int minimoParticipantes;
    private boolean confirmada;
    private LocalDate fechaServicio;
    private Ubicacion ubicacion;
    private Horario horario;
    private java.util.List<String> requisitos;

    public Convocatoria(String codigo, String nombre, int minimoParticipantes) {
        this(codigo, nombre, minimoParticipantes, false);
    }

    public Convocatoria(
            String codigo, String nombre, int minimoParticipantes, boolean confirmada) {
        this(codigo, nombre, minimoParticipantes, confirmada, null,
                new Ubicacion("Lugar por definir"), null, java.util.List.of("Disponibilidad"));
    }

    public Convocatoria(
            String codigo, String nombre, int minimoParticipantes, boolean confirmada,
            LocalDate fechaServicio, Ubicacion ubicacion, Horario horario) {
        this(codigo, nombre, minimoParticipantes, confirmada, fechaServicio,
                ubicacion, horario, java.util.List.of("Disponibilidad"));
    }

    public Convocatoria(
            String codigo, String nombre, int minimoParticipantes, boolean confirmada,
            LocalDate fechaServicio, Ubicacion ubicacion, Horario horario,
            java.util.List<String> requisitos) {
        this.codigo = validarTexto(codigo, "código");
        this.nombre = validarTexto(nombre, "nombre");
        definirMinimo(minimoParticipantes);
        this.confirmada = confirmada;
        this.fechaServicio = fechaServicio;
        this.ubicacion = java.util.Objects.requireNonNull(ubicacion);
        this.horario = horario;
        this.requisitos = validarRequisitos(requisitos);
    }

    public void definirMinimo(int minimo) {
        if (minimo <= 0) {
            throw new IllegalArgumentException("El mínimo debe ser mayor que cero");
        }
        if (confirmada) {
            throw new IllegalStateException("No se puede cambiar el mínimo de una convocatoria confirmada");
        }
        this.minimoParticipantes = minimo;
    }

    public void confirmar(int inscritosActuales) {
        if (inscritosActuales < minimoParticipantes) {
            throw new IllegalStateException("No se puede confirmar: aún faltan participantes");
        }
        confirmada = true;
    }

    public EstadoConvocatoria estadoPara(int inscritosActuales) {
        if (confirmada) {
            return EstadoConvocatoria.CONFIRMADA;
        }
        return inscritosActuales >= minimoParticipantes
                ? EstadoConvocatoria.MINIMO_ALCANZADO
                : EstadoConvocatoria.MINIMO_NO_ALCANZADO;
    }

    public int faltantesPara(int inscritosActuales) {
        return Math.max(0, minimoParticipantes - inscritosActuales);
    }

    public void definirHorario(Horario nuevoHorario) {
        this.horario = java.util.Objects.requireNonNull(
                nuevoHorario, "El horario es obligatorio");
    }

    public void editar(
            String nuevoNombre, LocalDate nuevaFecha, Ubicacion nuevaUbicacion,
            Horario nuevoHorario, java.util.List<String> nuevosRequisitos, int nuevoMinimo) {
        // Cualquier cambio relevante requiere una nueva confirmación de jefatura.
        confirmada = false;
        nombre = validarTexto(nuevoNombre, "título");
        fechaServicio = java.util.Objects.requireNonNull(
                nuevaFecha, "La fecha es obligatoria");
        ubicacion = java.util.Objects.requireNonNull(
                nuevaUbicacion, "La ubicación es obligatoria");
        horario = java.util.Objects.requireNonNull(
                nuevoHorario, "El horario es obligatorio");
        requisitos = validarRequisitos(nuevosRequisitos);
        definirMinimo(nuevoMinimo);
    }

    private static java.util.List<String> validarRequisitos(java.util.List<String> valores) {
        if (valores == null) throw new IllegalArgumentException("Los requisitos son obligatorios");
        java.util.List<String> limpios = valores.stream()
                .filter(java.util.Objects::nonNull).map(String::trim)
                .filter(v -> !v.isBlank()).toList();
        if (limpios.isEmpty()) {
            throw new IllegalArgumentException("Debe registrar al menos un requisito");
        }
        return java.util.List.copyOf(limpios);
    }

    private static String validarTexto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El " + campo + " es obligatorio");
        }
        return valor.trim();
    }

    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public int getMinimoParticipantes() { return minimoParticipantes; }
    public boolean isConfirmada() { return confirmada; }
    public LocalDate getFechaServicio() { return fechaServicio; }
    public Ubicacion getUbicacion() { return ubicacion; }
    public Horario getHorario() { return horario; }
    public boolean tieneHorarioDefinido() { return horario != null; }
    public java.util.List<String> getRequisitos() { return requisitos; }
}
