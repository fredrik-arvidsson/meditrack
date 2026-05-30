package se.meditrack.ai;

/**
 * Ett påfyllningsförslag för EN lagerpost. Rent resonemangsresultat —
 * innehåller medicationId (för att servicen ska kunna bygga en
 * OrderLineRequest), föreslagen kvantitet, och en kort motivering som
 * kan visas för användaren.
 *
 * medicationId och nuvarande saldo kommer ALLTID från databasen, aldrig
 * från modellen. Modellen får bara föreslå quantity + reason.
 */
public record ReorderSuggestion(
        Long medicationId,
        String medicationName,
        int currentQuantity,
        int threshold,
        int suggestedQuantity,
        String reason) {
}