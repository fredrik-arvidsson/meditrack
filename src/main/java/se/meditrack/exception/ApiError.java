package se.meditrack.exception;

import java.time.Instant;
import java.util.List;

/**
 * Enhetlig form på felsvar från API:t. Records → immutabelt och kompakt.
 *
 * fieldErrors är null för "vanliga" fel och en lista vid valideringsfel,
 * så klienten kan visa exakt vilka fält som var ogiltiga.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<FieldError> fieldErrors
) {
    public record FieldError(String field, String message) {}

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError ofValidation(String message, String path, List<FieldError> fieldErrors) {
        return new ApiError(Instant.now(), 400, "Bad Request", message, path, fieldErrors);
    }
}