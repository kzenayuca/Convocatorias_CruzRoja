package org.cruzroja.bc2_convocatorias.aplicacion.services;

import org.cruzroja.bc2_convocatorias.aplicacion.dto.SesionDTO;
import org.cruzroja.bc2_convocatorias.dominio.entities.UsuarioAcceso;
import org.cruzroja.bc2_convocatorias.dominio.enums.RolAcceso;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public final class AutorizacionConvocatoriaServicio {
    private static final String USUARIO_SESION = "usuarioConvocatorias";
    private final Map<String, UsuarioAcceso> usuarios = Map.of(
            "voluntario", new UsuarioAcceso(
                    "voluntario", "voluntario123", "Ana Torres",
                    RolAcceso.VOLUNTARIO, false),
            "graduado", new UsuarioAcceso(
                    "graduado", "graduado123", "Rosa Mendoza",
                    RolAcceso.GRADUADO, true),
            "reclutador", new UsuarioAcceso(
                    "reclutador", "reclutador123", "Elena Salazar",
                    RolAcceso.RECLUTADOR, true),
            "jefatura", new UsuarioAcceso(
                    "jefatura", "jefatura123", "Jefatura de Personal",
                    RolAcceso.JEFATURA, true)
    );

    public SesionDTO iniciar(String usuario, String clave, HttpSession sesion) {
        UsuarioAcceso encontrado = usuarios.get(usuario);
        if (encontrado == null || !encontrado.claveCoincide(clave)) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Usuario o contraseña incorrectos");
        }
        sesion.setAttribute(USUARIO_SESION, encontrado.getUsuario());
        return convertir(encontrado);
    }

    public SesionDTO obtenerSesion(HttpSession sesion) {
        return convertir(requerirAutenticado(sesion));
    }

    public void cerrar(HttpSession sesion) {
        sesion.invalidate();
    }

    public UsuarioAcceso requerirAutenticado(HttpSession sesion) {
        Object usuario = sesion.getAttribute(USUARIO_SESION);
        UsuarioAcceso encontrado = usuario == null ? null : usuarios.get(usuario.toString());
        if (encontrado == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Debe iniciar sesión para consultar los horarios");
        }
        return encontrado;
    }

    public void requerirReclutador(HttpSession sesion) {
        UsuarioAcceso usuario = requerirAutenticado(sesion);
        if (usuario.getRol() != RolAcceso.RECLUTADOR || !usuario.isGraduado()) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo un voluntario graduado designado como reclutador puede modificar horarios");
        }
    }

    public void requerirGestionConvocatorias(HttpSession sesion) {
        UsuarioAcceso usuario = requerirAutenticado(sesion);
        boolean autorizado = usuario.getRol() == RolAcceso.JEFATURA
                || (usuario.getRol() == RolAcceso.RECLUTADOR && usuario.isGraduado());
        if (!autorizado) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Solo reclutamiento o jefatura puede gestionar convocatorias");
        }
    }

    private static SesionDTO convertir(UsuarioAcceso usuario) {
        return new SesionDTO(
                usuario.getUsuario(), usuario.getNombre(), usuario.getRol().name(),
                usuario.isGraduado(), usuario.getRol() == RolAcceso.RECLUTADOR
                        || usuario.getRol() == RolAcceso.JEFATURA);
    }
}
