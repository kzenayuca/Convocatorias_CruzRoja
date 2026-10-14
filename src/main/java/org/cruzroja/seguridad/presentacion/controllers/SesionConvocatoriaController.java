package org.cruzroja.seguridad.presentacion.controllers;

import org.cruzroja.seguridad.aplicacion.dto.SesionDTO;
import org.cruzroja.seguridad.aplicacion.services.AutorizacionConvocatoriaServicio;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sesion")
public final class SesionConvocatoriaController {
    private final AutorizacionConvocatoriaServicio autorizacion;

    public SesionConvocatoriaController(AutorizacionConvocatoriaServicio autorizacion) {
        this.autorizacion = autorizacion;
    }

    @PostMapping
    public SesionDTO iniciar(@RequestBody LoginRequest request, HttpSession sesion) {
        return autorizacion.iniciar(request.usuario(), request.clave(), sesion);
    }

    @GetMapping
    public SesionDTO actual(HttpSession sesion) {
        return autorizacion.obtenerSesion(sesion);
    }

    @DeleteMapping
    public ResponseEntity<Void> cerrar(HttpSession sesion) {
        autorizacion.cerrar(sesion);
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(String usuario, String clave) {}
}
