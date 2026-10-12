package org.cruzroja.usuarios.presentacion.controllers;

import org.cruzroja.usuarios.aplicacion.dto.PostulacionDTO;
import org.cruzroja.usuarios.aplicacion.interfaces.PostulacionService;
import org.cruzroja.usuarios.presentacion.requests.PostulacionRequest;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/postulaciones")
public final class PostulacionController {
    private final PostulacionService servicio;

    public PostulacionController(PostulacionService servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<PostulacionDTO> listar() throws IOException {
        return servicio.listar();
    }

    @PostMapping
    public ResponseEntity<PostulacionDTO> registrar(
            @RequestBody PostulacionRequest request) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                servicio.registrar(request.aComando()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> solicitudInvalida(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
