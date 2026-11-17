package pe.cruzroja.convocatorias.membership.infrastructure.security;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import pe.cruzroja.convocatorias.membership.application.PasswordHasher;

@Component
public class BCryptPasswordHasher implements PasswordHasher {
    private final PasswordEncoder encoder;

    public BCryptPasswordHasher(PasswordEncoder encoder) {
        this.encoder = encoder;
    }

    public String encode(String password) {
        return encoder.encode(password);
    }
}
