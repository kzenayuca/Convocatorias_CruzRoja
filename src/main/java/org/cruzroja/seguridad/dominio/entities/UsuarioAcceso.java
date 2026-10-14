package org.cruzroja.seguridad.dominio.entities;

import org.cruzroja.seguridad.dominio.enums.RolAcceso;

/** Usuario autenticable. Un reclutador debe ser necesariamente graduado. */
public final class UsuarioAcceso {
    private final String usuario;
    private final String clave;
    private final String nombre;
    private final RolAcceso rol;
    private final boolean graduado;

    public UsuarioAcceso(
            String usuario, String clave, String nombre,
            RolAcceso rol, boolean graduado) {
        this.usuario = texto(usuario, "usuario");
        this.clave = texto(clave, "clave");
        this.nombre = texto(nombre, "nombre");
        this.rol = java.util.Objects.requireNonNull(rol, "El rol es obligatorio");
        if (rol == RolAcceso.RECLUTADOR && !graduado) {
            throw new IllegalArgumentException(
                    "El reclutador debe tener perfil de voluntario graduado");
        }
        this.graduado = graduado;
    }

    private static String texto(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("El " + campo + " es obligatorio");
        }
        return valor.trim();
    }

    public boolean claveCoincide(String valor) { return clave.equals(valor); }
    public String getUsuario() { return usuario; }
    public String getNombre() { return nombre; }
    public RolAcceso getRol() { return rol; }
    public boolean isGraduado() { return graduado; }
}
