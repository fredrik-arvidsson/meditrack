package se.meditrack.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import se.meditrack.ai.ReorderResult;
import se.meditrack.ai.ReorderSuggestionService;

/**
 * AI-agentens enda endpoint: föreslå påfyllning och skapa ett DRAFT-utkast.
 *
 * POST (inte GET) eftersom anropet har en sidoeffekt — det skapar en order.
 * 201 Created när ett utkast faktiskt skapades, 200 OK när inget behövde
 * göras (inget under tröskel). Tunn som alla controllers: tar emot,
 * delegerar, returnerar. All logik och alla säkerhetsgränser bor i
 * ReorderSuggestionService.
 */
@RestController
@RequestMapping("/api/orders")
public class ReorderController {

    private final ReorderSuggestionService reorderService;

    public ReorderController(ReorderSuggestionService reorderService) {
        this.reorderService = reorderService;
    }

    @PostMapping("/suggest-reorder")
    public ResponseEntity<ReorderResult> suggestReorder() {
        ReorderResult result = reorderService.suggestAndCreateDraft();

        HttpStatus status = result.draftOrder() != null
                ? HttpStatus.CREATED
                : HttpStatus.OK;

        return ResponseEntity.status(status).body(result);
    }
}