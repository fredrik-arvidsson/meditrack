package se.meditrack.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.meditrack.dto.MeResponse;
import se.meditrack.entity.User;
import se.meditrack.exception.NotFoundException;
import se.meditrack.repository.UserRepository;
import se.meditrack.security.CurrentUserProvider;

/**
 * Inloggad användares profil. Frontend anropar GET /api/me för att
 * (a) verifiera credentials vid inloggning — 200 betyder rätt, 401 fel —
 * och (b) hämta rollen, så UI:t kan visa rätt åtgärder.
 *
 * Endpointen kräver inloggning (som allt annat utom OPTIONS), så om vi
 * når hit finns en autentiserad användare. Vi hämtar dess id via
 * CurrentUserProvider och slår upp profilen.
 */
@RestController
@RequestMapping("/api/me")
public class MeController {

    private final UserRepository userRepository;
    private final CurrentUserProvider currentUser;

    public MeController(UserRepository userRepository, CurrentUserProvider currentUser) {
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @GetMapping
    public MeResponse me() {
        Long userId = currentUser.getCurrentUserId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("Inloggad användare hittades inte: " + userId));
        return MeResponse.from(user);
    }
}