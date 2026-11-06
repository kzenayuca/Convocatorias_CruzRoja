package pe.cruzroja.convocatorias.membership.application;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.shared.application.*;
import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

public class AccountsUseCases extends UseCaseSupport {
    private final VolunteerRepository repository;
    private final PasswordHasher passwords;
    private final ServiceParticipation participation;
    private final GraduationEligibility graduation;
    private final AnnualEnrollmentRepository cycles;
    public AccountsUseCases(VolunteerRepository repository,PasswordHasher passwords,ServiceParticipation participation,GraduationEligibility graduation,AnnualEnrollmentRepository cycles,ExecutionContext context,NotificationPublisher notifications) {
        super(context,notifications); this.repository=repository; this.passwords=passwords; this.participation=participation; this.graduation=graduation; this.cycles=cycles;
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
    public record Decision(boolean approved,String comment,UUID requestId) {
        public Decision(boolean approved,String comment) { this(approved,comment,null); }
        public Decision { required(comment,2000,"Motivo"); }
    }
    public Map<String,String> register(Registration r) {
        UUID user=UUID.randomUUID(); repository.register(user,r,passwords.encode(r.password()));
        for(var chief:repository.chiefs()) notifyUser(chief,"CUENTA",r.profile().name()+" "+r.profile().surnames()+" solicita activación de cuenta. Revisa y acepta o rechaza la solicitud.","/users?user="+user);
        notifyUser(user,"CUENTA","Tu solicitud de cuenta está pendiente de Jefatura.","/profile?section=condition");
        return Map.of("mensaje","Solicitud registrada. Jefatura debe activar tu cuenta antes de iniciar sesión.");
    }
    public Map<String,Object> profile() { return repository.profile(actor().id()); }
    public void phone(Phone r) { repository.phone(actor().id(),r.phone()); }
    public void change(Profile p) {
        repository.lock(actor().id()); valid(!repository.hasPendingChange(actor().id()),"Ya tienes un cambio de perfil pendiente. Espera su resolución antes de enviar otro.");
        repository.requestChange(actor().id(),p); var request=repository.lockChange(actor().id()); UUID id=(UUID)request.get("request_id");
        var current=repository.profile(actor().id());
        for(var chief:repository.chiefs()) notifyUser(chief,"PERFIL",current.get("name")+" "+current.get("surnames")+" solicita un cambio de perfil. Revisa los datos propuestos.","/users?change="+id);
        notifyUser(actor().id(),"PERFIL","Cambio de perfil enviado. Está pendiente de Jefatura.","/profile?request="+id);
    }
    public List<Map<String,Object>> profileRequests() { return repository.profileRequests(actor().id()); }
    public Map<String,Object> profileRequest(UUID id) { var r=repository.profileRequest(id); require(chief() || actor().id().equals(r.get("user_id")),"No puedes consultar solicitudes de perfil ajenas."); return r; }
    public List<Map<String,Object>> users() { require(chief(),"Solo jefatura puede consultar los datos personales de las cuentas."); return repository.users(); }
    public List<Map<String,Object>> changes() { require(chief(),"Solo jefatura revisa cambios de perfil."); return repository.changes(); }
    public GraduationEligibility.Assessment graduationProgress(UUID id) { require(chief(),"Solo Jefatura evalúa la graduación de otros voluntarios."); repository.profile(id); return graduation.assess(id); }
    public void resolveProfile(UUID id,Decision r) {
        require(chief(),"Solo jefatura revisa cambios de perfil."); var change=repository.lockChange(id);
        UUID request=(UUID)change.get("request_id"); valid(r.requestId()==null || r.requestId().equals(request),"La solicitud cambió o ya fue resuelta. Actualiza la pantalla.");
        if(r.approved()) repository.approveChange(id,change);
        repository.resolveChange(request,r.approved(),r.comment(),actor().id(),now()); repository.deleteChange(id); notifyUser(id,"PERFIL","Cambio de perfil "+(r.approved()?"aprobado: ":"rechazado: ")+r.comment(),"/profile?request="+request);
    }
    public void review(UUID id,Review r) {
        require(chief(),"Solo jefatura administra cuentas.");
        var volunteer=new Volunteer(id,r.role(),r.status(),r.graduated(),r.radioQualified(),r.pilotQualified());
        volunteer.protectOwnAdministration(actor().id());
        participation.lockCommitments(id); repository.lock(id); var previous=repository.profile(id);
        if(r.graduated() && !Boolean.TRUE.equals(previous.get("graduated")) && "VOLUNTARIO".equals(r.role())) {
            var assessment=graduation.assess(id); valid(assessment.eligible(),"La graduación requiere alcanzar la meta de formación con horas reconocidas menos penalizaciones; el saldo se acumula entre años.");
            cycles.graduation(id,actor().id(),now(),new AnnualEnrollmentRepository.GraduationEligibilityData(assessment.balance(),assessment.target()),r.comment());
            cycles.register(id,now().getYear(),actor().id(),now()); cycles.preserveCommitments(id,actor().id(),now());
        }
        repository.save(volunteer,r.comment()); participation.reevaluate(id);
        notifyUser(id,"CUENTA","Revisión de cuenta: "+r.status()+". Condición: "+(r.graduated()?"GRADUADO":"FORMACION")+"; radio: "+(r.radioQualified()?"habilitado":"no habilitado")+"; piloto: "+(r.pilotQualified()?"habilitado":"no habilitado")+". "+r.comment(),"/profile?section=condition");
    }
}
