package se.meditrack.exception;

import org.springframework.security.access.AccessDeniedException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * Global felhanterare. Översätter exceptions från ALLA controllers till
 * korrekta HTTP-svar med enhetlig form. Centraliserat — varje controller
 * slipper try/catch.
 *
 * Vad fångas:
 *  - NotFoundException → 404 (resurs finns inte, eller inte i din tenant)
 *  - ValidationException → 400 (affärsregel bröts)
 *  - MethodArgumentNotValidException → 400 med fältfel (Bean Validation)
 *  - Exception (catch-all) → 500, men loggas så vi kan felsöka
 *
 * Inga exception-meddelanden från catch-all visas för klienten — det
 * skulle kunna läcka intern info (stack traces, klassnamn). Klienten får
 * "Internt fel"; loggen får detaljerna.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(404, "Not Found", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(400, "Bad Request", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleBeanValidation(MethodArgumentNotValidException ex,
                                                         HttpServletRequest request) {
        // Bean Validation samlar fältfel — vi plockar ut dem och skickar
        // tillbaka strukturerat så frontend kan visa "namn är obligatoriskt"
        // bredvid rätt fält.
        List<ApiError.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldError(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ApiError body = ApiError.ofValidation("Valideringen misslyckades", request.getRequestURI(), fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex,
                                                       HttpServletRequest request) {
        ApiError body = ApiError.of(403, "Forbidden",
                "Du saknar behörighet för den här åtgärden", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(SeparationOfDutiesException.class)
    public ResponseEntity<ApiError> handleSeparationOfDuties(SeparationOfDutiesException ex,
                                                             HttpServletRequest request) {
        // Person-nivå separation of duties (samma person får inte utföra
        // två steg som ska skötas av olika). 403 precis som rollnekandet —
        // ett brott mot separation of duties är ett åtkomstbeslut, oavsett
        // om det är roll- eller person-nivå. Meddelandet får följa med (till
        // skillnad från catch-all): det avslöjar ingen känslig struktur,
        // bara regeln, och hjälper användaren förstå varför.
        ApiError body = ApiError.of(403, "Forbidden", ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnknown(Exception ex, HttpServletRequest request) {
        // Catch-all: oväntat fel. Logga FULL information för felsökning,
        // men returnera bara ett generiskt meddelande till klienten —
        // exception-meddelanden kan läcka intern struktur eller känslig
        // data om de ekas tillbaka okontrollerat.
        log.error("Oväntat fel vid {}", request.getRequestURI(), ex);
        ApiError body = ApiError.of(500, "Internal Server Error",
                "Ett oväntat fel inträffade", request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
