package org.fortishop.orderpaymentservice.dto.event;

public record InventoryFailedEvent(
        Long orderId,
        Long productId,
        String reason,
        String timestamp,
        String traceId
) {
}
