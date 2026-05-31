package se.meditrack.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import se.meditrack.entity.User;
import se.meditrack.repository.UserRepository;

/**
 * Ger tillgång till inloggad användares kontext (id + vårdenhet).
 *
 * Läser den autentiserade användaren ur SecurityContextHolder. Basic auth
 * bär bara användarnamnet (e-posten), så vi slår upp User-entiteten på
 * e-post för att få userId och careUnitId — den senare är tenant-nyckeln
 * som hela datalagret scopar mot.
 *
 * Tidigare var detta en platshållare med fasta värden. Bytet hit krävde
 * INGEN ändring i något service-lager: "vem är användaren?" var isolerat
 * bakom den här klassen från början, så bara implementationen byttes.
 */
@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Long getCurrentCareUnitId() {
        return currentUser().getCareUnit().getId();
    }

    public Long getCurrentUserId() {
        return currentUser().getId();
    }

    /**
     * Hämtar inloggad User ur säkerhetskontexten. Kastar om ingen är
     * autentiserad — endpoints som når hit ska redan vara skyddade av
     * SecurityConfig, så detta är ett skyddsräcke, inte ett normalfall.
     */
    private User currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("Ingen autentiserad användare i kontexten");
        }
        String email = auth.getName(); // username = e-post i Basic auth
        return userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() ->
                        new IllegalStateException("Inloggad användare saknas i databasen: " + email));
    }
}