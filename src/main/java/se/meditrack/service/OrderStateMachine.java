package se.meditrack.service;

import org.springframework.stereotype.Component;
import se.meditrack.enums.OrderStatus;
import se.meditrack.exception.ValidationException;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Reglerna för en beställnings statusövergångar — separerat från
 * OrderService av två skäl: (1) ren logik utan databasberoende blir
 * trivial att enhetstesta i isolation; (2) reglerna ligger på ETT
 * ställe, inte spridda i if-satser genom servicen.
 *
 * Övergångar:
 *   DRAFT     → SENT, CANCELLED
 *   SENT      → CONFIRMED, CANCELLED
 *   CONFIRMED → DELIVERED, CANCELLED
 *   DELIVERED → (terminalt — inget därifrån)
 *   CANCELLED → (terminalt — inget därifrån)
 *
 * En olaglig övergång kastar ValidationException (400) — tyst
 * misslyckande hade kunnat resultera i fel saldo, vilket är
 * patientsäkerhetskritiskt.
 */
@Component
public class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderStatus.DRAFT,     EnumSet.of(OrderStatus.SENT, OrderStatus.CANCELLED),
            OrderStatus.SENT,      EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
            OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED),
            OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class),
            OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class)
    );

    /**
     * Validerar att övergången från current till target är tillåten.
     * Kastar ValidationException om inte. Returnerar tyst vid tillåtet språng.
     */
    public void validateTransition(OrderStatus current, OrderStatus target) {
        if (current == target) {
            throw new ValidationException(
                    "Ingen statusövergång: ordern är redan " + current);
        }
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.get(current);
        if (!allowed.contains(target)) {
            throw new ValidationException(
                    "Otillåten statusövergång: " + current + " → " + target
                            + " (tillåtna från " + current + ": "
                            + (allowed.isEmpty() ? "inga (terminalt tillstånd)" : allowed) + ")");
        }
    }

    /**
     * Är target en laglig efterföljare till current? Användbar för UI:t
     * (visa bara knappar för lagliga övergångar).
     */
    public boolean canTransition(OrderStatus current, OrderStatus target) {
        return current != target && ALLOWED_TRANSITIONS.get(current).contains(target);
    }
}