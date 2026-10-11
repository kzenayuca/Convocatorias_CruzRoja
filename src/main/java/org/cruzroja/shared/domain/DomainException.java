package org.cruzroja.shared.domain;

/** Excepción base para errores originados por reglas del dominio. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }

    public DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
