package pe.cruzroja.convocatorias.shared.application;

import java.util.UUID;

/** Contrato público para que cambios de cuenta reevalúen compromisos futuros sin acoplar módulos. */
public interface ServiceParticipation {
    void lockCommitments(UUID volunteer);
    void reevaluate(UUID volunteer);
}
