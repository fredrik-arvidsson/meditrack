package se.meditrack.dto;

import se.meditrack.entity.Order;
import se.meditrack.enums.OrderStatus;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Utgående beställning med sina rader. Nästlade OrderLineResponse — hela
 * ordern i ett svar, frontend behöver inte göra flera anrop. Statusens
 * tidsstämplar (sent/confirmed/delivered) exponeras för spårbarhet i UI:t.
 */
public record OrderResponse(
        Long id,
        String orderNumber,
        OrderStatus status,
        List<OrderLineResponse> lines,
        LocalDateTime sentAt,
        LocalDateTime confirmedAt,
        LocalDateTime deliveredAt,
        LocalDateTime createdAt,
        String notes
) {
    public static OrderResponse from(Order order) {
        List<OrderLineResponse> lineResponses = order.getLines().stream()
                .map(OrderLineResponse::from)
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderNumber(),
                order.getStatus(),
                lineResponses,
                order.getSentAt(),
                order.getConfirmedAt(),
                order.getDeliveredAt(),
                order.getCreatedAt(),
                order.getNotes()
        );
    }
}
