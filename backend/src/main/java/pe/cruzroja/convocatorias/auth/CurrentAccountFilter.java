package pe.cruzroja.convocatorias.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** Recarga permisos y estado: una sesión abierta no conserva privilegios revocados. */
public class CurrentAccountFilter extends OncePerRequestFilter {
    private final JdbcUserDetailsService users;
    public CurrentAccountFilter(JdbcUserDetailsService users) { this.users = users; }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws ServletException, IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser old) {
            // Se busca por id porque el correo puede haber cambiado durante la sesión.
            var current = users.loadById(old.id());
            if (current == null || !current.isEnabled()) {
                var session = request.getSession(false);
                if (session != null) session.invalidate();
                SecurityContextHolder.clearContext();
            } else {
                SecurityContextHolder.getContext().setAuthentication(
                        UsernamePasswordAuthenticationToken.authenticated(current, null, current.getAuthorities()));
            }
        }
        chain.doFilter(request, response);
    }
}
