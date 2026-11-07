package pe.cruzroja.convocatorias.communications.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import pe.cruzroja.convocatorias.communications.domain.Announcement;

@Entity
@Table(name = "announcements")
public class AnnouncementEntity {
    @Id private UUID id;

    @Column(length = 150)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(length = 30)
    private String audience;

    private UUID publishedBy;
    private LocalDateTime publishedAt;

    protected AnnouncementEntity() {}

    public static AnnouncementEntity published(
            UUID id, Announcement message, UUID actor, LocalDateTime at) {
        var entity = new AnnouncementEntity();
        entity.id = id;
        entity.title = message.title();
        entity.body = message.body();
        entity.audience = message.audience();
        entity.publishedBy = actor;
        entity.publishedAt = at;
        return entity;
    }
}
