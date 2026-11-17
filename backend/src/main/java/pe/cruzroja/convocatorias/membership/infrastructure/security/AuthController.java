package pe.cruzroja.convocatorias.membership.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    @GetMapping("/csrf")
    Map<String, String> csrf(org.springframework.security.web.csrf.CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository =
            new HttpSessionSecurityContextRepository();

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {}

    public record SessionResponse(
            String id, String name, String email, String role, boolean graduated) {}

    @PostMapping("/login")
    ResponseEntity<SessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest servletRequest,
            HttpServletResponse servletResponse) {
        var authentication =
                authenticationManager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                request.email(), request.password()));
        if (servletRequest.getSession(false) != null) servletRequest.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, servletResponse);
        return ResponseEntity.ok(toResponse((AuthenticatedUser) authentication.getPrincipal()));
    }

    @GetMapping("/me")
    SessionResponse me() {
        return toResponse(
                (AuthenticatedUser)
                        SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    @PostMapping("/logout")
    Map<String, String> logout(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return Map.of("mensaje", "Sesión cerrada");
    }

    private SessionResponse toResponse(AuthenticatedUser user) {
        return new SessionResponse(
                user.id().toString(), user.name(), user.email(), user.role(), user.graduated());
    }
}
