package pe.cruzroja.convocatorias.membership.infrastructure.security;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

/** Traducción HTTP del protocolo de autenticación; no expone Spring Security al núcleo. */
@RestControllerAdvice
public class SecurityErrorHandler {
    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, String>> credentials() {
        return ResponseEntity.status(401)
                .body(Map.of("mensaje", "Credenciales incorrectas o cuenta no activa."));
    }
}
