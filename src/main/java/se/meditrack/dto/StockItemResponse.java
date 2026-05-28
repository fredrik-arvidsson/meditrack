package se.meditrack.dto;

import se.meditrack.entity.StockItem;

/**
 * Utgående lagerpost. belowThreshold beräknas vid mappning — klienten ska
 * inte behöva räkna själv (UX för stressade sjuksköterskor: visa varningen
 * färdig). medicationName plattas ut hit för att slippa nästlade objekt i
 * listvyer.
 */
public record StockItemResponse(
        Long id,
        Long medicationId,
        String medicationName,
        int quantity,
        int threshold,
        boolean belowThreshold
) {
    public static StockItemResponse from(StockItem stockItem) {
        return new StockItemResponse(
                stockItem.getId(),
                stockItem.getMedication().getId(),
                stockItem.getMedication().getName(),
                stockItem.getQuantity(),
                stockItem.getThreshold(),
                stockItem.getQuantity() < stockItem.getThreshold()
        );
    }
}