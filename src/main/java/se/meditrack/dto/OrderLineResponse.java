package se.meditrack.dto;

import se.meditrack.entity.OrderLine;

/**
 * Utgående orderrad. medicationName plattas ut så frontend slipper slå upp
 * läkemedlet separat — vanlig UX-optimering för listvyer.
 */
public record OrderLineResponse(
        Long id,
        Long medicationId,
        String medicationName,
        int quantity,
        String notes
) {
    public static OrderLineResponse from(OrderLine line) {
        return new OrderLineResponse(
                line.getId(),
                line.getMedication().getId(),
                line.getMedication().getName(),
                line.getQuantity(),
                line.getNotes()
        );
    }
}