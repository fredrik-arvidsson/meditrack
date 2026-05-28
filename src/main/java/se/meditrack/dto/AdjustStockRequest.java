package se.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import se.meditrack.enums.MovementReason;

/**
 * Manuell saldojustering (svinn, kassation, korrigering). delta kan vara
 * negativt (minska) eller positivt (öka). reason krävs för spårbarhet —
 * varje justering måste motiveras. Resulterar i en StockMovement.
 */
public record AdjustStockRequest(

        @NotNull(message = "delta är obligatoriskt")
        Integer delta,

        @NotNull(message = "reason är obligatorisk")
        MovementReason reason,

        String notes) {

}