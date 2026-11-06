package pe.cruzroja.convocatorias.membership.application;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class AccountsUseCases extends UseCaseSupport {
    private final VolunteerRepository repository;
    private final PasswordHasher passwords;
    private final ServiceParticipation participation;
    public AccountsUseCases(VolunteerRepository repository,PasswordHasher passwords,ServiceParticipation participation,ExecutionContext context,NotificationPublisher notifications) {
        super(context,notifications); this.repository=repository; this.passwords=passwords; this.participation=participation;
    }
    public record Profile(String name,String surnames,String email,String document,String occupation) {
        public Profile {
            required(name,120,"Nombres"); required(surnames,160,"Apellidos"); required(email,180,"Correo"); required(occupation,160,"Ocupación");
            valid(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"),"Correo no válido."); valid(document!=null && document.matches("[0-9]{8}"),"El DNI debe tener 8 dígitos.");
        }
    }
    public record Registration(Profile profile,String password,String phone) {
        public Registration { Objects.requireNonNull(profile); required(password,72,"Contraseña"); valid(password.length()>=8,"La contraseña requiere 8 caracteres."); required(phone,30,"Teléfono"); }
    }
    public record Phone(String phone) { public Phone { required(phone,30,"Teléfono"); } }
    public record Review(String role,boolean graduated,String status,String comment,boolean radioQualified,boolean pilotQualified) {
        public Review { required(role,30,"Rol"); required(status,30,"Estado"); required(comment,2000,"Motivo"); }
    }
    public record Decision(boolean approved,String comment) { public Decision { required(comment,2000,"Motivo"); } }
    public Map<String,String> register(Registration r) {
        repository.register(UUID.randomUUID(),r,passwords.encode(r.password()));
        return Map.of("mensaje","Solicitud registrada. Jefatura debe activar tu cuenta antes de iniciar sesión.");
    }
    public Map<String,Object> profile() { return repository.profile(actor().id()); }
    public void phone(Phone r) { repository.phone(actor().id(),r.phone()); }
    public void change(Profile p) { repository.requestChange(actor().id(),p); }
    public List<Map<String,Object>> users() { require(chief(),"Solo jefatura puede consultar los datos personales de las cuentas."); return repository.users(); }
    public List<Map<String,Object>> changes() { require(chief(),"Solo jefatura revisa cambios de perfil."); return repository.changes(); }
    public void resolveProfile(UUID id,Decision r) {
        require(chief(),"Solo jefatura revisa cambios de perfil."); var change=repository.lockChange(id);
        if(r.approved()) repository.approveChange(id,change);
        repository.deleteChange(id); notifyUser(id,"PERFIL","Cambio de perfil "+(r.approved()?"aprobado: ":"rechazado: ")+r.comment(),"/profile");
    }
    public void review(UUID id,Review r) {
        require(chief(),"Solo jefatura administra cuentas.");
        var volunteer=new Volunteer(id,r.role(),r.status(),r.graduated(),r.radioQualified(),r.pilotQualified());
        volunteer.protectOwnAdministration(actor().id());
        participation.lockCommitments(id); repository.lock(id); repository.save(volunteer,r.comment()); participation.reevaluate(id);
        notifyUser(id,"CUENTA","Estado de cuenta: "+r.status()+". "+r.comment(),"/profile");
    }
}
