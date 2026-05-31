package se.meditrack.exception;

/**
 * Kastas när en åtgärd bryter mot separation of duties på person-nivå —
 * dvs. samma person försöker utföra två steg som medvetet ska skötas av
 * olika personer (HSLF-FS 2017:37). Exempel: den som skickade en
 * beställning får inte själv bekräfta den.
 *
 * Eget domänundantag (inte Spring Securitys AccessDeniedException) av två
 * skäl: service-lagret hålls fritt från säkerhetsramverket, och namnet
 * dokumenterar regeln. Mappas till 403 i GlobalExceptionHandler — det är
 * ett åtkomstbeslut, inte ett valideringsfel.
 */
public class SeparationOfDutiesException extends RuntimeException {
    public SeparationOfDutiesException(String message) {
        super(message);
    }
}