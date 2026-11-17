package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.operations.application.ServicesUseCases;

/** Scalar references avoid loading accounts, teams or binary photos with a command. */
@Entity
@Table(name = "callouts")
public class CalloutEntity {
    @Id private UUID id;
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    private LocalDateTime startAt;
    private LocalDateTime endAt;
    private LocalDateTime deadline;
    private String location;

    @Column(columnDefinition = "text")
    private String requirements;

    private String minimumLevel;
    private int capacity;
    private int minimumParticipants;
    private String status;
    private String kind;
    private String area;
    private String contact;
    private UUID createdBy;
    private UUID responsibleId;
    private LocalDateTime openedAt;
    private LocalDateTime closedAt;

    @Column(columnDefinition = "text")
    private String observations;

    private String serviceCode;
    private String serviceType;
    private int socorristaSlots;
    private int radioSlots;
    private int pilotSlots;
    private int graduateSlots;
    private int traineeSlots;
    private int totalVolunteers;

    protected CalloutEntity() {}

    public static CalloutEntity draft(
            UUID id, ServicesUseCases.Callout request, UUID creator, String code) {
        var entity = new CalloutEntity();
        entity.id = id;
        entity.createdBy = creator;
        entity.status = "BORRADOR";
        entity.minimumLevel = "BASICO";
        entity.contact = "";
        entity.observations = "";
        entity.apply(request, code);
        return entity;
    }

    public void apply(ServicesUseCases.Callout request, String code) {
        title = request.title();
        description = request.description();
        startAt = request.start();
        endAt = request.end();
        deadline = request.deadline();
        location = request.location();
        requirements = request.requirements();
        kind = request.kind();
        area = request.area();
        serviceCode = code;
        serviceType = request.serviceType();
        totalVolunteers = request.totalVolunteers();
        capacity = totalVolunteers;
        minimumParticipants = totalVolunteers;
        socorristaSlots = totalVolunteers - request.radioSlots() - request.pilotSlots();
        graduateSlots = request.graduateSlots();
        radioSlots = request.radioSlots();
        pilotSlots = request.pilotSlots();
        traineeSlots = 0;
    }

    public void storeStatus(String value) {
        status = value;
    }

    public void appendObservation(String note) {
        observations = (observations == null ? "" : observations) + note;
    }

    public void storeCancellation(String reason) {
        status = "CANCELADA";
        observations = reason;
    }

    public void storeOperation(String action, String note, LocalDateTime at) {
        observations = note;
        if ("ABRIR".equals(action)) {
            openedAt = at;
        } else {
            closedAt = at;
            status = "FINALIZADA";
        }
    }
}
