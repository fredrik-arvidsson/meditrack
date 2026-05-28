package se.meditrack.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * En rad i en inkommande beställning: vilket läkemedel + hur många.
 * Nästlas inuti CreateOrderRequest. Ingen orderId här — raden hör till
 * den order som skapas i samma request.
 */
public record OrderLineRequest(

        @NotNull(message = "medicationId är obligatoriskt")
        Long medicationId,

        @NotNull
        @Positive(message = "Kvantitet måste vara större än noll")
        Integer quantity,

        String notes) {

}