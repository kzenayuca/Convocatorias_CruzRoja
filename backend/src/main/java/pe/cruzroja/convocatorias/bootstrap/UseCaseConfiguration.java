package pe.cruzroja.convocatorias.bootstrap;

import java.time.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.*;
import pe.cruzroja.convocatorias.shared.application.*;
import pe.cruzroja.convocatorias.membership.application.*;
import pe.cruzroja.convocatorias.operations.application.*;
import pe.cruzroja.convocatorias.administration.application.*;
import pe.cruzroja.convocatorias.communications.application.*;

/** Composition root: el framework construye/decorara el núcleo, nunca al revés. */
@Configuration
public class UseCaseConfiguration {
    @Bean Clock clock() { return Clock.system(ZoneId.of("America/Lima")); }
    @Bean CalloutWorkflow calloutWorkflow(CalloutRepository r,EnrollmentRepository e,ExecutionContext c,NotificationPublisher n) { return new CalloutWorkflow(r,e,c,n); }
    @Bean MonthlyLedger monthlyLedger(AttendanceRepository r) { return new MonthlyLedger(r); }
    @Bean ReplacementUseCases replacements(AttendanceRepository r,EnrollmentRepository e,CalloutWorkflow w,MonthlyLedger l,ImageInspector i,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new ReplacementUseCases(r,e,w,l,i,c,n),tx,"submit","resolve","detail","candidates"); }
    @Bean ServiceMaintenance maintenance(MaintenanceRepository r,CalloutWorkflow w,MonthlyLedger l,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new ServiceMaintenance(r,w,l,c,n),tx,"refresh"); }
    @Bean ServicesUseCases services(CalloutRepository r,EnrollmentRepository e,ServiceMaintenance m,CalloutWorkflow w,MonthlyLedger l,ImageInspector i,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new ServicesUseCases(r,e,m,w,l,i,c,n),tx,"create","edit","status","enroll","assign","cancellation","review","operation","photo","responsible","changeResponsible"); }
    @Bean AttendanceUseCases attendance(AttendanceRepository r,EnrollmentRepository e,CalloutRepository callouts,CalloutWorkflow w,ServiceMaintenance m,MonthlyLedger l,GraduationSettings settings,ImageInspector images,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new AttendanceUseCases(r,e,callouts,w,m,l,settings,images,c,n),tx,"mark","confirm","correction","rejectReview","justify","resolve","approve"); }
    @Bean MaterialsUseCases materials(MaterialRepository r,CalloutRepository callouts,CalloutWorkflow w,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new MaterialsUseCases(r,callouts,w,c,n),tx,"request","handover"); }
    @Bean AccountsUseCases accounts(VolunteerRepository r,PasswordHasher p,CalloutWorkflow participation,GraduationEligibility graduation,AnnualEnrollmentRepository cycles,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new AccountsUseCases(r,p,participation,graduation,cycles,c,n),tx,"register","phone","change","resolveProfile","review"); }
    @Bean AnnualEnrollmentUseCases annualEnrollments(AnnualEnrollmentRepository r,VolunteerRepository v,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new AnnualEnrollmentUseCases(r,v,c,n),tx,"register"); }
    @Bean AdministrationUseCases administration(AdministrationRepository r,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new AdministrationUseCases(r,c,n),tx,"update"); }
    @Bean NoticesUseCases notices(NoticeRepository r,ExecutionContext c,NotificationPublisher n,PlatformTransactionManager tx) { return transactional(new NoticesUseCases(r,c,n),tx,"publish","read"); }
    private static <T> T transactional(T target,PlatformTransactionManager manager,String... writes) {
        var source=new NameMatchTransactionAttributeSource(); var attribute=new RuleBasedTransactionAttribute();
        for(String method:writes) source.addTransactionalMethod(method,attribute);
        var interceptor=new TransactionInterceptor(); interceptor.setTransactionManager(manager); interceptor.setTransactionAttributeSource(source);
        var proxy=new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
        @SuppressWarnings("unchecked") T result=(T)proxy.getProxy(); return result;
    }
}
