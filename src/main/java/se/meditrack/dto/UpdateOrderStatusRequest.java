package se.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import se.meditrack.enums.OrderStatus;

/**
 * Begär en statusövergång (DRAFT→SENT→CONFIRMED→DELIVERED / CANCELLED).
 * Service validerar att övergången är tillåten via OrderStateMachine —
 * man kan inte hoppa direkt från DRAFT till DELIVERED. Klienten begär ett
 * måltillstånd; service avgör om det är lagligt.
 */
public record UpdateOrderStatusRequest(

        @NotNull(message = "status är obligatorisk")
        OrderStatus targetStatus) {

}