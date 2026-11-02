package pe.cruzroja.convocatorias.users.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class JdbcUserDetailsService implements UserDetailsService {
    private final JdbcTemplate jdbc;

    public JdbcUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var users = jdbc.query("SELECT id,name,email,password_hash,role,graduated,status FROM users WHERE lower(email)=lower(?)",
                (rs, row) -> new AuthenticatedUser(rs.getObject("id", java.util.UUID.class), rs.getString("name"),
                        rs.getString("email"), rs.getString("password_hash"), rs.getString("role"),
                        rs.getBoolean("graduated"), "ACTIVO".equals(rs.getString("status"))), email);
        if (users.isEmpty()) throw new UsernameNotFoundException("Usuario no encontrado");
        return users.get(0);
    }
}
