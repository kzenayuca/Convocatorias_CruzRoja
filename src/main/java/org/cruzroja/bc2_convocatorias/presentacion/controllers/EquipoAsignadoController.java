package org.cruzroja.bc2_convocatorias.presentacion.controllers;

import org.cruzroja.bc2_convocatorias.aplicacion.dto.IntegranteEquipoDTO;
import org.cruzroja.bc2_convocatorias.aplicacion.services.EquipoAsignadoServicioAplicacion;
import org.cruzroja.bc2_convocatorias.presentacion.requests.IntegranteEquipoRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/convocatorias/{codigo}/equipo")
public final class EquipoAsignadoController {
    private final EquipoAsignadoServicioAplicacion servicio;

    public EquipoAsignadoController(EquipoAsignadoServicioAplicacion servicio) {
        this.servicio = servicio;
    }

    @GetMapping
    public List<IntegranteEquipoDTO> listar(@PathVariable String codigo) throws IOException {
        return servicio.listarPorConvocatoria(codigo);
    }

    @PostMapping
    public ResponseEntity<IntegranteEquipoDTO> agregar(
            @PathVariable String codigo,
            @RequestBody IntegranteEquipoRequest request) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED).body(
                servicio.agregar(codigo, request.aComando()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> retirar(
            @PathVariable String codigo, @PathVariable long id) throws IOException {
        servicio.retirar(codigo, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/mentores/{mentorId}/orientandos")
    public List<IntegranteEquipoDTO> orientandos(
            @PathVariable String codigo, @PathVariable long mentorId) throws IOException {
        return servicio.listarOrientandos(codigo, mentorId);
    }

    @ExceptionHandler(NoSuchElementException.class)
    ResponseEntity<Map<String, String>> noEncontrado(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    ResponseEntity<Map<String, String>> invalido(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }
}
