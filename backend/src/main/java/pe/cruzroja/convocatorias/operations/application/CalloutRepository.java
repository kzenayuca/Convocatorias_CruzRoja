package pe.cruzroja.convocatorias.operations.application;

import java.time.*;
import java.util.*;
import pe.cruzroja.convocatorias.operations.domain.*;

public interface CalloutRepository {
    Map<String,Object> snapshot(UUID id,boolean lock);
    Map<String,Object> view(UUID id);
    List<Map<String,Object>> views();
    List<Map<String,Object>> schedule(LocalDate day,UUID volunteer);
    List<Callout.Member> members(UUID id);
    Participant participant(UUID id);
    boolean overlaps(UUID callout,UUID volunteer,LocalDateTime start,LocalDateTime end);
    boolean attended(UUID callout,UUID volunteer);
    List<String> replacementPositions(UUID callout);
    String responsiblePhone(UUID callout,UUID volunteer);
    List<UUID> activeUsers();
    void create(UUID id,ServicesUseCases.Callout request,UUID creator,String code);
    void edit(UUID id,ServicesUseCases.Callout request,String code);
    void recordCode(UUID id,String code,String type,UUID actor);
    List<Map<String,Object>> codes();
    void status(UUID id,String status);
    void cancel(UUID id,String reason);
    void operation(UUID id,String action,String observations,LocalDateTime at);
    List<Map<String,Object>> responsibleCandidates(UUID id);
    void changeResponsible(UUID id,UUID previous,UUID next,UUID actor,LocalDateTime at,String reason);
    void synchronizeResponsible(UUID id,UUID previous,UUID next,UUID actor,LocalDateTime at);
    List<Map<String,Object>> responsibleHistory(UUID id);
    void photo(UUID id,byte[] bytes,String type,UUID actor,LocalDateTime at);
    TeamPhoto photo(UUID id);
    List<Map<String,Object>> photos(UUID volunteer);
    record TeamPhoto(byte[] bytes,String type) { public TeamPhoto { bytes=bytes.clone(); } @Override public byte[] bytes() { return bytes.clone(); } }
}
