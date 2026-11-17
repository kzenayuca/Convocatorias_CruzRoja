package pe.cruzroja.convocatorias.operations.infrastructure.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Internal ORM repository; never injected into a controller or a use case. */
public interface CalloutJpaRepository extends JpaRepository<CalloutEntity, UUID> {}
