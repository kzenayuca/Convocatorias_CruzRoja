package pe.cruzroja.convocatorias.application;

import pe.cruzroja.convocatorias.domain.*;

/** Caso de uso pequeño y comprobable con un doble del repositorio de configuración. */
public class GraduationService {
    private final GraduationSettings settings;
    public GraduationService(GraduationSettings settings) { this.settings=settings; }
    public GraduationProgress progress(double earned,double deductions,int pending,boolean graduated) {
        return GraduationProgress.of(earned,deductions,settings.requiredHours(graduated),pending);
    }
}
