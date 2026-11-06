package pe.cruzroja.convocatorias.membership.application;

import java.time.LocalDateTime;
import java.util.*;

public interface AnnualEnrollmentRepository {
    List<Map<String,Object>> list(UUID user);
    boolean register(UUID user,int year,UUID actor,LocalDateTime at);
    void preserveCommitments(UUID user,UUID actor,LocalDateTime at);
    void graduation(UUID user,UUID actor,LocalDateTime at,GraduationEligibilityData data,String reason);
    record GraduationEligibilityData(double balance,double target) { }
}
