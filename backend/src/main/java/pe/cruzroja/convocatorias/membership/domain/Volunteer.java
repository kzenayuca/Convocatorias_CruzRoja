package pe.cruzroja.convocatorias.membership.domain;

import java.util.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

/** Raíz de agregado de cuenta: condición y habilitaciones no equivalen a permisos de responsable. */
public record Volunteer(UUID id,String role,String status,boolean graduated,boolean radioQualified,boolean pilotQualified) {
    public Volunteer {
        Objects.requireNonNull(id);
        valid(Set.of("VOLUNTARIO","JEFATURA").contains(role),"Rol no válido. Responsable es una función por servicio, no un rol de cuenta.");
        valid(Set.of("ACTIVO","PENDIENTE","RECHAZADO","INACTIVO","SUSPENDIDO").contains(status),"Estado no válido.");
    }
    public void protectOwnAdministration(UUID actor) {
        valid(!id.equals(actor) || status.equals("ACTIVO") && role.equals("JEFATURA"),"No puedes retirar tu propio acceso de jefatura.");
    }
}
