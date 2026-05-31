package se.meditrack.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meditrack.repository.UserRepository;

import java.util.List;

/**
 * Lär Spring Security att läsa MediTracks egna användare ur databasen.
 *
 * Vid inloggning anropar Spring loadUserByUsername med e-posten användaren
 * angav. Vi slår upp användaren (aktiv, icke-raderad) och returnerar ett
 * UserDetails som bär lösenordshash och roll. Spring jämför sedan det
 * angivna lösenordet mot hashen via PasswordEncoder (BCrypt).
 *
 * Rollen mappas till en Spring-authority med prefixet ROLE_ — det är
 * konventionen som @PreAuthorize("hasRole('PHARMACIST')") förväntar sig.
 * Vi lagrar rollen UTAN prefix i databasen (PHARMACIST), och lägger på
 * ROLE_ här vid översättningen. Att blanda ihop det är ett klassiskt fel.
 */
@Service
public class MeditrackUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public MeditrackUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        se.meditrack.entity.User user = userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Okänd användare: " + email));

        if (!user.isActive() || user.getPasswordHash() == null) {
            throw new UsernameNotFoundException("Inaktiv användare: " + email);
        }

        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));

        // Spring Securitys egen User-klass som UserDetails-implementation.
        // Bär e-post (username), hash (password) och rollen som authority.
        return new User(
                user.getEmail(),
                user.getPasswordHash(),
                authorities);
    }
}