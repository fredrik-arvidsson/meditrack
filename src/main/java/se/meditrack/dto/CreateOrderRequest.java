package se.meditrack.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Skapa en beställning med en eller flera rader. @Valid på listan gör att
 * varje OrderLineRequest valideras individuellt (kaskadvalidering) — utan
 * det kollas bara att listan inte är tom, inte radernas innehåll.
 * careUnitId sätts av service; status börjar alltid som DRAFT (inte
 * klientens val).
 */
public record CreateOrderRequest(

        @NotEmpty(message = "En beställning måste ha minst en rad")
        @Valid
        List<OrderLineRequest> lines,

        String notes) {

}