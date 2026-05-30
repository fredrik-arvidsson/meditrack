package se.meditrack.ai;

import se.meditrack.dto.OrderResponse;

import java.util.List;

/**
 * Resultatet av ett påfyllningsförslag: det skapade DRAFT-utkastet (eller
 * null om inget skapades), de råa förslagen (för att visa motiveringar i
 * UI:t), källetiketten (Gemini vs regel — ärlighet om ursprung), och ett
 * läsbart meddelande.
 */
public record ReorderResult(
        OrderResponse draftOrder,
        List<ReorderSuggestion> suggestions,
        String source,
        String message) {
}