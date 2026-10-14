package org.cruzroja.convocatorias.presentacion.controllers;

import org.cruzroja.convocatorias.aplicacion.dto.ConvocatoriaDTO;
import org.cruzroja.convocatorias.aplicacion.interfaces.ConvocatoriaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.cruzroja.seguridad.aplicacion.services.AutorizacionConvocatoriaServicio;
import jakarta.servlet.http.HttpSession;
import org.cruzroja.convocatorias.presentacion.requests.ConvocatoriaRequest;

@RestController
@RequestMapping("/api/convocatorias")
public final class ConvocatoriaController {
    private final ConvocatoriaService servicio;
    private final AutorizacionConvocatoriaServicio autorizacion;

    public ConvocatoriaController(
            ConvocatoriaService servicio,
            AutorizacionConvocatoriaServicio autorizacion) {
        this.servicio = servicio;
        this.autorizacion = autorizacion;
    }

    @GetMapping("/minimos")
    public List<ConvocatoriaDTO> listar() throws IOException {
        return servicio.listarVerificacionMinimos();
    }

    @GetMapping("/disponibles")
    public List<ConvocatoriaDTO> disponibles(
            @RequestParam(defaultValue = "") String perfil) throws IOException {
        return servicio.filtrarPorPerfil(perfil);
    }

    @GetMapping
    public List<ConvocatoriaDTO> listarConvocatorias(HttpSession sesion) throws IOException {
        autorizacion.requerirAutenticado(sesion);
        return servicio.listarVerificacionMinimos();
    }

    @GetMapping("/{codigo}")
    public ConvocatoriaDTO obtener(
            @PathVariable String codigo, HttpSession sesion) throws IOException {
        autorizacion.requerirAutenticado(sesion);
        return servicio.obtener(codigo);
    }

    @PatchMapping("/{codigo}/minimo")
    public ConvocatoriaDTO definirMinimo(
            @PathVariable String codigo, @RequestBody MinimoRequest request,
            HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        return servicio.definirMinimo(codigo, request.minimoParticipantes());
    }

    @PatchMapping("/{codigo}/horario")
    public ConvocatoriaDTO definirHorario(
            @PathVariable String codigo, @RequestBody HorarioRequest request,
            HttpSession sesion) throws IOException {
        autorizacion.requerirReclutador(sesion);
        return servicio.definirHorario(codigo, request.horaInicio(), request.horaFin());
    }

    @PostMapping
    public ResponseEntity<ConvocatoriaDTO> publicar(
            @RequestBody ConvocatoriaRequest request, HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                servicio.publicar(request.aComando()));
    }

    @PutMapping("/{codigo}")
    public ConvocatoriaDTO editar(
            @PathVariable String codigo, @RequestBody ConvocatoriaRequest request,
            HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        return servicio.editar(codigo, request.aComando());
    }

    @DeleteMapping("/{codigo}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String codigo, HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        servicio.eliminar(codigo);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{codigo}/confirmacion")
    public ConvocatoriaDTO confirmar(
            @PathVariable String codigo, HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        return servicio.confirmar(codigo);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> reglaInvalida(RuntimeException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String, String>> noEncontrada(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", exception.getMessage()));
    }

    public record MinimoRequest(int minimoParticipantes) {}
    public record HorarioRequest(String horaInicio, String horaFin) {}
}
