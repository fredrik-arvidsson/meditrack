package se.meditrack.enums;

/**
 * Orsak till en lagerrörelse. Matchar CHECK-constraint chk_movement_reason i V1.
 *
 * INITIAL  — saldo vid skapande (delta 0)
 * DELIVERY — inkommande vid leverans av order
 * MANUAL_ADJUSTMENT — manuell korrigering
 * CORRECTION — rättelse av tidigare fel
 * EXPIRY   — kassation pga utgånget datum
 * LOSS     — svinn/förlust
 */
public enum MovementReason {
    INITIAL,
    DELIVERY,
    MANUAL_ADJUSTMENT,
    CORRECTION,
    EXPIRY,
    LOSS
}