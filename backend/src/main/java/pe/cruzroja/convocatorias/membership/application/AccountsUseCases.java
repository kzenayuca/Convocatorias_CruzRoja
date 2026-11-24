package pe.cruzroja.convocatorias.membership.application;

import static pe.cruzroja.convocatorias.shared.domain.Rules.*;

import java.util.*;
import pe.cruzroja.convocatorias.membership.domain.Volunteer;
import pe.cruzroja.convocatorias.shared.application.*;

public class AccountsUseCases extends UseCaseSupport {
    private final VolunteerRepository repository;
    private final PasswordHasher passwords;
    private final ServiceParticipation participation;
    private final GraduationEligibility graduation;
    private final AnnualEnrollmentRepository cycles;

    public AccountsUseCases(
            VolunteerRepository repository,
            PasswordHasher passwords,
            ServiceParticipation participation,
            GraduationEligibility graduation,
            AnnualEnrollmentRepository cycles,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
        this.passwords = passwords;
        this.participation = participation;
        this.graduation = graduation;
        this.cycles = cycles;
    }

    public record Profile(
            String name, String surnames, String email, String document, String occupation) {
        public Profile {
            required(name, 120, "Nombres");
            required(surnames, 160, "Apellidos");
            required(email, 180, "Correo");
            required(occupation, 160, "Ocupación");
            valid(email.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"), "Correo no válido.");
            valid(document != null && document.matches("[0-9]{8}"), "El DNI debe tener 8 dígitos.");
        }
    }

    public record Registration(Profile profile, String password, String phone) {
        public Registration {
            Objects.requireNonNull(profile);
            required(password, 72, "Contraseña");
            valid(password.length() >= 8, "La contraseña requiere 8 caracteres.");
            required(phone, 30, "Teléfono");
        }
    }

    public record Phone(String phone) {
        public Phone {
            required(phone, 30, "Teléfono");
        }
    }

    public record Review(
            String role,
            boolean graduated,
            String status,
            String comment,
            boolean radioQualified,
            boolean pilotQualified) {
        public Review {
            required(role, 30, "Rol");
            required(status, 30, "Estado");
            required(comment, 2000, "Motivo");
        }
    }

    public record Decision(boolean approved, String comment, UUID requestId) {
        public Decision(boolean approved, String comment) {
            this(approved, comment, null);
        }

        public Decision {
            required(comment, 2000, "Motivo");
        }
    }

    public RegistrationReceipt register(Registration registration) {
        UUID volunteerId = UUID.randomUUID();
        repository.register(volunteerId, registration, passwords.encode(registration.password()));
        for (var chief : repository.chiefs())
            notifyUser(
                    chief,
                    "CUENTA",
                    registration.profile().name()
                            + " "
                            + registration.profile().surnames()
                            + " solicita activación de cuenta. Revisa y acepta o rechaza la solicitud.",
                    "/users?user=" + volunteerId);
        notifyUser(
                volunteerId,
                "CUENTA",
                "Tu solicitud de cuenta está pendiente de Jefatura.",
                "/profile?section=condition");
        return new RegistrationReceipt(
                "Solicitud registrada. Jefatura debe activar tu cuenta antes de iniciar sesión.");
    }

    public VolunteerProfile profile() {
        return repository.profile(actor().id());
    }

    public void phone(Phone phoneUpdate) {
        repository.phone(actor().id(), phoneUpdate.phone());
    }

    public void change(Profile proposedProfile) {
        repository.lock(actor().id());
        valid(
                !repository.hasPendingChange(actor().id()),
                "Ya tienes un cambio de perfil pendiente. Espera su resolución antes de enviar otro.");
        repository.requestChange(actor().id(), proposedProfile);
        var pendingChange = repository.lockChange(actor().id());
        UUID requestId = pendingChange.requestId();
        var currentProfile = repository.profile(actor().id());
        for (var chief : repository.chiefs())
            notifyUser(
                    chief,
                    "PERFIL",
                    currentProfile.name()
                            + " "
                            + currentProfile.surnames()
                            + " solicita un cambio de perfil. Revisa los datos propuestos.",
                    "/users?change=" + requestId);
        notifyUser(
                actor().id(),
                "PERFIL",
                "Cambio de perfil enviado. Está pendiente de Jefatura.",
                "/profile?request=" + requestId);
    }

    public List<ProfileChangeRequest> profileRequests() {
        return repository.profileRequests(actor().id());
    }

    public ProfileChangeRequest profileRequest(UUID requestId) {
        var request = repository.profileRequest(requestId);
        require(
                chief() || actor().id().equals(request.userId()),
                "No puedes consultar solicitudes de perfil ajenas.");
        return request;
    }

    public List<VolunteerProfile> users() {
        require(chief(), "Solo jefatura puede consultar los datos personales de las cuentas.");
        return repository.users();
    }

    public List<PendingProfileChange> changes() {
        require(chief(), "Solo jefatura revisa cambios de perfil.");
        return repository.changes();
    }

    public GraduationEligibility.Assessment graduationProgress(UUID volunteerId) {
        require(chief(), "Solo Jefatura evalúa la graduación de otros voluntarios.");
        repository.profile(volunteerId);
        return graduation.assess(volunteerId);
    }

    public void resolveProfile(UUID volunteerId, Decision decision) {
        require(chief(), "Solo jefatura revisa cambios de perfil.");
        var pendingChange = repository.lockChange(volunteerId);
        UUID requestId = pendingChange.requestId();
        valid(
                decision.requestId() == null || decision.requestId().equals(requestId),
                "La solicitud cambió o ya fue resuelta. Actualiza la pantalla.");
        if (decision.approved()) repository.approveChange(volunteerId, pendingChange);
        repository.resolveChange(
                requestId, decision.approved(), decision.comment(), actor().id(), now());
        repository.deleteChange(volunteerId);
        notifyUser(
                volunteerId,
                "PERFIL",
                "Cambio de perfil "
                        + (decision.approved() ? "aprobado: " : "rechazado: ")
                        + decision.comment(),
                "/profile?request=" + requestId);
    }

    public void review(UUID volunteerId, Review review) {
        require(chief(), "Solo jefatura administra cuentas.");
        var volunteer =
                new Volunteer(
                        volunteerId,
                        review.role(),
                        review.status(),
                        review.graduated(),
                        review.radioQualified(),
                        review.pilotQualified());
        volunteer.protectOwnAdministration(actor().id());
        participation.lockCommitments(volunteerId);
        repository.lock(volunteerId);
        var previousProfile = repository.profile(volunteerId);
        if (review.graduated()
                && !previousProfile.graduated()
                && "VOLUNTARIO".equals(review.role())) {
            recordGraduationAndEnroll(volunteerId, review.comment());
        }
        repository.save(volunteer, review.comment());
        participation.reevaluate(volunteerId);
        notifyAccountReview(volunteerId, review);
    }

    private void recordGraduationAndEnroll(UUID volunteerId, String reviewComment) {
        var assessment = graduation.assess(volunteerId);
        valid(
                assessment.eligible(),
                "La graduación requiere alcanzar la meta de formación con horas reconocidas menos penalizaciones; el saldo se acumula entre años.");
        cycles.graduation(
                volunteerId,
                actor().id(),
                now(),
                new AnnualEnrollmentRepository.GraduationEligibilityData(
                        assessment.balance(), assessment.target()),
                reviewComment);
        cycles.register(volunteerId, now().getYear(), actor().id(), now());
        cycles.preserveCommitments(volunteerId, actor().id(), now());
    }

    private void notifyAccountReview(UUID volunteerId, Review review) {
        notifyUser(
                volunteerId,
                "CUENTA",
                "Revisión de cuenta: "
                        + review.status()
                        + ". Condición: "
                        + (review.graduated() ? "GRADUADO" : "FORMACION")
                        + "; radio: "
                        + (review.radioQualified() ? "habilitado" : "no habilitado")
                        + "; piloto: "
                        + (review.pilotQualified() ? "habilitado" : "no habilitado")
                        + ". "
                        + review.comment(),
                "/profile?section=condition");
    }
}
