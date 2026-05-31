package se.meditrack.dto;

import se.meditrack.entity.User;
import se.meditrack.enums.Role;

/**
 * Inloggad användares profil. Frontend använder denna för att (a) verifiera
 * credentials vid inloggning, och (b) känna till rollen — så att UI:t kan
 * dölja åtgärder användaren ändå skulle nekas (t.ex. "Bekräfta" för en
 * sjuksköterska). UI-dölјningen är bekvämlighet, inte säkerhet: backend
 * genomdriver alltid behörigheten oavsett vad frontend visar.
 *
 * Notera: ingen lösenordshash exponeras — bara identitet och roll.
 */
public record MeResponse(
        Long id,
        String name,
        String email,
        Role role
) {
    public static MeResponse from(User user) {
        return new MeResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}