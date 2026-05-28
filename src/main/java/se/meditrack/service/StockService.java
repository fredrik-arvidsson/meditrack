package se.meditrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meditrack.dto.AdjustStockRequest;
import se.meditrack.dto.CreateStockItemRequest;
import se.meditrack.dto.StockItemResponse;
import se.meditrack.dto.UpdateThresholdRequest;
import se.meditrack.entity.CareUnit;
import se.meditrack.entity.Medication;
import se.meditrack.entity.StockItem;
import se.meditrack.entity.StockMovement;
import se.meditrack.enums.MovementReason;
import se.meditrack.exception.NotFoundException;
import se.meditrack.exception.ValidationException;
import se.meditrack.repository.CareUnitRepository;
import se.meditrack.repository.MedicationRepository;
import se.meditrack.repository.StockItemRepository;
import se.meditrack.repository.StockMovementRepository;
import se.meditrack.security.CurrentUserProvider;

import java.util.List;

/**
 * Affärslogik för lagerhantering. Hjärtat i MediTrack.
 *
 * Två invarianter som koden upprätthåller strikt:
 *  1. Saldo ändras ALDRIG utan att en StockMovement skapas samtidigt
 *     (saldo + historik är atomära — se applyMovement).
 *  2. Den saldokritiska skrivningen sker under pessimistisk låsning
 *     (SELECT ... FOR UPDATE) så samtidiga uppdateringar serialiseras
 *     istället för att skapa lost updates.
 *
 * Allt inom @Transactional: saldo och historik committas tillsammans
 * eller inte alls.
 */
@Service
@Transactional
public class StockService {

    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MedicationRepository medicationRepository;
    private final CareUnitRepository careUnitRepository;
    private final CurrentUserProvider currentUser;

    public StockService(StockItemRepository stockItemRepository,
                        StockMovementRepository stockMovementRepository,
                        MedicationRepository medicationRepository,
                        CareUnitRepository careUnitRepository,
                        CurrentUserProvider currentUser) {
        this.stockItemRepository = stockItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.medicationRepository = medicationRepository;
        this.careUnitRepository = careUnitRepository;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> findAll() {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        return stockItemRepository.findAllByCareUnitId(careUnitId).stream()
                .map(StockItemResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> findBelowThreshold() {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        return stockItemRepository.findBelowThreshold(careUnitId).stream()
                .map(StockItemResponse::from)
                .toList();
    }

    public StockItemResponse createStockItem(CreateStockItemRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        CareUnit careUnit = careUnitRepository.findById(careUnitId)
                .orElseThrow(() -> new NotFoundException("Vårdenhet hittades inte: " + careUnitId));
        Medication medication = medicationRepository.findByIdAndCareUnitId(request.medicationId(), careUnitId)
                .orElseThrow(() -> new NotFoundException("Läkemedel hittades inte: " + request.medicationId()));

        StockItem stockItem = new StockItem();
        stockItem.setCareUnit(careUnit);
        stockItem.setMedication(medication);
        stockItem.setQuantity(0);
        stockItem.setThreshold(request.threshold());
        StockItem saved = stockItemRepository.save(stockItem);

        // Startsaldot bokförs som en INITIAL-rörelse — även utgångsläget
        // har en historikrad. Spårbarhet från första dagen.
        if (request.initialQuantity() > 0) {
            applyMovement(saved, request.initialQuantity(), MovementReason.INITIAL, null, "Startsaldo");
        }

        return StockItemResponse.from(saved);
    }

    public StockItemResponse updateThreshold(Long stockItemId, UpdateThresholdRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        StockItem stockItem = stockItemRepository.findByIdAndCareUnitId(stockItemId, careUnitId)
                .orElseThrow(() -> new NotFoundException("Lagerpost hittades inte: " + stockItemId));

        // Threshold är ren konfiguration — ingen StockMovement, inget lås.
        stockItem.setThreshold(request.threshold());
        return StockItemResponse.from(stockItem);
    }

    public StockItemResponse adjustStock(Long stockItemId, AdjustStockRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();

        // Pessimistisk låsning även här — saldo är patientsäkerhetskritiskt,
        // och en kodväg är enklare att resonera om. (Adjust är sällan
        // samtidig; optimistic hade räckt — noterat i README.)
        StockItem stockItem = stockItemRepository.findByIdAndCareUnitIdForUpdate(stockItemId, careUnitId)
                .orElseThrow(() -> new NotFoundException("Lagerpost hittades inte: " + stockItemId));

        applyMovement(stockItem, request.delta(), request.reason(), null, request.notes());
        return StockItemResponse.from(stockItem);
    }

    /**
     * Tar emot en leverans: ökar saldot för en lagerpost. Den saldokritiska
     * operationen — pessimistisk låsning (FOR UPDATE) serialiserar samtidiga
     * leveranser. Anropas från OrderService när en order går till DELIVERED.
     */
    public void receiveDelivery(Long stockItemId, int quantity, Long orderId) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        StockItem stockItem = stockItemRepository.findByIdAndCareUnitIdForUpdate(stockItemId, careUnitId)
                .orElseThrow(() -> new NotFoundException("Lagerpost hittades inte: " + stockItemId));

        applyMovement(stockItem, quantity, MovementReason.DELIVERY, orderId, "Leverans av order " + orderId);
    }

    /**
     * Den enda vägen att ändra ett saldo. Uppdaterar quantity OCH skapar en
     * StockMovement med quantity_after — atomärt, inom samma transaktion.
     * Saldo och historik kan aldrig glida isär.
     */
    private void applyMovement(StockItem stockItem, int delta, MovementReason reason,
                               Long orderId, String notes) {
        int newQuantity = stockItem.getQuantity() + delta;
        if (newQuantity < 0) {
            throw new ValidationException(
                    "Otillåten lagerförändring: saldo kan inte bli negativt (nuvarande "
                            + stockItem.getQuantity() + ", delta " + delta + ")");
        }
        stockItem.setQuantity(newQuantity);

        StockMovement movement = new StockMovement();
        movement.setCareUnit(stockItem.getCareUnit());
        movement.setStockItem(stockItem);
        movement.setMedication(stockItem.getMedication());
        movement.setDelta(delta);
        movement.setQuantityAfter(newQuantity);
        movement.setReason(reason);
        movement.setNotes(notes);
        movement.setCreatedBy(currentUser.getCurrentUserId());
        if (orderId != null) {
            // order sätts om rörelsen härstammar från en order (DELIVERY).
            // Vi laddar inte hela Order-entiteten här i onödan — men eftersom
            // StockMovement.order är en @ManyToOne behöver vi en referens.
            // Hanteras i OrderService som har Order-objektet; för fristående
            // anrop lämnas order null och orderId noteras i notes.
        }
        stockMovementRepository.save(movement);
    }
}