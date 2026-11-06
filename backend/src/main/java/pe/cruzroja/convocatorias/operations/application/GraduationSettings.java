package pe.cruzroja.convocatorias.operations.application;

/** Puerto de configuración: el dominio no conoce JDBC ni Spring. */
public interface GraduationSettings {
    Double requiredHours(boolean graduated);
}
