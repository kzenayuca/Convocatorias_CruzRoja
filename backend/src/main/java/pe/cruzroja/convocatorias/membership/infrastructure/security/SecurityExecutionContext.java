package pe.cruzroja.convocatorias.membership.infrastructure.security;

import java.time.*;
import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import pe.cruzroja.convocatorias.shared.application.*;

@Component
public class SecurityExecutionContext implements ExecutionContext {
    private final Clock clock;

    public SecurityExecutionContext(Clock clock) {
        this.clock = clock;
    }

    public Actor actor() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedUser user)
            return new Actor(user.id(), user.role(), user.graduated());
        return new Actor(new UUID(0, 0), "SYSTEM", false);
    }

    public LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
