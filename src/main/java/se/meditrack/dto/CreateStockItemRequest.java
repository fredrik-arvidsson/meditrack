package se.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Börja lagerföra ett läkemedel på vårdenheten. Sätter startsaldo och
 * threshold. medicationId pekar ut vilket läkemedel; careUnitId sätts av
 * service från inloggad användare (aldrig från klienten).
 */
public record CreateStockItemRequest(

        @NotNull(message = "medicationId är obligatoriskt")
        Long medicationId,

        @NotNull
        @PositiveOrZero(message = "Startsaldo kan inte vara negativt")
        Integer initialQuantity,

        @NotNull
        @PositiveOrZero(message = "Threshold kan inte vara negativt")
        Integer threshold) {

}