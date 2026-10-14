package org.cruzroja.usuarios.presentacion.controllers;

import org.cruzroja.usuarios.aplicacion.dto.VoluntarioDTO;
import org.cruzroja.usuarios.aplicacion.interfaces.VoluntarioService;
import org.cruzroja.usuarios.presentacion.requests.PerfilRequest;
import org.cruzroja.seguridad.aplicacion.services.AutorizacionConvocatoriaServicio;
import org.cruzroja.convocatorias.dominio.repositories.ConvocatoriaRepository;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/perfiles")
public final class VoluntarioController {
    private final VoluntarioService servicio;
    private final AutorizacionConvocatoriaServicio autorizacion;
    private final ConvocatoriaRepository convocatorias;

    public VoluntarioController(
            VoluntarioService servicio,
            AutorizacionConvocatoriaServicio autorizacion,
            ConvocatoriaRepository convocatorias) {
        this.servicio = servicio;
        this.autorizacion = autorizacion;
        this.convocatorias = convocatorias;
    }

    @GetMapping("/actual")
    public VoluntarioDTO actual(HttpSession sesion) throws IOException {
        return servicio.obtener(autorizacion.requerirAutenticado(sesion).getUsuario());
    }

    @PutMapping("/actual")
    public VoluntarioDTO actualizar(
            @RequestBody PerfilRequest request, HttpSession sesion) throws IOException {
        return servicio.actualizar(
                autorizacion.requerirAutenticado(sesion).getUsuario(), request.aComando());
    }

    @GetMapping("/compatibles")
    public List<VoluntarioDTO> compatibles(
            @RequestParam String convocatoria, HttpSession sesion) throws IOException {
        autorizacion.requerirGestionConvocatorias(sesion);
        var seleccionada = convocatorias.buscar(convocatoria).orElseThrow(() ->
                new IllegalArgumentException("La convocatoria no existe"));
        return servicio.filtrarPorRequisitos(seleccionada.getRequisitos());
    }
}
