package pe.cruzroja.convocatorias.shared.application;

public final class AccessDenied extends RuntimeException {
    public AccessDenied(String message) {
        super(message);
    }
}
