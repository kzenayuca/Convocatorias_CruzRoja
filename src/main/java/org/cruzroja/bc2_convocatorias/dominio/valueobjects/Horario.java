package org.cruzroja.bc2_convocatorias.dominio.valueobjects;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/** Value Object inmutable que valida y calcula el horario de un servicio. */
public final class Horario {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("HH:mm");
    private final LocalTime horaInicio;
    private final LocalTime horaFin;

    public Horario(LocalTime horaInicio, LocalTime horaFin) {
        this.horaInicio = Objects.requireNonNull(horaInicio, "La hora de inicio es obligatoria");
        this.horaFin = Objects.requireNonNull(horaFin, "La hora de finalización es obligatoria");
        if (!horaFin.isAfter(horaInicio)) {
            throw new IllegalArgumentException(
                    "La hora de finalización debe ser posterior a la hora de inicio");
        }
    }

    public static Horario desdeTexto(String inicio, String fin) {
        try {
            return new Horario(LocalTime.parse(inicio), LocalTime.parse(fin));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new IllegalArgumentException("Las horas deben usar el formato HH:mm", exception);
        }
    }

    public Duration duracion() { return Duration.between(horaInicio, horaFin); }

    public String duracionLegible() {
        long minutos = duracion().toMinutes();
        long horas = minutos / 60;
        long restantes = minutos % 60;
        return restantes == 0 ? horas + " h" : horas + " h " + restantes + " min";
    }

    public String getHoraInicioTexto() { return horaInicio.format(FORMATO); }
    public String getHoraFinTexto() { return horaFin.format(FORMATO); }
}
