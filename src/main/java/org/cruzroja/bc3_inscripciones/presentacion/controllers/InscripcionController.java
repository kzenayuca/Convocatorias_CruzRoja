package org.cruzroja.bc3_inscripciones.presentacion.controllers;

import org.cruzroja.bc3_inscripciones.aplicacion.dto.InscripcionDTO;
import org.cruzroja.bc3_inscripciones.aplicacion.interfaces.IInscripcionServicio;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.cruzroja.bc3_inscripciones.presentacion.requests.InscripcionRequest;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;

/** API REST de la gestión de inscripciones. */
@RestController
@RequestMapping("/api/inscripciones")
public final class InscripcionController {
    private final IInscripcionServicio servicio;

    public InscripcionController(IInscripcionServicio servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<InscripcionDTO> listar() throws IOException {
        return servicio.listarInscripciones();
    }

    @PostMapping
    public ResponseEntity<InscripcionDTO> registrar(
            @RequestBody InscripcionRequest request) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                servicio.registrar(request.aComando()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable long id) throws IOException {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/estado")
    public InscripcionDTO actualizarEstado(
            @PathVariable long id,
            @RequestBody EstadoRequest request) throws IOException {
        return servicio.actualizarEstado(id, request.estado());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noEncontrada(NoSuchElementException exception) {
        return error(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> solicitudInvalida(IllegalArgumentException exception) {
        return error(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity<Map<String, String>> errorPersistencia(IOException exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR,
                "No se pudo acceder a la tabla de inscripciones: " + exception.getMessage());
    }

    private static ResponseEntity<Map<String, String>> error(
            HttpStatus estado, String mensaje) {
        return ResponseEntity.status(estado).body(Map.of("error", mensaje));
    }

    public record EstadoRequest(String estado) {
    }
}
