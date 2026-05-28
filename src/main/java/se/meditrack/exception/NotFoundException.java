package se.meditrack.exception;

/**
 * Kastas när en resurs inte finns inom användarens tenant. Fångas senare
 * av en @ControllerAdvice och översätts till HTTP 404. Att den är tenant-
 * medveten betyder: "finns inte FÖR DIG" — en resurs i en annan vårdenhet
 * ska inte gå att skilja från en som inte existerar (ingen informationsläcka).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}