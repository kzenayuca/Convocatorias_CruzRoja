package pe.cruzroja.convocatorias.membership.application;

public interface PasswordHasher {
    String encode(String password);
}
