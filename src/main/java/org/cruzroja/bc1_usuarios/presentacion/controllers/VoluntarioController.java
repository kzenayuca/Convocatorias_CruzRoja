package org.cruzroja.bc1_usuarios.presentacion.controllers;

import org.cruzroja.bc1_usuarios.aplicacion.dto.VoluntarioDTO;
import org.cruzroja.bc1_usuarios.aplicacion.interfaces.IVoluntarioServicio;
import org.cruzroja.bc1_usuarios.presentacion.requests.PerfilRequest;
import org.cruzroja.bc2_convocatorias.aplicacion.services.AutorizacionConvocatoriaServicio;
import org.cruzroja.bc2_convocatorias.dominio.repositories.IConvocatoriaRepositorio;
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
    private final IVoluntarioServicio servicio;
    private final AutorizacionConvocatoriaServicio autorizacion;
    private final IConvocatoriaRepositorio convocatorias;

    public VoluntarioController(
            IVoluntarioServicio servicio,
            AutorizacionConvocatoriaServicio autorizacion,
            IConvocatoriaRepositorio convocatorias) {
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
