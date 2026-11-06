package pe.cruzroja.convocatorias.shared.application;

import java.util.UUID;

/** Contrato de consulta entre contextos: no revela repositorios ni tablas de asistencia. */
public interface GraduationEligibility {
    Assessment assess(UUID volunteer);
    record Assessment(double balance,Double target) {
        public boolean eligible() { return target!=null && balance>=target; }
    }
}
