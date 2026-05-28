package se.meditrack.enums;

/**
 * Status i en beställnings livscykel. Matchar chk_order_status i V1.
 *
 * Tillåtna övergångar (valideras i OrderStateMachine, inte i entiteten):
 * DRAFT → SENT → CONFIRMED → DELIVERED, samt → CANCELLED från icke-terminala
 * lägen. DELIVERED är terminalt.
 */
public enum OrderStatus {
    DRAFT,
    SENT,
    CONFIRMED,
    DELIVERED,
    CANCELLED
}
