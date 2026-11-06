package pe.cruzroja.convocatorias.shared.application;

public final class NotFound extends RuntimeException {
    public NotFound() { super("Registro no encontrado."); }
}
