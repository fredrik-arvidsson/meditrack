package se.meditrack.exception;

/**
 * Kastas när en operation bryter mot en affärsregel (t.ex. negativt saldo,
 * otillåten statusövergång). Fångas av @ControllerAdvice och översätts till
 * HTTP 400 Bad Request. Skild från NotFoundException (404) — olika fel,
 * olika HTTP-status.
 */
public class ValidationException extends RuntimeException {

    public ValidationException(String message) {
        super(message);
    }
}