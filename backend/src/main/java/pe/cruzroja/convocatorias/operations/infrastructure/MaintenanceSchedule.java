package pe.cruzroja.convocatorias.operations.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import pe.cruzroja.convocatorias.operations.application.ServiceMaintenance;

@Configuration
@EnableScheduling
public class MaintenanceSchedule {
    private final ServiceMaintenance maintenance;

    public MaintenanceSchedule(ServiceMaintenance maintenance) {
        this.maintenance = maintenance;
    }

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void refresh() {
        maintenance.refresh();
    }
}
