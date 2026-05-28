package se.meditrack.security;

import org.springframework.stereotype.Component;

/**
 * Ger tillgång till inloggad användares kontext (id + vårdenhet).
 *
 * PLATSHÅLLARE: returnerar tills vidare fasta värden så service-lagret kan
 * byggas och testas innan auth finns. När Spring Security kopplas in byts
 * implementationen mot en som läser SecurityContextHolder — utan att någon
 * service behöver ändras. Detta isolerar "vem är användaren?" till ett ställe.
 */
@Component
public class CurrentUserProvider {

    // TODO: ersätt med SecurityContextHolder-lookup när auth är på plats
    private static final Long SYSTEM_CARE_UNIT_ID = 1L;
    private static final Long SYSTEM_USER_ID = 1L;

    public Long getCurrentCareUnitId() {
        return SYSTEM_CARE_UNIT_ID;
    }

    public Long getCurrentUserId() {
        return SYSTEM_USER_ID;
    }
}