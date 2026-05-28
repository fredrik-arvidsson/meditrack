package se.meditrack.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import se.meditrack.dto.CreateOrderRequest;
import se.meditrack.dto.OrderLineRequest;
import se.meditrack.dto.OrderResponse;
import se.meditrack.dto.UpdateOrderStatusRequest;
import se.meditrack.entity.CareUnit;
import se.meditrack.entity.Medication;
import se.meditrack.entity.Order;
import se.meditrack.entity.OrderLine;
import se.meditrack.entity.StockItem;
import se.meditrack.entity.StockMovement;
import se.meditrack.enums.MovementReason;
import se.meditrack.enums.OrderStatus;
import se.meditrack.exception.NotFoundException;
import se.meditrack.exception.ValidationException;
import se.meditrack.repository.CareUnitRepository;
import se.meditrack.repository.MedicationRepository;
import se.meditrack.repository.OrderRepository;
import se.meditrack.repository.StockItemRepository;
import se.meditrack.repository.StockMovementRepository;
import se.meditrack.security.CurrentUserProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Beställningsflödet. Skapar order med rader, driver statusövergångar via
 * OrderStateMachine, och utlöser lageruppdatering vid leverans.
 *
 * Designval värda att lyfta:
 *  - Status sätts ALDRIG från klienten. Skapas alltid som DRAFT, ändras
 *    bara via updateStatus som går genom OrderStateMachine.
 *  - Vid DELIVERED ökas saldot per rad atomärt med pessimistisk låsning
 *    (samma transaktion). DELIVERED är därför den enda statusövergången
 *    med sidoeffekter på lager.
 *  - StockMovement.order sätts HÄR (sömmen från StockService 14.9):
 *    OrderService har Order-objektet, kan koppla det direkt.
 */
@Service
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final MedicationRepository medicationRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final CareUnitRepository careUnitRepository;
    private final OrderStateMachine stateMachine;
    private final CurrentUserProvider currentUser;

    public OrderService(OrderRepository orderRepository,
                        MedicationRepository medicationRepository,
                        StockItemRepository stockItemRepository,
                        StockMovementRepository stockMovementRepository,
                        CareUnitRepository careUnitRepository,
                        OrderStateMachine stateMachine,
                        CurrentUserProvider currentUser) {
        this.orderRepository = orderRepository;
        this.medicationRepository = medicationRepository;
        this.stockItemRepository = stockItemRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.careUnitRepository = careUnitRepository;
        this.stateMachine = stateMachine;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        return orderRepository.findAllByCareUnitIdOrderByCreatedAtDesc(careUnitId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        Order order = orderRepository.findByIdAndCareUnitId(id, careUnitId)
                .orElseThrow(() -> new NotFoundException("Beställning hittades inte: " + id));
        return OrderResponse.from(order);
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        CareUnit careUnit = careUnitRepository.findById(careUnitId)
                .orElseThrow(() -> new NotFoundException("Vårdenhet hittades inte: " + careUnitId));

        Order order = new Order();
        order.setCareUnit(careUnit);
        order.setOrderNumber(generateOrderNumber());
        // Status sätts av service, inte av klient. Alla beställningar
        // börjar i DRAFT — övergångarna sker via updateStatus.
        order.setStatus(OrderStatus.DRAFT);
        order.setNotes(request.notes());

        for (OrderLineRequest lineRequest : request.lines()) {
            Medication medication = medicationRepository
                    .findByIdAndCareUnitId(lineRequest.medicationId(), careUnitId)
                    .orElseThrow(() -> new NotFoundException(
                            "Läkemedel hittades inte: " + lineRequest.medicationId()));

            OrderLine line = new OrderLine();
            line.setOrder(order);
            line.setMedication(medication);
            line.setQuantity(lineRequest.quantity());
            line.setNotes(lineRequest.notes());
            order.getLines().add(line);
        }

        Order saved = orderRepository.save(order);
        // Cascade ALL + orphanRemoval på Order.lines gör att raderna
        // sparas tillsammans — ingen separat save() på OrderLine.
        return OrderResponse.from(saved);
    }

    public OrderResponse updateStatus(Long orderId, UpdateOrderStatusRequest request) {
        Long careUnitId = currentUser.getCurrentCareUnitId();
        Order order = orderRepository.findByIdAndCareUnitId(orderId, careUnitId)
                .orElseThrow(() -> new NotFoundException("Beställning hittades inte: " + orderId));

        OrderStatus current = order.getStatus();
        OrderStatus target = request.targetStatus();

        // Reglerna ligger i en klass, inte spridda i if-satser här.
        stateMachine.validateTransition(current, target);

        LocalDateTime now = LocalDateTime.now();
        Long actorId = currentUser.getCurrentUserId();

        // Tidsstämpel + aktör per övergång — spårbarhet för PDL/audit.
        switch (target) {
            case SENT -> {
                order.setSentAt(now);
                order.setSentBy(actorId);
            }
            case CONFIRMED -> {
                order.setConfirmedAt(now);
                order.setConfirmedBy(actorId);
            }
            case DELIVERED -> {
                order.setDeliveredAt(now);
                order.setDeliveredBy(actorId);
                // Den enda övergången med sidoeffekter på lager.
                applyDeliveryToStock(order);
            }
            case CANCELLED -> {
                // Ingen tidsstämpel-kolumn för cancellation i V1 —
                // updated_at från Auditable räcker som spår.
            }
            default -> throw new ValidationException(
                    "Ohanterat målläge: " + target);
        }

        order.setStatus(target);
        return OrderResponse.from(order);
    }

    public OrderResponse cancelOrder(Long orderId) {
        // Bekvämlighetsmetod — under huven är cancellation bara en
        // statusövergång till CANCELLED. Reglerna i stateMachine
        // avgör om det är lagligt (DELIVERED kan inte avbeställas).
        return updateStatus(orderId, new UpdateOrderStatusRequest(OrderStatus.CANCELLED));
    }

    /**
     * Vid DELIVERED: öka saldot för varje rads läkemedel. Använder
     * StockItemRepositorys pessimistic-låsta query direkt här, eftersom
     * vi behöver Order-objektet för StockMovement.order — sömmen som
     * StockService medvetet lämnade öppen (14.9). Att applicera
     * inom samma transaktion betyder: misslyckas leveransen någonstans
     * → rollback, ordern blir aldrig DELIVERED och inget saldo ändras.
     */
    private void applyDeliveryToStock(Order order) {
        Long careUnitId = order.getCareUnit().getId();

        for (OrderLine line : order.getLines()) {
            Long medicationId = line.getMedication().getId();

            // Hitta lagerposten för det här läkemedlet — med pessimistic
            // lock direkt. Finns ingen StockItem för läkemedlet ännu är
            // det ett konfigurationsfel: beställde du något du inte
            // lagerför? Vägrar leverans hellre än tyst skapar ny.
            StockItem stockItem = stockItemRepository
                    .findByMedicationAndCareUnitForUpdate(medicationId, careUnitId)
                    .orElseThrow(() -> new ValidationException(
                            "Kan inte leverera: ingen lagerpost finns för läkemedel "
                                    + medicationId + ". Skapa lagerpost först."));

            int delta = line.getQuantity();
            int newQuantity = stockItem.getQuantity() + delta;
            stockItem.setQuantity(newQuantity);

            // Skapa StockMovement med order-referensen — sömmen sys här.
            StockMovement movement = new StockMovement();
            movement.setCareUnit(stockItem.getCareUnit());
            movement.setStockItem(stockItem);
            movement.setMedication(stockItem.getMedication());
            movement.setDelta(delta);
            movement.setQuantityAfter(newQuantity);
            movement.setReason(MovementReason.DELIVERY);
            movement.setOrder(order);
            movement.setNotes("Leverans av order " + order.getOrderNumber());
            movement.setCreatedBy(currentUser.getCurrentUserId());
            stockMovementRepository.save(movement);
        }
    }

    /**
     * Genererar ett orderNumber. UUID-suffix räcker för demo — i produktion
     * vore ett sekventiellt nummer per enhet (med separat sekvenstabell)
     * bättre, men det är ett rabbit hole utanför caset.
     */
    private String generateOrderNumber() {
        return "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }
}