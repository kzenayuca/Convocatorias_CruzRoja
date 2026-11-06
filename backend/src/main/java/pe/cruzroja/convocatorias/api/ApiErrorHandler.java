package pe.cruzroja.convocatorias.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            java.time.DateTimeException.class})
    ResponseEntity<Map<String,String>> format(Exception error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje","Revise las fechas y los datos enviados."));
    }
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String,String>> upload() {
        return ResponseEntity.status(413).body(Map.of("mensaje","La foto supera el límite de 10 MB."));
    }
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    ResponseEntity<Map<String, String>> status(org.springframework.web.server.ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(Map.of("mensaje", error.getReason() == null ? "Operación no permitida." : error.getReason()));
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    ResponseEntity<Map<String, String>> duplicate() {
        return ResponseEntity.status(409).body(Map.of("mensaje", "Los datos ya están registrados o no son válidos. Revise código del servicio, correo y DNI."));
    }
    @ExceptionHandler(javax.imageio.IIOException.class)
    ResponseEntity<Map<String,String>> corruptPhoto() {
        return ResponseEntity.badRequest().body(Map.of("mensaje","La foto está dañada o no es una imagen JPG/PNG válida."));
    }

    @ExceptionHandler(org.springframework.security.core.AuthenticationException.class)
    ResponseEntity<Map<String, String>> credentials() {
        return ResponseEntity.status(401).body(Map.of("mensaje", "Credenciales incorrectas o cuenta no activa."));
    }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Map<String, String>> invalid(IllegalArgumentException error) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Map<String, String>> conflict(IllegalStateException error) {
        return ResponseEntity.status(409).body(Map.of("mensaje", error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, String>> validation() {
        return ResponseEntity.badRequest().body(Map.of("mensaje", "Revise los campos del formulario."));
    }
}
