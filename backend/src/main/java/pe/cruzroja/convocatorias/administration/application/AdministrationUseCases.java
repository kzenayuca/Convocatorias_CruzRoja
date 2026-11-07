package pe.cruzroja.convocatorias.administration.application;

import java.util.Map;
import pe.cruzroja.convocatorias.administration.domain.AdministrationRepository;
import pe.cruzroja.convocatorias.administration.domain.HourTargets;
import pe.cruzroja.convocatorias.shared.application.*;

public class AdministrationUseCases extends UseCaseSupport {
    private final AdministrationRepository repository;

    public AdministrationUseCases(
            AdministrationRepository repository,
            ExecutionContext context,
            NotificationPublisher notifications) {
        super(context, notifications);
        this.repository = repository;
    }

    public Map<String, Object> settings() {
        require(chief(), "Solo jefatura administra los requisitos institucionales.");
        return repository.settings();
    }

    public void update(HourTargets targets) {
        require(chief(), "Solo jefatura configura las metas de horas.");
        repository.save(targets, actor().id(), now());
    }
}
